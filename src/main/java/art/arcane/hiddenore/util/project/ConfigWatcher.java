package art.arcane.hiddenore.util.project;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.util.common.SchedulerUtils;
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
    private volatile boolean running;
    private volatile Thread thread;
    private volatile WatchService watchService;

    public ConfigWatcher(HiddenOre plugin) {
        this.plugin = plugin;
        this.dir = plugin.getDataFolder().toPath();
        this.watchedFiles = new HashSet<>();
        watchedFiles.add("config.yml");
        watchedFiles.add("language.yml");
    }

    public synchronized void start() {
        if (thread != null && thread.isAlive()) {
            return;
        }

        running = true;
        Thread watcherThread = new Thread(this, "HiddenOre-ConfigWatcher");
        watcherThread.setDaemon(true);
        thread = watcherThread;
        watcherThread.start();
    }

    public synchronized void stop() {
        running = false;

        WatchService watcher = watchService;
        if (watcher != null) {
            try {
                watcher.close();
            } catch (IOException ignored) {
            }
        }

        Thread watcherThread = thread;
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
    }

    @Override
    public void run() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            watchService = watcher;
            dir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);

            while (running && plugin.isEnabled()) {
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                boolean shouldReload = false;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind != StandardWatchEventKinds.ENTRY_MODIFY) continue;
                    Path changed = (Path) event.context();
                    if (watchedFiles.contains(changed.getFileName().toString())) {
                        shouldReload = true;
                    }
                }

                if (shouldReload && plugin.isEnabled()) {
                    SchedulerUtils.runSync(plugin, this::reloadAndNotifyOps);
                }

                if (!key.reset()) break;
            }
        } catch (ClosedWatchServiceException ignored) {
            // Shutdown path.
        } catch (IOException e) {
            if (running) {
                plugin.getLogger().warning("ConfigWatcher stopped: " + e.getMessage());
            }
        } finally {
            watchService = null;
            thread = null;
            running = false;
        }
    }

    private void reloadAndNotifyOps() {
        if (!plugin.isEnabled()) {
            return;
        }

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

        org.bukkit.Sound bukkitSound;
        try {
            bukkitSound = org.bukkit.Sound.valueOf(soundStr);
        } catch (IllegalArgumentException ignored) {
            bukkitSound = org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
        Sound sound = Sound.sound(bukkitSound.key(), Sound.Source.MASTER, volume, pitch);

        Bukkit.getOnlinePlayers().stream()
                .filter(Player::isOp)
                .forEach(op -> {
                    op.sendMessage(plugin.getMessages().parse(msg));
                    op.playSound(sound);
                });
    }
}
