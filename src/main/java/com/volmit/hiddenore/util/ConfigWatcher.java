package com.volmit.hiddenore.util;

import com.volmit.hiddenore.HiddenOre;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

public class ConfigWatcher implements Runnable {
    private final HiddenOre plugin;
    private final Set<String> watchedFiles;
    private final Path dir;

    public ConfigWatcher(HiddenOre plugin) {
        this.plugin = plugin;
        this.dir = plugin.getDataFolder().toPath();
        this.watchedFiles = new HashSet<>();
        watchedFiles.add("config.yml");
        watchedFiles.add("language.yml");
    }

    public void start() {
        Thread t = new Thread(this, "HiddenOre-ConfigWatcher");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void run() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            dir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);

            while (true) {
                WatchKey key = watcher.take();
                boolean shouldReload = false;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind != StandardWatchEventKinds.ENTRY_MODIFY) continue;
                    Path changed = (Path) event.context();
                    if (watchedFiles.contains(changed.getFileName().toString())) {
                        shouldReload = true;
                    }
                }

                if (shouldReload) {
                    Bukkit.getScheduler().runTask(plugin, this::reloadAndNotifyOps);
                }

                if (!key.reset()) break;
            }
        } catch (IOException | InterruptedException e) {
            plugin.getLogger().warning("ConfigWatcher stopped: " + e.getMessage());
        }
    }

    private void reloadAndNotifyOps() {
        plugin.reloadConfig();
        // Reload language
        File langFile = new File(plugin.getDataFolder(), "language.yml");
        plugin.getMessages().reload(langFile);
        // Drop/vein/other manager reloads
        plugin.getRuleManager().reload();

        // Get configurable message and sound
        YamlConfiguration lang = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "language.yml"));
        String msg = lang.getString("config_reloaded_message", "<green>Config updated and reloaded!</green>");
        String soundStr = lang.getString("config_reloaded_sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        float volume = (float) lang.getDouble("config_reloaded_sound_volume", 1.0);
        float pitch = (float) lang.getDouble("config_reloaded_sound_pitch", 1.6);

        Sound sound = Sound.sound(org.bukkit.Sound.valueOf(soundStr).key(), Sound.Source.MASTER, volume, pitch);

        Bukkit.getOnlinePlayers().stream()
                .filter(Player::isOp)
                .forEach(op -> {
                    op.sendMessage(plugin.getMessages().parse(msg));
                    op.playSound(sound);
                });
    }
}