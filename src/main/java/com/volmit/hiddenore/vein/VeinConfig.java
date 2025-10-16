package com.volmit.hiddenore.vein;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class VeinConfig {
    public final long cooldownMs;
    public final double veinBaseChance;
    public final long tokenWindowMs;
    public final double tokenRange;
    public final Map<String, Double> enchantModifiers = new HashMap<>();

    // Vein start sound fields
    public final String veinStartSound;
    public final float veinStartSoundVolume;
    public final float veinStartSoundPitch;

    public VeinConfig(ConfigurationSection section) {
        this.cooldownMs = parseDuration(section.getString("cooldown", "5s"));
        this.veinBaseChance = section.getDouble("vein_base_chance", 0.25);
        this.tokenWindowMs = parseDuration(section.getString("token_window", "20s"));
        this.tokenRange = section.getDouble("token_range", 8);

        ConfigurationSection mods = section.getConfigurationSection("enchant_modifiers");
        if (mods != null) {
            for (String key : mods.getKeys(false)) {
                double mod = mods.getConfigurationSection(key).getDouble("vein_chance_mult", 0.0);
                enchantModifiers.put(key.toLowerCase(Locale.ROOT), mod);
            }
        }

        // Read vein start sound info
        ConfigurationSection soundSection = section.getConfigurationSection("vein_start_sound");
        if (soundSection != null) {
            veinStartSound = soundSection.getString("sound", "BLOCK_BEACON_POWER_SELECT");
            veinStartSoundVolume = (float) soundSection.getDouble("volume", 1.0);
            veinStartSoundPitch = (float) soundSection.getDouble("pitch", 1.0);
        } else {
            veinStartSound = "BLOCK_BEACON_POWER_SELECT";
            veinStartSoundVolume = 1.0f;
            veinStartSoundPitch = 1.0f;
        }
    }

    private static long parseDuration(String s) {
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.endsWith("ms")) return Long.parseLong(s.substring(0, s.length() - 2));
        if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length() - 1)) * 1000L;
        if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60_000L;
        return Long.parseLong(s);
    }
}