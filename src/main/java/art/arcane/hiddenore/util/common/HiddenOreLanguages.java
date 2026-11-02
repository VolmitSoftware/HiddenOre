package art.arcane.hiddenore.util.common;

import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.volmlib.util.localization.GitHubLanguageSource;
import art.arcane.volmlib.util.localization.LanguageCatalogMerge;
import art.arcane.volmlib.util.localization.LanguageDirectory;
import art.arcane.volmlib.util.localization.LanguageMeta;
import art.arcane.volmlib.util.localization.LanguageSourceManifest;
import art.arcane.volmlib.util.localization.LanguageUpstreamStore;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.VolmitLocales;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HiddenOreLanguages implements AutoCloseable {
  public static final String MANIFEST_RESOURCE = "hiddenore-language-source.properties";
  private static final String PRODUCT = "HiddenOre";

  private final LanguageDirectory directory;
  private final Logger logger;
  private final LanguageSourceManifest manifest;
  private final LanguageUpstreamSync sync;
  private final Throwable sourceFailure;
  private volatile PluginLanguageService selections;
  private volatile LanguageUpstreamSync.SelfWriteListener selfWrite;
  private volatile LanguageUpstreamSync.Applied applied;
  private volatile List<String> availableLocales = List.of(VolmitLocales.ENGLISH);
  private volatile Map<String, LanguageMeta> metas = Map.of();

  public HiddenOreLanguages(Path root, Logger logger) {
    this(root, logger, GitHubLanguageSource.API_ROOT, GitHubLanguageSource.RAW_ROOT,
        LanguageUpstreamSync.MINIMUM_CHECK_INTERVAL);
  }

  HiddenOreLanguages(Path root, Logger logger, URI apiRoot, URI rawRoot, Duration minimumCheckInterval) {
    directory = new LanguageDirectory(root);
    this.logger = Objects.requireNonNull(logger, "logger");
    LanguageSourceManifest loadedManifest = null;
    LanguageUpstreamSync loadedSync = null;
    Throwable failure = null;
    try {
      loadedManifest = LanguageSourceManifest.load(HiddenOreLanguages.class.getClassLoader(), MANIFEST_RESOURCE);
      loadedSync = new LanguageUpstreamSync(new LanguageUpstreamSync.Options(
          PRODUCT,
          Messages.catalog(),
          directory,
          loadedManifest,
          new GitHubLanguageSource(loadedManifest, PRODUCT, apiRoot, rawRoot),
          new LanguageUpstreamStore(directory, loadedManifest),
          Messages::englishReference,
          Messages::validateContent,
          this::commit,
          this::written,
          this::applyUpstream,
          logger), minimumCheckInterval);
    } catch (RuntimeException exception) {
      failure = exception;
    }
    manifest = loadedManifest;
    sync = loadedSync;
    sourceFailure = failure;
    try {
      refresh();
    } catch (IOException exception) {
      logger.log(Level.WARNING, "Could not scan HiddenOre languages in " + root, exception);
    }
  }

  public void selfWrite(LanguageUpstreamSync.SelfWriteListener selfWrite) {
    this.selfWrite = selfWrite;
  }

  public void bind(PluginLanguageService selections, LanguageUpstreamSync.Applied applied) {
    this.selections = selections;
    this.applied = applied;
  }

  public LanguageDirectory directory() {
    return directory;
  }

  public void ensureEnglish() throws IOException {
    if (sync == null) {
      directory.prepare();
      Path english = directory.languageFile(VolmitLocales.ENGLISH);
      if (!directory.exists(VolmitLocales.ENGLISH)) {
        String reference = Messages.englishReference();
        AtomicFileIO.writeString(english, reference);
        written(english, reference);
      }
      return;
    }
    try {
      sync.mergeEnglish().ifPresent(result -> logger.info("Updated HiddenOre language " + VolmitLocales.ENGLISH
          + " from the built-in catalog: " + result.summary() + "."));
    } catch (IOException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IOException("Could not refresh the English language file", exception);
    }
  }

  public String install(String locale) throws IOException {
    String requiredLocale = canonical(locale);
    if (sync == null) {
      throw new IOException("Language " + requiredLocale + " is not installed and the language source is "
          + sourceDescription());
    }
    logger.info("Installing HiddenOre language " + requiredLocale + " from " + sourceDescription() + "...");
    String content = sync.install(requiredLocale);
    logger.info("Installed HiddenOre language " + requiredLocale + " to "
        + directory.languageFile(requiredLocale).toAbsolutePath().normalize() + ".");
    refresh();
    return content;
  }

  public CompletableFuture<String> requestInstall(String locale) {
    String requiredLocale = canonical(locale);
    if (directory.exists(requiredLocale)) {
      try {
        return CompletableFuture.completedFuture(directory.read(requiredLocale));
      } catch (IOException exception) {
        return CompletableFuture.failedFuture(exception);
      }
    }
    if (sync == null || !isRepositoryLocale(requiredLocale)) {
      return CompletableFuture.failedFuture(new IOException("Language " + requiredLocale
          + " is not available from " + sourceDescription()));
    }
    logger.info("Installing HiddenOre language " + requiredLocale + " from " + sourceDescription() + "...");
    return sync.installAsync(requiredLocale).thenApply(content -> {
      logger.info("Installed HiddenOre language " + requiredLocale + " to "
          + directory.languageFile(requiredLocale).toAbsolutePath().normalize() + ".");
      refreshQuietly();
      return content;
    });
  }

  public CompletableFuture<LanguageUpstreamSync.SyncReport> startUpstreamCheck() {
    if (sync == null) {
      return CompletableFuture.completedFuture(LanguageUpstreamSync.SyncReport.failed(
          sourceDescription(), System.currentTimeMillis()));
    }
    return sync.checkAsync().thenApply(this::logReport);
  }

  public BukkitLanguageSwitcher.LanguageUpdateAction updateAction() {
    return this::startUpstreamCheck;
  }

  public BukkitLanguageSwitcher.LocaleDisplayNames displayNames() {
    return locale -> meta(locale).name().orElse(null);
  }

  public String displayName(String locale) {
    String normalized = locale == null ? "" : locale.trim();
    return meta(normalized).name().or(() -> VolmitLocales.displayName(normalized)).orElse(normalized);
  }

  public LanguageMeta meta(String locale) {
    return metas.getOrDefault(locale == null ? "" : locale.trim(), LanguageMeta.empty());
  }

  public List<String> availableLocales() {
    return availableLocales;
  }

  public boolean isRepositoryLocale(String locale) {
    return manifest != null && manifest.contains(locale);
  }

  public String canonical(String locale) {
    String requiredLocale = LanguageDirectory.requireLocale(locale);
    if (LanguageDirectory.sameLocale(requiredLocale, VolmitLocales.ENGLISH)) {
      return VolmitLocales.ENGLISH;
    }
    return isRepositoryLocale(requiredLocale) ? manifest.canonical(requiredLocale) : requiredLocale;
  }

  public Optional<Throwable> sourceFailure() {
    return Optional.ofNullable(sourceFailure);
  }

  public String sourceDescription() {
    return manifest == null ? "unavailable" : manifest.repository() + "@" + manifest.branch();
  }

  public Optional<LanguageUpstreamSync.Status> upstreamStatus() {
    return sync == null ? Optional.empty() : Optional.of(sync.status());
  }

  public String sourceState() {
    if (sourceFailure != null) {
      return "unavailable (" + sourceFailure.getClass().getSimpleName() + ")";
    }
    Optional<LanguageUpstreamSync.Status> status = upstreamStatus();
    if (status.isEmpty()) {
      return "unavailable";
    }
    LanguageUpstreamSync.Status current = status.get();
    StringBuilder state = new StringBuilder();
    state.append(current.revision().map(sha -> "revision " + sha.substring(0, 7)).orElse("not checked"));
    if (current.checkedMillis() > 0L) {
      state.append(", checked ").append(Instant.ofEpochMilli(current.checkedMillis()));
    }
    current.lastError().ifPresent(error -> state.append(", last error: ").append(error));
    return state.toString();
  }

  public List<String> refresh() throws IOException {
    LinkedHashSet<String> choices = new LinkedHashSet<>();
    choices.add(VolmitLocales.ENGLISH);
    if (manifest != null) {
      choices.addAll(manifest.locales());
    }
    List<String> installed = directory.scan();
    choices.addAll(installed);
    LinkedHashMap<String, LanguageMeta> discovered = new LinkedHashMap<>();
    for (String locale : installed) {
      try {
        LanguageMeta meta = directory.meta(locale);
        if (!meta.isEmpty()) {
          discovered.put(locale, meta);
        }
      } catch (IOException exception) {
        logger.log(Level.FINE, "Could not read language metadata for " + locale, exception);
      }
    }
    List<String> sorted = new ArrayList<>(choices);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);
    metas = Map.copyOf(discovered);
    availableLocales = List.copyOf(sorted);
    return installed;
  }

  @Override
  public void close() {
    bind(null, null);
    selfWrite(null);
    if (sync != null) {
      sync.close();
    }
  }

  private void refreshQuietly() {
    try {
      refresh();
    } catch (IOException exception) {
      logger.log(Level.WARNING, "Could not scan HiddenOre languages in " + directory.root(), exception);
    }
  }

  private <T> T commit(LanguageUpstreamSync.CommitAction<T> action) throws IOException {
    PluginLanguageService active = selections;
    if (active == null) {
      return action.apply();
    }
    return active.commitUpdate(action::apply);
  }

  private void written(Path file, String content) {
    LanguageUpstreamSync.SelfWriteListener listener = selfWrite;
    if (listener != null) {
      listener.written(file, content);
    }
  }

  private void applyUpstream(String locale, String content, LanguageCatalogMerge.MergeResult result) throws Exception {
    refresh();
    LanguageUpstreamSync.Applied listener = applied;
    if (listener != null) {
      listener.applied(locale, content, result);
    }
  }

  private LanguageUpstreamSync.SyncReport logReport(LanguageUpstreamSync.SyncReport report) {
    String revision = report.revision().map(sha -> sha.substring(0, 7)).orElse("unknown");
    for (String locale : report.changedLocales()) {
      LanguageCatalogMerge.MergeResult result = report.merged().get(locale);
      String source = locale.equals(VolmitLocales.ENGLISH)
          ? "the built-in catalog" : sourceDescription() + " (" + revision + ")";
      logger.info("Updated HiddenOre language " + locale + " from " + source + ": " + result.summary() + ".");
    }
    if (report.failure().isEmpty() && report.changedLocales().isEmpty() && report.failures().isEmpty()
        && report.revision().isPresent() && !report.coolingDown()) {
      logger.info("HiddenOre languages match " + sourceDescription() + " (" + revision + ").");
    }
    return report;
  }
}
