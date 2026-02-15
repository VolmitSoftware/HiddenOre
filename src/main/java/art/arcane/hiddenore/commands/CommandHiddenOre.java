package art.arcane.hiddenore.commands;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.util.common.Messages;
import art.arcane.volmlib.util.decree.annotations.Decree;
import art.arcane.volmlib.util.decree.annotations.Param;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Decree(name = "hiddenore", description = "HiddenOre command root")
public class CommandHiddenOre {
    private final HiddenOre plugin;

    public CommandHiddenOre(HiddenOre plugin) {
        this.plugin = plugin;
    }

    @Decree(name = "reload", description = "Reload HiddenOre configuration and language files")
    public void reload(@Param(name = "sender", contextual = true) CommandSender sender) {
        Messages messages = plugin.getMessages();
        if (!sender.hasPermission("hiddenore.admin")) {
            sender.sendMessage(messages.get("no_permission"));
            return;
        }

        plugin.reloadAll();
        sender.sendMessage(messages.get("reloaded"));
    }

    @Decree(name = "debug", description = "Toggle ore debug mode for yourself")
    public void debug(@Param(name = "sender", contextual = true) CommandSender sender) {
        Messages messages = plugin.getMessages();
        if (!sender.hasPermission("hiddenore.admin")) {
            sender.sendMessage(messages.get("no_permission"));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("no_permission"));
            return;
        }

        boolean nowDebug = plugin.toggleDebug(player.getUniqueId());
        if (nowDebug) {
            player.sendMessage(messages.parse("<green>Debug mode enabled.</green>"));
        } else {
            player.sendMessage(messages.parse("<red>Debug mode disabled.</red>"));
        }
    }
}
