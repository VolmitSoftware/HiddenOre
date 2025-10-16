package com.volmit.hiddenore.commands;

import com.volmit.hiddenore.HiddenOre;
import com.volmit.hiddenore.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HiddenOreCommand implements CommandExecutor {
    private final HiddenOre plugin;

    public HiddenOreCommand(HiddenOre plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.getMessages();

        // Require hiddenore.admin for all commands
        if (!sender.hasPermission("hiddenore.admin")) {
            sender.sendMessage(messages.get("no_permission"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            sender.sendMessage(messages.get("reloaded"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("debug")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(messages.get("no_permission"));
                return true;
            }
            Player player = (Player) sender;
            boolean nowDebug = plugin.toggleDebug(player.getUniqueId());
            if (nowDebug) {
                player.sendMessage(messages.parse("<green>Debug mode enabled.</green>"));
            } else {
                player.sendMessage(messages.parse("<red>Debug mode disabled.</yellow>"));
            }
            return true;
        }
        for (Component line : messages.getList("usage")) {
            sender.sendMessage(line);
        }
        return true;
    }
}