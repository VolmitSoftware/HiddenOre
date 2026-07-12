package art.arcane.hiddenore;

import art.arcane.hiddenore.api.HiddenOreAPI;
import art.arcane.hiddenore.generation.GenerationRules;
import art.arcane.hiddenore.listeners.MiningListener;
import art.arcane.hiddenore.listeners.PlacementListener;
import art.arcane.hiddenore.listeners.WorldLifecycleListener;
import art.arcane.hiddenore.rules.MiningRuleManager;
import art.arcane.hiddenore.service.HiddenOreCommandService;
import art.arcane.hiddenore.util.common.Messages;
import art.arcane.hiddenore.util.common.SplashScreen;
import art.arcane.hiddenore.util.project.ConfigWatcher;
import art.arcane.hiddenore.util.project.SoundResolver;
import art.arcane.hiddenore.vein.SeededVeinGenerator;
import art.arcane.volmlib.integration.ReloadAware;
import art.arcane.volmlib.util.bukkit.ChunkPositionSet;
import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bstats.bukkit.Metrics;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class HiddenOre extends JavaPlugin implements ReloadAware {
  private static volatile BukkitAudiences audiences;
  private final Set<UUID> debugPlayers = ConcurrentHashMap.newKeySet();
  private GenerationRules generationRules;
  private ConfigWatcher configWatcher;
  private HiddenOreCommandService commandService;
  private ChunkPositionSet placedBlocks;
  private ChunkPositionSet consumedVeins;
  private HiddenOreAPI api;
  private volatile RuntimeState runtimeState;
  private volatile boolean draining;

  public HiddenOre() {
    getLogger().info("Loading dependencies...");
    new SpigotApplicationBuilder(this)
      .build();
    getLogger().info("Dependencies loaded.");
  }

  @Override
  public void onEnable() {
    draining = false;

    try {
      audiences = BukkitAudiences.create(this);
      saveDefaultConfig();
      File langFile = new File(getDataFolder(), "language.yml");
      if (!langFile.exists()) {
        saveResource("language.yml", false);
      }
      placedBlocks = new ChunkPositionSet(this, "placed_blocks");
      consumedVeins = new ChunkPositionSet(this, "consumed_veins");
      api = new HiddenOreAPI(this);
      generationRules = new GenerationRules(this);
      reloadAll();
      generationRules.start();
      getServer().getPluginManager().registerEvents(new MiningListener(this), this);
      getServer().getPluginManager().registerEvents(new PlacementListener(this), this);
      getServer().getPluginManager().registerEvents(new WorldLifecycleListener(this), this);
      commandService = new HiddenOreCommandService(this);
      commandService.register();
      configWatcher = new ConfigWatcher(this);
      configWatcher.start();
      SplashScreen.print(this, true, "");
    } catch (Exception exception) {
      getLogger().log(Level.SEVERE, "Error enabling plugin", exception);
      try {
        SplashScreen.print(this, false, exception.getMessage());
      } catch (RuntimeException splashException) {
        getLogger().log(Level.SEVERE, "Error rendering the HiddenOre startup failure screen", splashException);
      } finally {
        drain();
        getServer().getPluginManager().disablePlugin(this);
      }
      return;
    }

    if (generationRules != null) {
      if (generationRules.isEnabled()) {
        getLogger().info("HiddenOre is currently configured to remove ores from newly generated chunks");
        getLogger().info("If this is unintended, you can disable it in the config");
      } else {
        getLogger().info("HiddenOre has the ability to remove ores as they generate in new chunks,");
        getLogger().info("you can enable this ability in the config.");
      }
    }

    try {
      new Metrics(this, 27610);
    } catch (RuntimeException exception) {
      getLogger().log(Level.WARNING, "Failed to initialize HiddenOre metrics", exception);
    }
  }

  @Override
  public void onDisable() {
    drain();
  }

  @Override
  public void onPreUnload(ReloadAware.PreUnloadReason reason) {
    getLogger().info("BileTools pre-unload hook fired (" + reason + "). Draining HiddenOre runtime services.");
    drain();
  }

  private synchronized void drain() {
    if (draining) {
      return;
    }
    draining = true;
    if (configWatcher != null) {
      configWatcher.stop();
      configWatcher = null;
    }
    if (generationRules != null) {
      generationRules.close();
      generationRules = null;
    }
    debugPlayers.clear();
    if (audiences != null) {
      try {
        audiences.close();
      } catch (Throwable ex) {
        getLogger().log(Level.WARNING, "Error closing Adventure audiences", ex);
      }
      audiences = null;
    }
  }

  public static BukkitAudiences audiences() {
    return audiences;
  }

  public static void sendMessage(CommandSender sender, Component component) {
    BukkitAudiences a = audiences;
    if (a != null && sender != null && component != null) {
      a.sender(sender).sendMessage(component);
    }
  }

  public MiningRuleManager getRuleManager() {
    return getRuntimeState().ruleManager();
  }

  public Messages getMessages() {
    return getRuntimeState().messages();
  }

  public synchronized void reloadAll() {
    if (draining) {
      throw new IllegalStateException("HiddenOre is shutting down");
    }

    File configFile = new File(getDataFolder(), "config.yml");
    File langFile = new File(getDataFolder(), "language.yml");
    YamlConfiguration config = loadYaml(configFile, "config.yml");
    YamlConfiguration langConfig = loadYaml(langFile, "language.yml");

    MiningRuleManager nextRuleManager = new MiningRuleManager(config);
    Messages nextMessages = new Messages(langConfig);
    SeededVeinGenerator nextVeinGenerator = new SeededVeinGenerator(nextRuleManager.getAllDropRules());
    boolean autoPickup = optionalBoolean(config, "auto_pickup_drops", false);
    boolean suppressBlockDrop = optionalBoolean(config, "suppress_block_drop_on_custom_drop", false);
    GenerationRules.GenerationPolicy generationPolicy = GenerationRules.parsePolicy(config);
    ReloadNotification reloadNotification = parseReloadNotification(langConfig, nextMessages);

    runtimeState = new RuntimeState(nextRuleManager, nextMessages, nextVeinGenerator, generationPolicy,
        reloadNotification, autoPickup, suppressBlockDrop);
  }

  public boolean isDebug(UUID uuid) {
    return debugPlayers.contains(uuid);
  }

  public void setDebug(UUID uuid, boolean debug) {
    if (debug) {
      debugPlayers.add(uuid);
    } else {
      debugPlayers.remove(uuid);
    }
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
    return getRuntimeState().autoPickup();
  }

  public boolean suppressBlockDropOnCustomDrop() {
    return getRuntimeState().suppressBlockDrop();
  }

  public SeededVeinGenerator getVeinGenerator() {
    return getRuntimeState().veinGenerator();
  }

  public ChunkPositionSet getPlacedBlocks() {
    return placedBlocks;
  }

  public ChunkPositionSet getConsumedVeins() {
    return consumedVeins;
  }

  public HiddenOreAPI getApi() {
    return api;
  }

  public RuntimeState getRuntimeState() {
    RuntimeState current = runtimeState;
    if (current == null) {
      throw new IllegalStateException("HiddenOre runtime is not available");
    }
    return current;
  }

  public boolean isDraining() {
    return draining;
  }

  private YamlConfiguration loadYaml(File file, String name) {
    YamlConfiguration configuration = new YamlConfiguration();
    try {
      configuration.load(file);
      return configuration;
    } catch (IOException | InvalidConfigurationException exception) {
      throw new IllegalArgumentException("Failed to load " + name, exception);
    }
  }

  private boolean optionalBoolean(YamlConfiguration configuration, String path, boolean defaultValue) {
    Object value = configuration.get(path);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Boolean)) {
      throw new IllegalArgumentException(path + ": expected true or false");
    }
    return (Boolean) value;
  }

  private ReloadNotification parseReloadNotification(YamlConfiguration configuration, Messages messages) {
    String rawMessage = optionalString(configuration, "config_reloaded_message", "<green>Config updated and reloaded!</green>");
    String soundName = optionalString(configuration, "config_reloaded_sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
    float volume = finiteFloat(configuration, "config_reloaded_sound_volume", 1.0f, 0.0f, Float.MAX_VALUE);
    float pitch = finiteFloat(configuration, "config_reloaded_sound_pitch", 1.6f, 0.5f, 2.0f);
    Sound sound = SoundResolver.resolve(soundName, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
    return new ReloadNotification(messages.parseConfigured("config_reloaded_message", rawMessage), sound, volume, pitch);
  }

  private String optionalString(YamlConfiguration configuration, String path, String defaultValue) {
    Object value = configuration.get(path);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof String) || ((String) value).isBlank()) {
      throw new IllegalArgumentException(path + ": expected a non-empty string");
    }
    return (String) value;
  }

  private float finiteFloat(YamlConfiguration configuration, String path, float defaultValue, float minimum, float maximum) {
    Object value = configuration.get(path);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException(path + ": expected a finite number");
    }
    double number = ((Number) value).doubleValue();
    if (!Double.isFinite(number) || number < minimum || number > maximum) {
      throw new IllegalArgumentException(path + ": expected a value between " + minimum + " and " + maximum);
    }
    return (float) number;
  }

  public record RuntimeState(MiningRuleManager ruleManager,
                             Messages messages,
                             SeededVeinGenerator veinGenerator,
                             GenerationRules.GenerationPolicy generationPolicy,
                             ReloadNotification reloadNotification,
                             boolean autoPickup,
                             boolean suppressBlockDrop) {
  }

  public record ReloadNotification(Component message, Sound sound, float volume, float pitch) {
  }
}
