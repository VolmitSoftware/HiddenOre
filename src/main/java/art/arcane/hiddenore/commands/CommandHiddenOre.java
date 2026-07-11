package art.arcane.hiddenore.commands;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.util.common.Messages;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

@Director(name = "hiddenore", description = "HiddenOre command root")
public final class CommandHiddenOre {
  private final HiddenOre plugin;

  public CommandHiddenOre(HiddenOre plugin) {
    this.plugin = plugin;
  }

  @Director(name = "reload", description = "Reload HiddenOre configuration and language files")
  public void reload(@Param(name = "sender", contextual = true) CommandSender sender) {
    Messages messages = plugin.getMessages();
    if (!sender.hasPermission("hiddenore.admin")) {
      HiddenOre.sendMessage(sender, messages.get("no_permission"));
      return;
    }

    if (!SchedulerUtils.runGlobal(plugin, () -> reloadAndRespond(sender, messages))) {
      HiddenOre.sendMessage(sender, messages.get("reload_failed"));
    }
  }

  @Director(name = "debug", description = "Toggle ore debug mode for yourself")
  public void debug(@Param(name = "sender", contextual = true) CommandSender sender) {
    Messages messages = plugin.getMessages();
    if (!sender.hasPermission("hiddenore.admin")) {
      HiddenOre.sendMessage(sender, messages.get("no_permission"));
      return;
    }

    if (!(sender instanceof Player player)) {
      HiddenOre.sendMessage(sender, messages.get("player_only"));
      return;
    }

    boolean nowDebug = plugin.toggleDebug(player.getUniqueId());
    if (nowDebug) {
      HiddenOre.sendMessage(player, messages.parse("<green>Debug mode enabled.</green>"));
    } else {
      HiddenOre.sendMessage(player, messages.parse("<red>Debug mode disabled.</red>"));
    }
  }

  private void reloadAndRespond(CommandSender sender, Messages previousMessages) {
    try {
      plugin.reloadAll();
      sendScheduled(sender, plugin.getMessages().get("reloaded"));
    } catch (RuntimeException exception) {
      plugin.getLogger().log(Level.SEVERE, "HiddenOre reload failed; the previous runtime configuration remains active", exception);
      sendScheduled(sender, previousMessages.get("reload_failed"));
    }
  }

  private void sendScheduled(CommandSender sender, Component message) {
    if (sender instanceof Player player) {
      if (!SchedulerUtils.runEntity(plugin, player, () -> HiddenOre.sendMessage(player, message))) {
        plugin.getLogger().warning("Failed to schedule a HiddenOre reload response for " + player.getName());
      }
      return;
    }
    HiddenOre.sendMessage(sender, message);
  }
}
