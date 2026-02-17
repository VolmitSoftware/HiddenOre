package art.arcane.hiddenore.service;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.commands.CommandHiddenOre;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionResult;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.director.theme.DirectorProduct;
import art.arcane.volmlib.util.director.theme.DirectorTheme;
import art.arcane.volmlib.util.director.theme.DirectorThemes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class HiddenOreCommandService implements CommandExecutor, TabCompleter {
    private static final String ROOT_COMMAND = "hiddenore";
    private static final String ROOT_PERMISSION = "hiddenore.admin";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final HiddenOre plugin;
    private final DirectorTheme theme;
    private volatile DirectorRuntimeEngine director;

    public HiddenOreCommandService(HiddenOre plugin) {
        this.plugin = plugin;
        this.theme = DirectorThemes.forProduct(DirectorProduct.HIDDENORE);
    }

    public void register() {
        PluginCommand command = plugin.getCommand(ROOT_COMMAND);
        if (command == null) {
            plugin.getLogger().warning("Failed to find command '" + ROOT_COMMAND + "'");
            return;
        }

        command.setExecutor(this);
        command.setTabCompleter(this);
        getDirector();
    }

    private DirectorRuntimeEngine getDirector() {
        DirectorRuntimeEngine local = director;
        if (local != null) {
            return local;
        }

        synchronized (this) {
            if (director != null) {
                return director;
            }

            director = DirectorEngineFactory.create(
                    new CommandHiddenOre(plugin),
                    null,
                    buildDirectorContexts(),
                    null,
                    null,
                    null
            );

            return director;
        }
    }

    private DirectorContextRegistry buildDirectorContexts() {
        DirectorContextRegistry contexts = new DirectorContextRegistry();
        contexts.register(CommandSender.class, (invocation, map) -> {
            if (invocation.getSender() instanceof BukkitDirectorSender sender) {
                return sender.sender();
            }

            return null;
        });

        contexts.register(Player.class, (invocation, map) -> {
            if (invocation.getSender() instanceof BukkitDirectorSender sender && sender.sender() instanceof Player player) {
                return player;
            }

            return null;
        });

        return contexts;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase(ROOT_COMMAND)) {
            return false;
        }

        if (!sender.hasPermission(ROOT_PERMISSION)) {
            sender.sendMessage(plugin.getMessages().get("no_permission"));
            return true;
        }

        if (sendHelpIfRequested(sender, args)) {
            playSuccessSound(sender);
            return true;
        }

        DirectorExecutionResult result = runDirector(sender, label, args);
        if (result.isSuccess()) {
            playSuccessSound(sender);
            return true;
        }

        playFailureSound(sender);
        if (result.getMessage() == null || result.getMessage().trim().isEmpty()) {
            for (var line : plugin.getMessages().getList("usage")) {
                sender.sendMessage(line);
            }
        }

        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase(ROOT_COMMAND) || !sender.hasPermission(ROOT_PERMISSION)) {
            return List.of();
        }

        return runDirectorTab(sender, alias, args);
    }

    private boolean sendHelpIfRequested(CommandSender sender, String[] args) {
        Optional<DirectorMiniMenu.DirectorHelpPage> page = DirectorMiniMenu.resolveHelp(getDirector(), Arrays.asList(args), 8);
        if (page.isEmpty()) {
            return false;
        }

        DirectorMiniMenu.Theme helpTheme = DirectorMiniMenu.Theme.fromDirectorTheme(theme);
        for (String line : DirectorMiniMenu.render(page.get(), helpTheme)) {
            sendRich(sender, line);
        }

        return true;
    }

    private DirectorExecutionResult runDirector(CommandSender sender, String label, String[] args) {
        try {
            return getDirector().execute(new DirectorInvocation(new BukkitDirectorSender(sender), label, Arrays.asList(args)));
        } catch (Throwable e) {
            plugin.getLogger().warning("Director command execution failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            return DirectorExecutionResult.notHandled();
        }
    }

    private List<String> runDirectorTab(CommandSender sender, String alias, String[] args) {
        try {
            return getDirector().tabComplete(new DirectorInvocation(new BukkitDirectorSender(sender), alias, Arrays.asList(args)));
        } catch (Throwable e) {
            plugin.getLogger().warning("Director tab completion failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            return List.of();
        }
    }

    private void playSuccessSound(CommandSender sender) {
        if (sender instanceof Player player) {
            player.playSound(player.getLocation(), theme.getSuccessSound(), SoundCategory.MASTER, 0.8f, 1.2f);
        }
    }

    private void playFailureSound(CommandSender sender) {
        if (sender instanceof Player player) {
            player.playSound(player.getLocation(), theme.getErrorSound(), SoundCategory.MASTER, 0.8f, 0.9f);
        }
    }

    private void sendRich(CommandSender sender, String miniMessage) {
        if (miniMessage == null || miniMessage.trim().isEmpty()) {
            return;
        }

        Component component = MINI_MESSAGE.deserialize(miniMessage);
        try {
            sender.getClass().getMethod("sendRichMessage", String.class).invoke(sender, miniMessage);
            return;
        } catch (Throwable ignored) {
        }

        try {
            sender.getClass().getMethod("sendMessage", Component.class).invoke(sender, component);
            return;
        } catch (Throwable ignored) {
        }

        sender.sendMessage(LEGACY_SERIALIZER.serialize(component));
    }

    private record BukkitDirectorSender(CommandSender sender) implements DirectorSender {
        @Override
        public String getName() {
            return sender.getName();
        }

        @Override
        public boolean isPlayer() {
            return sender instanceof Player;
        }

        @Override
        public void sendMessage(String message) {
            if (message != null && !message.trim().isEmpty()) {
                sender.sendMessage(message);
            }
        }
    }
}
