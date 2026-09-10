package art.arcane.hiddenore.commands;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import org.bukkit.command.CommandSender;

@Director(name = "hiddenore", description = "HiddenOre command root", descriptionKey = "command.description.root")
public final class CommandHiddenOre {
  private final HiddenOre plugin;
  private HiddenOreDebugCommands debug;

  public CommandHiddenOre(HiddenOre plugin) {
    this.plugin = plugin;
    debug = new HiddenOreDebugCommands(plugin);
  }

  @Director(name = "language", description = "Choose your language or the server language")
  public void language(@Param(name = "sender", contextual = true) CommandSender sender) {
    plugin.languageSwitcher().open(sender);
  }

  @Director(name = "config", sync = true, description = "Edit HiddenOre settings", descriptionKey = "command.description.config")
  public void config(@Param(name = "sender", contextual = true) CommandSender sender) {
    plugin.configEditor().open(sender);
  }

}
