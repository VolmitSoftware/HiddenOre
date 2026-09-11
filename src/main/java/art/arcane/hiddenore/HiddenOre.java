package art.arcane.hiddenore;

import art.arcane.volmlib.util.diagnostics.BukkitDebugDump;
import art.arcane.volmlib.util.diagnostics.DebugDumpContributor;
import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.volmlib.util.config.TomlDocumentEditor;
import art.arcane.volmlib.util.config.ConfigEditorDocument;
import art.arcane.volmlib.util.config.BukkitConfigEditor;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.VolmitLocales;
import art.arcane.hiddenore.api.HiddenOreAPI;
import art.arcane.hiddenore.api.HiddenOreService;
import art.arcane.hiddenore.generation.GenerationRules;
import art.arcane.hiddenore.listeners.MiningListener;
import art.arcane.hiddenore.listeners.PlacementListener;
import art.arcane.hiddenore.listeners.WorldLifecycleListener;
import art.arcane.hiddenore.rules.MiningRuleManager;
import art.arcane.hiddenore.service.HiddenOreCommandService;
import art.arcane.hiddenore.service.HiddenOreIntegrationService;
import art.arcane.hiddenore.service.HiddenOrePlaceholderService;
import art.arcane.hiddenore.service.HiddenOreTelemetry;
import art.arcane.hiddenore.util.common.Messages;
import art.arcane.hiddenore.util.common.SplashScreen;
import art.arcane.hiddenore.util.project.ConfigWatcher;
import art.arcane.hiddenore.vein.SeededVeinGenerator;
import art.arcane.volmlib.integration.ReloadAware;
import art.arcane.volmlib.util.bukkit.ChunkPositionSet;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.theme.DirectorProduct;
import art.arcane.volmlib.util.director.theme.DirectorThemes;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.URI;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HiddenOre extends JavaPlugin implements ReloadAware {
  // bstats.org plugin id
  private static final int BSTATS_PLUGIN_ID = 27610;
  private static final long LOG_THROTTLE_NANOS = TimeUnit.MINUTES.toNanos(1L);
  private final Set<UUID> debugPlayers = ConcurrentHashMap.newKeySet();
  private final ConcurrentMap<String, LogThrottle> logThrottles = new ConcurrentHashMap<>();
  private final AtomicLong configurationRevision = new AtomicLong();
  private GenerationRules generationRules;
  private RemoteLanguageCatalog remoteLanguages;
  private PluginLanguageService languageService;
  private BukkitLanguageSwitcher languageSwitcher;
  private BukkitConfigEditor configEditor;
  private BukkitDebugDump debugDump;
  private volatile String languageLocale = "en_US";
  private ConfigWatcher configWatcher;
  private HiddenOreCommandService commandService;
  private HiddenOreIntegrationService integrationService;
  private HiddenOrePlaceholderService placeholderService;
  private ChunkPositionSet placedBlocks;
  private ChunkPositionSet consumedVeins;
  private HiddenOreAPI api;
  // HiddenOreMetrics owns all bstats types; never reference them from this class (slimjar link trap)
  private HiddenOreMetrics metrics;
  private volatile RuntimeState runtimeState;
  private volatile String appliedConfigToml;
  private volatile boolean draining;
  private boolean serviceRegistered;

  public HiddenOre() {
    info("Loading dependencies...");
    new SpigotApplicationBuilder(this)
      .build();
    info("Dependencies loaded.");
  }

  @Override
  public void onEnable() {
    draining = false;
    debugDump = BukkitDebugDump.create(this, new BukkitDebugDump.Options(() -> true, this::captureDebugState,
        new BukkitDebugDump.Presentation("/hiddenore debug dump", "/hiddenore debug",
            DirectorMiniMenu.Theme.fromDirectorTheme(DirectorThemes.forProduct(DirectorProduct.HIDDENORE)),
            (key, arguments) -> ComponentText.literal(getMessages().directorText(key, arguments)))));

    try {
      File configFile = new File(getDataFolder(), "hiddenore.toml");
      if (!configFile.exists()) {
        saveResource("hiddenore.toml", false);
      }
      configWatcher = new ConfigWatcher(this);
      placedBlocks = new ChunkPositionSet(this, "placed_blocks");
      consumedVeins = new ChunkPositionSet(this, "consumed_veins");
      api = new HiddenOreAPI(this);
      generationRules = new GenerationRules(this);
      remoteLanguages = RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
          "HiddenOre", URI.create("https://raw.githubusercontent.com/VolmitSoftware/HiddenOre/"),
          "src/main/resources/languages", ".toml", "language-source.properties",
          getClass().getClassLoader()));
      String initialConfig = readConfig(configFile);
      reloadAll(initialConfig, prepareReloadMessages(initialConfig), configurationRevision.get());
      String initialLocale = languageLocale;
      languageLocale = "en_US";
      languageService = new PluginLanguageService(new PluginLanguageService.Options(
          getDataFolder().toPath().resolve("languages/language-preferences.properties"), VolmitLocales::all,
          () -> languageLocale, () -> getMessages().defaultSnapshot(), this::prepareLanguage,
          this::selectLanguage, getLogger()));
      getMessages().languageService(languageService);
      languageSwitcher = BukkitLanguageSwitcher.register(this, languageService,
          new BukkitLanguageSwitcher.Options("hiddenore", "hiddenore.admin",
              DirectorMiniMenu.Theme.fromDirectorTheme(DirectorThemes.forProduct(DirectorProduct.HIDDENORE)),
              (key, arguments) -> getMessages().directorText(key, arguments),
              new PluginLanguageEditor.Options(
                  locale -> getMessages().editorOptions().loader().load(locale), this::saveLanguageMessage)));
      if (!initialLocale.equals("en_US")) {
        languageService.selectDefault(initialLocale).exceptionally(failure -> {
          logException(Level.WARNING, failure, "Unable to load the configured language; English remains active.");
          return null;
        });
      }
      configEditor = BukkitConfigEditor.register(this, new BukkitConfigEditor.Options(
          this::loadConfigurationDocument, this::saveConfiguration,
          new BukkitConfigEditor.Presentation("hiddenore.admin",
              DirectorMiniMenu.Theme.fromDirectorTheme(DirectorThemes.forProduct(DirectorProduct.HIDDENORE)),
              (key, arguments) -> getMessages().directorText(key, arguments))));
      generationRules.start();
      getServer().getPluginManager().registerEvents(new MiningListener(this), this);
      getServer().getPluginManager().registerEvents(new PlacementListener(this), this);
      getServer().getPluginManager().registerEvents(new WorldLifecycleListener(this), this);
      commandService = new HiddenOreCommandService(this);
      commandService.register();
      integrationService = new HiddenOreIntegrationService(this);
      integrationService.register();
      placeholderService = new HiddenOrePlaceholderService(this);
      placeholderService.register();
      getServer().getServicesManager().register(HiddenOreService.class, api, this, ServicePriority.Normal);
      serviceRegistered = true;
      debug("HiddenOre service registered for third-party integrations.");
      String startupSnapshot = appliedConfigToml;
      if (startupSnapshot == null) {
        throw new IllegalStateException("HiddenOre configuration snapshot is unavailable after startup reload");
      }
      configWatcher.startWithAppliedSnapshot(startupSnapshot);
      SplashScreen.print(this, true);
    } catch (Exception exception) {
      logException(Level.SEVERE, exception, "HiddenOre failed to enable.");
      try {
        SplashScreen.print(this, false);
      } catch (RuntimeException splashException) {
        logException(Level.SEVERE, splashException, "Error rendering the HiddenOre startup failure screen.");
      } finally {
        drain();
        getServer().getPluginManager().disablePlugin(this);
      }
      return;
    }

    if (generationRules != null) {
      if (generationRules.isEnabled()) {
        info("Ore replacement in newly generated chunks is enabled; verify ore-removal.enabled if this is unintended.");
      }
    }

  }

  @Override
  public void onDisable() {
    drain();
    if (debugDump != null) {
      debugDump.close();
      debugDump = null;
    }
    if (languageSwitcher != null) {
      languageSwitcher.close();
      languageSwitcher = null;
    }
    if (languageService != null) {
      languageService.close();
      languageService = null;
    }
    if (remoteLanguages != null) {
      remoteLanguages.close();
      remoteLanguages = null;
    }
  }

  @Override
  public void onPreUnload(ReloadAware.PreUnloadReason reason) {
    info("BileTools pre-unload hook fired (%s). Draining HiddenOre runtime services.", reason);
    drain();
  }

  public void info(String message, Object... args) {
    log(Level.INFO, message, args);
  }

  public void debug(String message, Object... args) {
    log(Level.FINE, message, args);
  }

  public void warn(String message, Object... args) {
    log(Level.WARNING, message, args);
  }

  public void warnThrottled(String key, String message, Object... args) {
    Logger logger = getLogger();
    if (!logger.isLoggable(Level.WARNING)) {
      return;
    }
    long suppressed = claimLog(key);
    if (suppressed < 0L) {
      return;
    }
    logger.warning(withSuppressed(format(message, args), suppressed));
  }

  public void logException(Level level, Throwable failure, String message, Object... args) {
    Logger logger = getLogger();
    if (logger.isLoggable(level)) {
      logger.log(level, format(message, args), failure);
    }
  }

  private synchronized void drain() {
    if (draining) {
      return;
    }
    draining = true;
    if (configEditor != null) {
      configEditor.close();
      configEditor = null;
    }
    // Stop the bStats scheduler first so no chart callable observes a half-drained runtime.
    if (metrics != null) {
      metrics.shutdown();
      metrics = null;
    }
    if (serviceRegistered) {
      serviceRegistered = false;
      getServer().getServicesManager().unregister(HiddenOreService.class, api);
    }
    if (placeholderService != null) {
      placeholderService.unregister();
      placeholderService = null;
    }
    if (integrationService != null) {
      integrationService.unregister();
      integrationService = null;
    }
    if (configWatcher != null) {
      configWatcher.stop();
      configWatcher = null;
    }
    if (generationRules != null) {
      generationRules.close();
      generationRules = null;
    }
    debugPlayers.clear();
  }

  public static void sendMessage(CommandSender sender, Object component) {
    if (sender == null || component == null) {
      return;
    }
    ComponentMessenger.send(sender, ComponentText.component(component));
  }

  public MiningRuleManager getRuleManager() {
    return getRuntimeState().ruleManager();
  }

  public Messages getMessages() {
    return getRuntimeState().messages();
  }

  public Messages prepareReloadMessages(String configToml) {
    File configFile = new File(getDataFolder(), "hiddenore.toml");
    JsonObject config = loadToml(configToml, configFile);
    String locale = optionalString(config, "language", "en_US");
    Messages preparedMessages = new Messages(remoteLanguages, getDataFolder().toPath().resolve("languages"));
    preparedMessages.reload(runtimeState == null ? "en_US" : locale);
    return preparedMessages;
  }

  public boolean reloadAll(String configToml, Messages preparedMessages, long expectedRevision) {
    PluginLanguageService activeLanguages = languageService;
    if (activeLanguages == null) {
      return applyReloadSnapshot(configToml, preparedMessages, expectedRevision);
    }
    try {
      return activeLanguages.commitUpdate(() -> applyReloadSnapshot(configToml, preparedMessages, expectedRevision));
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to apply HiddenOre configuration changes.", exception);
    }
  }

  private synchronized boolean applyReloadSnapshot(String configToml, Messages preparedMessages, long expectedRevision) {
    if (draining) {
      throw new IllegalStateException("HiddenOre is shutting down");
    }
    if (configurationRevision.get() != expectedRevision) {
      return false;
    }

    File configFile = new File(getDataFolder(), "hiddenore.toml");
    applyReload(configFile, loadToml(configToml, configFile), preparedMessages);
    appliedConfigToml = configToml;
    return true;
  }

  private void applyReload(File configFile, JsonObject config, Messages nextMessages) {
    ConfigurationSettings settings = parseSettings(configFile, config);
    SeededVeinGenerator nextVeinGenerator = new SeededVeinGenerator(settings.rules().getAllDropRules());

    nextMessages.languageService(languageService);
    languageLocale = settings.language();
    runtimeState = new RuntimeState(settings.rules(), nextMessages, nextVeinGenerator, settings.generation(),
        settings.autoPickup(), settings.suppressBlockDrop(), settings.metrics());
    if (languageService != null) {
      languageService.invalidate();
    }
    updateMetrics(settings.metrics());
    HiddenOreTelemetry.countConfigReload();
  }

  private void updateMetrics(boolean enabled) {
    if (!enabled || BSTATS_PLUGIN_ID <= 0) {
      if (metrics != null) {
        metrics.shutdown();
        metrics = null;
      }
      return;
    }
    if (metrics == null) {
      try {
        metrics = HiddenOreMetrics.start(this, BSTATS_PLUGIN_ID);
      } catch (RuntimeException exception) {
        logException(Level.WARNING, exception, "Failed to initialize HiddenOre metrics.");
      }
    }
  }

  private DebugDumpContributor.Report captureDebugState() {
    String state = "Language: " + languageLocale + "\nDraining: " + draining + "\nDebug players: " + debugPlayers.size();
    return () -> state;
  }

  public BukkitDebugDump debugDump() {
    return debugDump;
  }

  public BukkitLanguageSwitcher languageSwitcher() {
    return languageSwitcher;
  }

  public BukkitConfigEditor configEditor() {
    return configEditor;
  }

  private LocalizationSnapshot prepareLanguage(String locale) {
    Messages messages = new Messages(remoteLanguages, getDataFolder().toPath().resolve("languages"));
    messages.reload(locale);
    return messages.defaultSnapshot();
  }

  private synchronized LocalizationSnapshot saveLanguageMessage(PluginLanguageEditor.Edit edit) throws Exception {
    return getMessages().editorOptions().writer().write(edit);
  }

  private synchronized void selectLanguage(String locale, LocalizationSnapshot prepared) throws Exception {
    if (draining) {
      throw new IllegalStateException("HiddenOre is shutting down");
    }
    writeLanguage(new File(getDataFolder(), "hiddenore.toml"), locale);
    configurationSaved();
    getMessages().install(prepared);
    languageLocale = locale;
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

  public RuntimeState runtimeStateOrNull() {
    return runtimeState;
  }

  public GenerationRules getGenerationRules() {
    return generationRules;
  }

  public boolean isDraining() {
    return draining;
  }

  public long configurationRevision() {
    return configurationRevision.get();
  }

  private ConfigEditorDocument loadConfigurationDocument() throws IOException {
    File file = new File(getDataFolder(), "hiddenore.toml");
    String source = readConfig(file);
    parseSettings(file, loadToml(source, file));
    return ConfigEditorDocument.fromToml(source);
  }

  private synchronized ConfigEditorDocument saveConfiguration(ConfigEditorDocument.Edit edit) throws IOException {
    if (draining) {
      throw new IllegalStateException("HiddenOre is shutting down");
    }
    File file = new File(getDataFolder(), "hiddenore.toml");
    ConfigEditorDocument saved = writeConfiguration(file, edit);
    configurationSaved();
    return saved;
  }

  private void configurationSaved() {
    configurationRevision.incrementAndGet();
    if (configWatcher != null) {
      configWatcher.configurationSaved();
    }
  }

  private static String readConfig(File file) {
    try {
      return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalArgumentException("Failed to load HiddenOre configuration file '" + file.getAbsolutePath()
          + "': " + exception.getMessage(), exception);
    }
  }

  static JsonObject loadToml(String content, File file) {
    try {
      JsonElement configuration = TomlCodec.toJsonElement(content);
      if (!configuration.isJsonObject()) {
        throw new IOException("Expected a TOML table");
      }
      return configuration.getAsJsonObject();
    } catch (IOException exception) {
      throw new IllegalArgumentException("Failed to load HiddenOre configuration file '" + file.getAbsolutePath()
          + "': " + exception.getMessage(), exception);
    }
  }

  static void writeLanguage(File file, String locale) throws IOException {
    String original = readConfig(file);
    loadToml(original, file);
    String content = TomlDocumentEditor.set(original, List.of("language"), new JsonPrimitive(locale));
    requireUnchanged(file, original);
    AtomicFileIO.writeString(file.toPath(), content);
  }

  static ConfigEditorDocument writeConfiguration(File file, ConfigEditorDocument.Edit edit) throws IOException {
    String original = edit.original().source();
    requireUnchanged(file, original);
    String content = TomlDocumentEditor.set(original, edit.path(), edit.value());
    parseSettings(file, loadToml(content, file));
    ConfigEditorDocument result = ConfigEditorDocument.fromToml(content);
    requireUnchanged(file, original);
    AtomicFileIO.writeString(file.toPath(), content);
    return result;
  }

  private static void requireUnchanged(File file, String original) throws IOException {
    if (!original.equals(readConfig(file))) {
      throw new IOException("Configuration changed while this editor was open. Reopen it and try again.");
    }
  }

  private static ConfigurationSettings parseSettings(File file, JsonObject configuration) {
    try {
      return new ConfigurationSettings(new MiningRuleManager(configuration),
          GenerationRules.parsePolicy(configuration),
          Messages.requireLocale(optionalString(configuration, "language", "en_US"), file.getName()),
          optionalBoolean(configuration, "auto_pickup_drops", false),
          optionalBoolean(configuration, "suppress_block_drop_on_custom_drop", false),
          optionalBoolean(configuration, "metrics", true));
    } catch (IllegalArgumentException exception) {
      throw invalidConfiguration(file, exception);
    }
  }

  private static IllegalArgumentException invalidConfiguration(File file, IllegalArgumentException cause) {
    return new IllegalArgumentException("Invalid HiddenOre configuration file '" + file.getAbsolutePath()
        + "': " + cause.getMessage(), cause);
  }

  private static boolean optionalBoolean(JsonObject configuration, String path, boolean defaultValue) {
    JsonElement value = configuration.get(path);
    if (value == null) {
      return defaultValue;
    }
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
      throw new IllegalArgumentException(path + ": expected true or false");
    }
    return value.getAsBoolean();
  }

  private static String optionalString(JsonObject configuration, String path, String defaultValue) {
    JsonElement value = configuration.get(path);
    if (value == null) {
      return defaultValue;
    }
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
      throw new IllegalArgumentException(path + ": expected a non-empty string");
    }
    return value.getAsString();
  }

  private void log(Level level, String message, Object... args) {
    Logger logger = getLogger();
    if (logger.isLoggable(level)) {
      logger.log(level, format(message, args));
    }
  }

  private long claimLog(String key) {
    LogThrottle throttle = logThrottles.computeIfAbsent(key, ignored -> new LogThrottle());
    return throttle.claim(System.nanoTime());
  }

  private static String format(String message, Object... args) {
    return args.length > 0 ? String.format(message, args) : message;
  }

  private static String withSuppressed(String message, long suppressed) {
    return suppressed > 0L ? message + " (" + suppressed + " similar failures suppressed.)" : message;
  }

  public record RuntimeState(MiningRuleManager ruleManager,
                             Messages messages,
                             SeededVeinGenerator veinGenerator,
                             GenerationRules.GenerationPolicy generationPolicy,
                             boolean autoPickup,
                             boolean suppressBlockDrop,
                             boolean metrics) {
  }

  private record ConfigurationSettings(MiningRuleManager rules, GenerationRules.GenerationPolicy generation,
                                       String language, boolean autoPickup, boolean suppressBlockDrop,
                                       boolean metrics) {
  }

  private static final class LogThrottle {
    private final AtomicLong nextLogAtNanos = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong suppressed = new AtomicLong();

    private long claim(long nowNanos) {
      while (true) {
        long next = nextLogAtNanos.get();
        if (next != Long.MIN_VALUE && nowNanos - next < 0L) {
          suppressed.incrementAndGet();
          return -1L;
        }
        if (nextLogAtNanos.compareAndSet(next, nowNanos + LOG_THROTTLE_NANOS)) {
          return suppressed.getAndSet(0L);
        }
      }
    }
  }
}
