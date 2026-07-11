package art.arcane.hiddenore.util.project;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class ConfigWatcher implements Runnable {
  private static final long RELOAD_DEBOUNCE_MILLIS = 250L;

  private final HiddenOre plugin;
  private final Set<String> watchedFiles;
  private final Path dir;
  private volatile boolean running;
  private volatile Thread thread;
  private volatile WatchService watchService;

  public ConfigWatcher(HiddenOre plugin) {
    this.plugin = plugin;
    this.dir = plugin.getDataFolder().toPath();
    this.watchedFiles = Set.of("config.yml", "language.yml");
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
      } catch (IOException exception) {
        plugin.getLogger().log(Level.WARNING, "Failed to close the HiddenOre config watcher", exception);
      }
    }

    Thread watcherThread = thread;
    if (watcherThread != null) {
      watcherThread.interrupt();
      if (watcherThread != Thread.currentThread()) {
        try {
          watcherThread.join(1000L);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          plugin.getLogger().log(Level.WARNING, "Interrupted while stopping the HiddenOre config watcher", exception);
        }
        if (watcherThread.isAlive()) {
          plugin.getLogger().warning("HiddenOre config watcher did not stop within one second");
        }
      }
    }
  }

  @Override
  public void run() {
    try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
      watchService = watcher;
      dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);

      while (running && plugin.isEnabled()) {
        WatchKey key;
        try {
          key = watcher.take();
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          if (running) {
            plugin.getLogger().log(Level.WARNING, "HiddenOre config watcher was interrupted unexpectedly", exception);
          }
          break;
        }
        boolean shouldReload = containsWatchedChange(key);
        if (!key.reset()) {
          break;
        }
        if (shouldReload) {
          awaitQuietPeriod(watcher);
          if (running && plugin.isEnabled()) {
            if (!SchedulerUtils.runGlobal(plugin, this::reloadAndNotifyOps)) {
              plugin.getLogger().warning("Failed to schedule config reload");
            }
          }
        }
      }
    } catch (ClosedWatchServiceException exception) {
      if (running) {
        plugin.getLogger().log(Level.SEVERE, "HiddenOre config watcher closed unexpectedly", exception);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      if (running) {
        plugin.getLogger().log(Level.WARNING, "HiddenOre config watcher was interrupted unexpectedly", exception);
      }
    } catch (IOException exception) {
      if (running) {
        plugin.getLogger().log(Level.SEVERE, "HiddenOre config watcher stopped unexpectedly", exception);
      }
    } finally {
      watchService = null;
      thread = null;
      running = false;
    }
  }

  private void reloadAndNotifyOps() {
    if (!running || plugin.isDraining() || !plugin.isEnabled()) {
      return;
    }

    Path configFile = dir.resolve("config.yml");
    Path languageFile = dir.resolve("language.yml");
    if (!Files.isRegularFile(configFile) || !Files.isRegularFile(languageFile)) {
      plugin.getLogger().warning("Config reload skipped because config.yml or language.yml is missing");
      return;
    }

    try {
      plugin.reloadAll();
    } catch (RuntimeException exception) {
      plugin.getLogger().log(Level.SEVERE, "Config reload failed; the previous runtime configuration remains active", exception);
      return;
    }

    HiddenOre.RuntimeState runtime = plugin.getRuntimeState();
    HiddenOre.ReloadNotification notification = runtime.reloadNotification();
    Component message = notification.message();
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (!SchedulerUtils.runEntity(plugin, player, () -> notifyOperator(player, message, notification.sound(),
          notification.volume(), notification.pitch()))) {
        plugin.getLogger().warning("Failed to schedule a config reload notification for an online player");
      }
    }
  }

  private boolean containsWatchedChange(WatchKey key) {
    boolean watched = false;
    for (WatchEvent<?> event : key.pollEvents()) {
      if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
        watched = true;
        continue;
      }
      if (!(event.context() instanceof Path changed)) {
        continue;
      }
      if (watchedFiles.contains(changed.getFileName().toString())) {
        watched = true;
      }
    }
    return watched;
  }

  private void awaitQuietPeriod(WatchService watcher) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RELOAD_DEBOUNCE_MILLIS);
    while (running) {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0L) {
        return;
      }
      WatchKey key = watcher.poll(remaining, TimeUnit.NANOSECONDS);
      if (key == null) {
        return;
      }
      if (containsWatchedChange(key)) {
        deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RELOAD_DEBOUNCE_MILLIS);
      }
      if (!key.reset()) {
        return;
      }
    }
  }

  private void notifyOperator(Player player, Component message, Sound sound, float volume, float pitch) {
    if (!player.isOp()) {
      return;
    }
    HiddenOre.sendMessage(player, message);
    player.playSound(player.getLocation(), sound, volume, pitch);
  }
}
