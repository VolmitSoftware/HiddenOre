package art.arcane.hiddenore.service;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.commands.CommandHiddenOre;
import art.arcane.hiddenore.util.common.Messages;
import art.arcane.volmlib.util.director.DirectorEngineOptions;
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
import java.util.logging.Level;

public final class HiddenOreCommandService implements CommandExecutor, TabCompleter {
  private static final String ROOT_COMMAND = "hiddenore";
  private static final String ROOT_PERMISSION = "hiddenore.admin";

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
          DirectorEngineOptions.builder()
              .contexts(buildDirectorContexts())
              .textResolver((key, arguments) -> plugin.getMessages().directorText(key, arguments))
              .build()
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
      HiddenOre.sendMessage(sender, plugin.getMessages().component(Messages.NO_PERMISSION));
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
    sendRootHelp(sender);

    return true;
  }

  private void sendRootHelp(CommandSender sender) {
    Optional<DirectorMiniMenu.DirectorHelpPage> page = DirectorMiniMenu.resolveHelp(getDirector(), List.of(), 8);
    if (page.isEmpty()) {
      return;
    }

    DirectorMiniMenu.Theme helpTheme = DirectorMiniMenu.Theme.fromDirectorTheme(theme);
    DirectorMiniMenu.deliver(sender, DirectorMiniMenu.render(page.get(), helpTheme, plugin.getMessages().directorResolver()));
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
    Messages messages = plugin.getMessages();
    DirectorMiniMenu.deliver(sender, DirectorMiniMenu.render(page.get(), helpTheme, messages.directorResolver()));

    return true;
  }

  private DirectorExecutionResult runDirector(CommandSender sender, String label, String[] args) {
    try {
      return getDirector().execute(new DirectorInvocation(new BukkitDirectorSender(sender), label, Arrays.asList(args)));
    } catch (Throwable e) {
      plugin.getLogger().log(Level.SEVERE, "Director command execution failed", e);
      return DirectorExecutionResult.notHandled();
    }
  }

  private List<String> runDirectorTab(CommandSender sender, String alias, String[] args) {
    try {
      return getDirector().tabComplete(new DirectorInvocation(new BukkitDirectorSender(sender), alias, Arrays.asList(args)));
    } catch (Throwable e) {
      plugin.getLogger().log(Level.SEVERE, "Director tab completion failed", e);
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

  private record BukkitDirectorSender(
      CommandSender sender) implements DirectorSender {
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
