package art.arcane.hiddenore.commands;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.util.common.Messages;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.theme.DirectorProduct;
import art.arcane.volmlib.util.director.theme.DirectorThemes;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Director(name = "debug", description = "HiddenOre diagnostic tools", descriptionKey = "command.description.debug_group")
public final class HiddenOreDebugCommands {
  private final HiddenOre plugin;

  public HiddenOreDebugCommands(HiddenOre plugin) {
    this.plugin = plugin;
  }

  @Director(name = "version", description = "Show the HiddenOre version", descriptionKey = "command.description.version")
  public void version(@Param(name = "sender", contextual = true) CommandSender sender) {
    ComponentMessenger.sendMarkup(sender, DirectorMiniMenu.version("HiddenOre", plugin.getDescription().getVersion(),
        DirectorMiniMenu.Theme.fromDirectorTheme(DirectorThemes.forProduct(DirectorProduct.HIDDENORE))));
  }

  @Director(name = "dump", sync = true, description = "Create and optionally upload a diagnostic report", descriptionKey = "command.description.debugdump")
  public void dump(
    @Param(name = "upload", defaultValue = "true", description = "Upload the report to mclo.gs", descriptionKey = "command.parameter.debugdump_upload") boolean upload,
    @Param(name = "sender", contextual = true) CommandSender sender
  ) {
    plugin.debugDump().request(sender, upload);
  }

  @Director(name = "mode", description = "Toggle ore debug mode for yourself", descriptionKey = "command.description.debug")
  public void mode(@Param(name = "sender", contextual = true) CommandSender sender) {
    Messages messages = plugin.getMessages();
    if (!sender.hasPermission("hiddenore.admin")) {
      HiddenOre.sendMessage(sender, messages.component(sender, Messages.NO_PERMISSION));
      return;
    }

    if (!(sender instanceof Player player)) {
      HiddenOre.sendMessage(sender, messages.component(sender, Messages.PLAYER_ONLY));
      return;
    }

    boolean nowDebug = plugin.toggleDebug(player.getUniqueId());
    if (nowDebug) {
      HiddenOre.sendMessage(player, messages.component(player, Messages.DEBUG_ENABLED));
    } else {
      HiddenOre.sendMessage(player, messages.component(player, Messages.DEBUG_DISABLED));
    }
  }

}
