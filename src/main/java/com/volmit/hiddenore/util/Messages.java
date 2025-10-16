package com.volmit.hiddenore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Messages {
    private YamlConfiguration lang;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private String prefix;

    public Messages(YamlConfiguration lang) {
        this.lang = lang;
        this.prefix = lang.getString("prefix", "");
    }

    public void reload(File langFile) {
        this.lang = YamlConfiguration.loadConfiguration(langFile);
        this.prefix = lang.getString("prefix", "");
    }

    public Component get(String key) {
        String raw = lang.getString(key, "<red>Missing message: " + key + "</red>");
        return parse(raw);
    }

    public List<Component> getList(String key) {
        List<String> lines = lang.getStringList(key);
        List<Component> comps = new ArrayList<>();
        for (String line : lines) {
            comps.add(parse(line));
        }
        return comps;
    }

    public Component parse(String raw) {
        if (raw.contains("&")) {
            raw = ChatColor.translateAlternateColorCodes('&', raw);
        }
        return miniMessage.deserialize(prefix + raw);
    }
}