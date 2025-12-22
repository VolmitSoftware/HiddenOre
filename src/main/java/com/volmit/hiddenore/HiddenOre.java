package com.volmit.hiddenore;

import com.volmit.hiddenore.commands.HiddenOreCommand;
import com.volmit.hiddenore.generation.GenerationRules;
import com.volmit.hiddenore.listeners.MiningListener;
import com.volmit.hiddenore.rules.MiningRuleManager;
import com.volmit.hiddenore.util.ConfigWatcher;
import com.volmit.hiddenore.util.Messages;
import com.volmit.hiddenore.vein.PlayerVeinState;
import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class HiddenOre extends JavaPlugin {
    private MiningRuleManager ruleManager;
    private GenerationRules generationRules;
    private Messages messages;
    private final Set<UUID> debugPlayers = new HashSet<>();
    private final HashMap<UUID, PlayerVeinState> veinStates = new HashMap<>();
    private ConfigWatcher configWatcher;

    private static final String ASCII_BANNER =
            " _   _ _     _     _            _____          \n" +
                    "| | | (_)   | |   | |          |  _  |         \n" +
                    "| |_| |_  __| | __| | ___ _ __ | | | |_ __ ___ \n" +
                    "|  _  | |/ _` |/ _` |/ _ \\ '_ \\| | | | '__/ _ \\\n" +
                    "| | | | | (_| | (_| |  __/ | | \\ \\_/ / | |  __/\n" +
                    "\\_| |_/_|\\__,_|\\__,_|\\___|_| |_|\\___/|_|  \\___|\n";

    @Override
    public void onEnable() {
        boolean success = true;
        String errorMsg = "";

        try {
            saveDefaultConfig();
            File langFile = new File(getDataFolder(), "language.yml");
            if (!langFile.exists()) {
                saveResource("language.yml", false);
            }
            reloadAll();
            getServer().getPluginManager().registerEvents(new MiningListener(this), this);
            getCommand("hiddenore").setExecutor(new HiddenOreCommand(this));
            configWatcher = new ConfigWatcher(this);
            configWatcher.start();
        } catch (Exception e) {
            success = false;
            errorMsg = e.getMessage();
            getLogger().log(Level.SEVERE, "Error enabling plugin: ", e);
        }

        for (String line : ASCII_BANNER.split("\n")) {
            getLogger().info(line);
        }
        if (generationRules.isEnabled()) {
            getLogger().info("HiddenOre is currently configured to remove ores from newly generated chunks");
            getLogger().info("If this is unintended, you can disable it in the config");
        } else {
            getLogger().info("HiddenOre has the ability to remove ores as they generate in new chunks,");
            getLogger().info("you can enable this ability in the config.");
        }
        new Metrics(this, 27610);
    }

    public MiningRuleManager getRuleManager() {
        return ruleManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public void reloadAll() {
        reloadConfig();
        ruleManager = new MiningRuleManager(this);
        if (generationRules != null) generationRules.reload();
        else generationRules = new GenerationRules(this);
        File langFile = new File(getDataFolder(), "language.yml");
        YamlConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
        this.messages = new Messages(langConfig);
    }

    public boolean isDebug(UUID uuid) {
        return debugPlayers.contains(uuid);
    }

    public void setDebug(UUID uuid, boolean debug) {
        if (debug) debugPlayers.add(uuid);
        else debugPlayers.remove(uuid);
    }

    public boolean toggleDebug(UUID uuid) {
        if (isDebug(uuid)) {
            setDebug(uuid, false);
            return false;
        } else {
            setDebug(uuid, true);
            return true;
        }
    }

    public boolean isAutoPickup() {
        return getConfig().getBoolean("auto_pickup_drops", false);
    }

    public PlayerVeinState getPlayerVeinState(UUID uuid) {
        return veinStates.computeIfAbsent(uuid, k -> new PlayerVeinState());
    }
}