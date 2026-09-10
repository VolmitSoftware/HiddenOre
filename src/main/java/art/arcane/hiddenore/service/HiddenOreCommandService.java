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
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.localization.LanguageAudience;
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
import java.util.Locale;
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
      plugin.warn("Failed to find command '%s'.", ROOT_COMMAND);
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

    if (args.length > 0 && args[0].equalsIgnoreCase("language")) {
      plugin.languageSwitcher().command(sender, Arrays.copyOfRange(args, 1, args.length));
      return true;
    }
    return LanguageAudience.call(sender instanceof Player player ? player.getUniqueId() : null,
        () -> executeCommand(sender, label, args));
  }

  private boolean executeCommand(CommandSender sender, String label, String[] args) {
    if (!(args.length > 1 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("dump"))
        && !sender.hasPermission(ROOT_PERMISSION)) {
      HiddenOre.sendMessage(sender, plugin.getMessages().component(sender, Messages.NO_PERMISSION));
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
    Optional<DirectorMiniMenu.DirectorHelpPage> page = DirectorMiniMenu.resolveHelp(getDirector(), List.of());
    if (page.isEmpty()) {
      return;
    }

    DirectorMiniMenu.Theme helpTheme = DirectorMiniMenu.Theme.fromDirectorTheme(theme);
    DirectorMiniMenu.deliver(sender, page.get(), helpTheme, plugin.getMessages().directorResolver());
  }

  @Nullable
  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
    if (command.getName().equalsIgnoreCase(ROOT_COMMAND) && !sender.hasPermission(ROOT_PERMISSION)
        && sender.hasPermission("hiddenore.debugdump")) {
      if (args.length == 1 && "debug".startsWith(args[0].toLowerCase(Locale.ROOT))) {
        return List.of("debug");
      }
      if (args.length == 2 && args[0].equalsIgnoreCase("debug")
          && "dump".startsWith(args[1].toLowerCase(Locale.ROOT))) {
        return List.of("dump");
      }
      if (args.length > 2 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("dump")) {
        return runDirectorTab(sender, alias, args);
      }
    }
    if (command.getName().equalsIgnoreCase(ROOT_COMMAND) && args.length > 0 && args[0].equalsIgnoreCase("language")) {
      return plugin.languageSwitcher().complete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
    if (!command.getName().equalsIgnoreCase(ROOT_COMMAND) || !sender.hasPermission(ROOT_PERMISSION)) {
      return List.of();
    }

    return runDirectorTab(sender, alias, args);
  }

  private boolean sendHelpIfRequested(CommandSender sender, String[] args) {
    Optional<DirectorMiniMenu.DirectorHelpPage> page = DirectorMiniMenu.resolveHelp(getDirector(), Arrays.asList(args));
    if (page.isEmpty()) {
      return false;
    }

    DirectorMiniMenu.Theme helpTheme = DirectorMiniMenu.Theme.fromDirectorTheme(theme);
    Messages messages = plugin.getMessages();
    DirectorMiniMenu.deliver(sender, page.get(), helpTheme, messages.directorResolver());

    return true;
  }

  private DirectorExecutionResult runDirector(CommandSender sender, String label, String[] args) {
    try {
      return getDirector().execute(new DirectorInvocation(new BukkitDirectorSender(sender), label, Arrays.asList(args)));
    } catch (Throwable e) {
      plugin.logException(Level.SEVERE, e, "Director command execution failed.");
      return DirectorExecutionResult.notHandled();
    }
  }

  private List<String> runDirectorTab(CommandSender sender, String alias, String[] args) {
    try {
      return getDirector().tabComplete(new DirectorInvocation(new BukkitDirectorSender(sender), alias, Arrays.asList(args)));
    } catch (Throwable e) {
      plugin.logException(Level.SEVERE, e, "Director tab completion failed.");
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
        ComponentMessenger.sendLiteral(sender, message);
      }
    }
  }
}
