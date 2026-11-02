package art.arcane.hiddenore.util.common;

import art.arcane.volmlib.util.director.DirectorMessages;
import art.arcane.volmlib.util.config.BukkitConfigMessages;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.format.ColorFormatter;
import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.LinesValue;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.LanguageFileEditor;
import art.arcane.volmlib.util.localization.LanguageReferenceRenderer;
import art.arcane.volmlib.util.localization.LanguageFileHeader;
import art.arcane.volmlib.util.localization.LanguageDirectory;
import art.arcane.volmlib.util.localization.LanguageMeta;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationIssue;
import art.arcane.volmlib.util.localization.LocalizationManager;
import art.arcane.volmlib.util.localization.LocalizationReloadResult;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageArgumentKind;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.ResolvedLines;
import art.arcane.volmlib.util.localization.ResolvedText;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.VolmitLocales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Messages {
  public static final TextKey DEBUG_DUMP_DESCRIPTION = TextKey.of("command.description.debugdump", "Create and optionally upload a diagnostic report");
  public static final TextKey DEBUG_GROUP_DESCRIPTION = TextKey.of("command.description.debug_group", "HiddenOre diagnostic tools");
  public static final TextKey VERSION_DESCRIPTION = TextKey.of("command.description.version", "Show the HiddenOre version");
  public static final TextKey DEBUG_DUMP_UPLOAD = TextKey.of("command.parameter.debugdump_upload", "Upload the report to mclo.gs");
  public static final TextKey PREFIX = TextKey.of("prefix", "&a[HiddenOre]&r ");
  public static final TextKey NO_PERMISSION = TextKey.of(
      "no_permission",
      "&cYou do not have permission to use this command.&r"
  );
  public static final TextKey PLAYER_ONLY = TextKey.of(
      "player_only",
      "&cThis command can only be used by a player.&r"
  );
  public static final TextKey DEBUG_ENABLED = TextKey.of(
      "debug_enabled",
      "&aDebug mode enabled.&r"
  );
  public static final TextKey DEBUG_DISABLED = TextKey.of(
      "debug_disabled",
      "&cDebug mode disabled.&r"
  );
  public static final TextKey CONFIG_RELOADED_MESSAGE = TextKey.of(
      "config_reloaded_message",
      "&aConfiguration updated and reloaded.&r"
  );
  public static final TextKey DEBUG_PLAYER_PLACED = TextKey.of(
      "debug.player_placed",
      "&cPlayer-placed {block}, no hidden drops.&r"
  );
  public static final TextKey DEBUG_RANDOM_DROP = TextKey.of(
      "debug.random_drop",
      "&aRandom drop: {material} x{amount}&r"
  );
  public static final TextKey DEBUG_RANDOM_DROP_LOST = TextKey.of(
      "debug.random_drop_lost",
      "&cRandom drop {material} lost because the tool tier is too low.&r"
  );
  public static final TextKey DEBUG_VEIN_DROP = TextKey.of(
      "debug.vein_drop",
      "&aVein {vein}: {material} x{amount}&r"
  );
  public static final TextKey DEBUG_VEIN_DROP_DISCOVERED = TextKey.of(
      "debug.vein_drop_discovered",
      "&aVein {vein}: {material} x{amount} (discovered)&r"
  );
  public static final TextKey DEBUG_VEIN_DROP_LOST = TextKey.of(
      "debug.vein_drop_lost",
      "&cVein {vein}: {material} lost because the tool tier is too low.&r"
  );
  public static final TextKey DEBUG_COMMAND_HIT = TextKey.of(
      "debug.command_hit",
      "&7Command roll: chance={chance}, roll={roll} -> &ahit&7&r"
  );
  public static final TextKey DEBUG_COMMAND_MISS = TextKey.of(
      "debug.command_miss",
      "&7Command roll: chance={chance}, roll={roll} -> &cmiss&7&r"
  );
  public static final TextKey COMMAND_ROOT_DESCRIPTION = TextKey.of(
      "command.description.root",
      "HiddenOre command root"
  );
  public static final TextKey COMMAND_DEBUG_DESCRIPTION = TextKey.of(
      "command.description.debug",
      "Toggle ore debug mode for yourself"
  );
  public static final TextKey COMMAND_CONFIG_DESCRIPTION = TextKey.of("command.description.config", "Edit HiddenOre settings");

  private static final String ENGLISH_LOCALE = VolmitLocales.ENGLISH;
  private static final int MAXIMUM_BASE_DEPTH = 4;
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
  private static final Pattern PLACEHOLDERS = Pattern.compile("\\{\\{|\\}\\}|\\{([^{}]+)\\}");
  private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();
  private static final List<MessageKey> PLUGIN_KEYS = List.of(
    DEBUG_GROUP_DESCRIPTION,
    VERSION_DESCRIPTION,
    DEBUG_DUMP_DESCRIPTION,
    DEBUG_DUMP_UPLOAD,
      PREFIX,
      NO_PERMISSION,
      PLAYER_ONLY,
      DEBUG_ENABLED,
      DEBUG_DISABLED,
      CONFIG_RELOADED_MESSAGE,
      DEBUG_PLAYER_PLACED,
      DEBUG_RANDOM_DROP,
      DEBUG_RANDOM_DROP_LOST,
      DEBUG_VEIN_DROP,
      DEBUG_VEIN_DROP_DISCOVERED,
      DEBUG_VEIN_DROP_LOST,
      DEBUG_COMMAND_HIT,
      DEBUG_COMMAND_MISS,
      COMMAND_ROOT_DESCRIPTION,
      COMMAND_DEBUG_DESCRIPTION,
      COMMAND_CONFIG_DESCRIPTION
  );
  private static final MessageCatalog CATALOG = createCatalog();

  private final LocalizationManager manager;
  private final HiddenOreLanguages languages;
  private PluginLanguageService languageService;
  private String activeLocale = ENGLISH_LOCALE;

  public Messages() {
    this(null);
  }

  public Messages(HiddenOreLanguages languages) {
    this.languages = languages;
    manager = new LocalizationManager(LocalizationCandidate.english(CATALOG, PluralSelector.oneOther()));
    if (languages != null) {
      ensureEnglish();
    }
  }

  public LocalizationReloadResult reload(String locale) {
    String requestedLocale = requireLocale(locale, "hiddenore.toml");
    LocalizationReloadResult result = manager.reload(() -> loadCandidate(requestedLocale));
    if (result.applied()) {
      activeLocale = requestedLocale;
      return result;
    }
    throw invalidReload("languages/" + requestedLocale + ".toml", result);
  }

  public Component component(TextKey key) {
    return component(key, MessageArgs.empty());
  }

  public Component component(TextKey key, MessageArgs arguments) {
    LocalizationSnapshot snapshot = selectedSnapshot(null);
    ResolvedText resolved = snapshot.resolve(key, arguments);
    String prefix = snapshot.resolve(PREFIX).template();
    return render(prefix + resolved.template(), resolved.arguments());
  }

  public List<Component> components(LinesKey key) {
    return components(key, MessageArgs.empty());
  }

  public List<Component> components(LinesKey key, MessageArgs arguments) {
    LocalizationSnapshot snapshot = selectedSnapshot(null);
    ResolvedLines resolved = snapshot.resolve(key, arguments);
    String prefix = snapshot.resolve(PREFIX).template();
    List<Component> components = new ArrayList<>(resolved.lines().size());
    for (String line : resolved.lines()) {
      components.add(render(prefix + line, resolved.arguments()));
    }
    return List.copyOf(components);
  }

  public Component component(CommandSender sender, TextKey key) {
    return component(sender, key, MessageArgs.empty());
  }

  public Component component(CommandSender sender, TextKey key, MessageArgs arguments) {
    LocalizationSnapshot snapshot = selectedSnapshot(sender);
    ResolvedText resolved = snapshot.resolve(key, arguments);
    String prefix = snapshot.resolve(PREFIX).template();
    return render(prefix + resolved.template(), resolved.arguments());
  }

  public void languageService(PluginLanguageService languageService) {
    this.languageService = languageService;
  }

  public LocalizationSnapshot defaultSnapshot() {
    return manager.snapshot();
  }

  public Map<String, LocalizationSnapshot> prepareInstalledLocales(Set<String> knownLocales) throws IOException {
    if (languages == null) {
      return Map.of();
    }
    Map<String, LocalizationSnapshot> prepared = new HashMap<>();
    LocalizationSnapshot english = LocalizationSnapshot.create(
        LocalizationCandidate.english(CATALOG, PluralSelector.oneOther()));
    for (String locale : knownLocales) {
      if (languages.isRepositoryLocale(locale) && Files.notExists(languagePath(locale), LinkOption.NOFOLLOW_LINKS)) {
        prepared.put(locale, english);
      }
    }
    for (String locale : languages.refresh()) {
      try {
        prepared.put(locale, loadEditorSnapshot(locale));
      } catch (Exception failure) {
        Logger.getLogger("HiddenOre").log(Level.WARNING, "Cannot prepare language " + languagePath(locale)
            + "; the last valid messages remain active.", failure);
      }
    }
    return Map.copyOf(prepared);
  }

  public void publishPreparedLocales(Map<String, LocalizationSnapshot> preparedLocales) {
    if (languageService == null) {
      return;
    }
    for (Map.Entry<String, LocalizationSnapshot> locale : preparedLocales.entrySet()) {
      languageService.cache(locale.getKey(), locale.getValue());
    }
    languageService.cache(activeLocale, defaultSnapshot());
  }

  public synchronized void install(LocalizationSnapshot prepared) {
    manager.install(prepared);
    activeLocale = prepared.overlays().isEmpty() ? ENGLISH_LOCALE : prepared.overlays().getFirst().locale();
  }

  public PluginLanguageEditor.Options editorOptions() {
    return new PluginLanguageEditor.Options(this::loadEditorSnapshot, this::saveEditor);
  }

  private LocalizationSnapshot loadEditorSnapshot(String locale) throws Exception {
    return LocalizationSnapshot.create(loadCandidate(locale));
  }

  private synchronized LocalizationSnapshot saveEditor(PluginLanguageEditor.Edit edit) throws Exception {
    LocaleOverlay edited = LocaleOverlay.builder("editor", edit.locale()).put(edit.key(), edit.value()).build();
    LocalizationValidator.validate(CATALOG, List.of(edited)).throwIfInvalid();
    loadEditorSnapshot(edit.locale());
    LocalizationCandidate base = LocalizationCandidate.english(CATALOG, PluralSelector.oneOther());
    Path path = languagePath(edit.locale());
    LocalizationSnapshot prepared = LanguageFileEditor.update(path, raw -> {
      LocalizationSnapshot current = withOverride(base, loadOverlay(raw, path.toString(), edit.locale()));
      MessageKey key = CATALOG.key(edit.key());
      if (key == null || !current.value(key).equals(edit.expected())) {
        throw new IOException("Language message changed while it was being edited: " + edit.key());
      }
      String updatedContent = TomlLanguageEditor.upsert(raw, edit.key(), edit.value()).content();
      LocalizationSnapshot updated = withOverride(base, loadOverlay(updatedContent, path.toString(), edit.locale()));
      return new LanguageFileEditor.Prepared<>(updatedContent, updated);
    });

    if (activeLocale.equals(edit.locale())) {
      manager.install(prepared);
    }
    return prepared;
  }

  private Path languagePath(String locale) {
    return languages.directory().languageFile(requireLocale(locale, "languages"));
  }

  private LocalizationSnapshot withOverride(LocalizationCandidate base, LocaleOverlay override) {
    List<LocaleOverlay> overlays = new ArrayList<>(base.overlays().size() + 1);
    overlays.add(override);
    overlays.addAll(base.overlays());
    return LocalizationSnapshot.create(new LocalizationCandidate(CATALOG, overlays, PluralSelector.oneOther()));
  }

  private LocalizationSnapshot selectedSnapshot(CommandSender sender) {
    if (languageService == null) {
      return manager.snapshot();
    }
    return sender instanceof Player player
        ? languageService.snapshot(player.getUniqueId()) : languageService.snapshot();
  }

  public DirectorTextResolver directorResolver() {
    return this::directorText;
  }

  static MessageCatalog catalog() {
    return CATALOG;
  }

  LocalizationSnapshot snapshot() {
    return manager.snapshot();
  }

  public String directorText(TextKey key, MessageArgs arguments) {
    MessageKey definition = CATALOG.key(key.id());
    if (!(definition instanceof TextKey textKey)) {
      return DirectorTextResolver.ENGLISH.resolve(key, arguments);
    }
    ResolvedText resolved = selectedSnapshot(null).resolve(textKey, arguments);
    return PLAIN_SERIALIZER.serialize(render(resolved.template(), resolved.arguments()));
  }

  private static MessageCatalog createCatalog() {
    MessageCatalog.Builder builder = MessageCatalog.builder(ENGLISH_LOCALE);
    builder.addAll(PLUGIN_KEYS);
    builder.addAll(DirectorMessages.keys());
    builder.addAll(BukkitLanguageMessages.keys());
    builder.addAll(BukkitConfigMessages.keys());
    return builder.build();
  }

  private LocalizationCandidate loadCandidate(String locale) throws Exception {
    if (languages == null) {
      return LocalizationCandidate.english(CATALOG, PluralSelector.oneOther());
    }
    List<LocaleOverlay> overlays = new ArrayList<>();
    String raw = readInstalled(locale);
    if (raw != null) {
      overlays.add(parseDownloadedOverlay(locale, raw));
      overlays.addAll(baseOverlays(locale, raw));
    }
    return new LocalizationCandidate(CATALOG, overlays, PluralSelector.oneOther());
  }

  private String readInstalled(String locale) throws IOException {
    Path file = languagePath(locale);
    if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
      return LanguageDirectory.readContent(file);
    }
    if (languages.isRepositoryLocale(locale)) {
      return languages.install(locale);
    }
    return null;
  }

  private List<LocaleOverlay> baseOverlays(String locale, String raw) throws IOException {
    List<LocaleOverlay> overlays = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    visited.add(normalizedLocale(locale));
    LanguageMeta meta = metaFromContent(locale, raw);
    int depth = 0;
    while (meta.base().isPresent() && depth < MAXIMUM_BASE_DEPTH) {
      String base = languages.canonical(meta.base().get());
      if (!visited.add(normalizedLocale(base)) || LanguageDirectory.sameLocale(base, ENGLISH_LOCALE)) {
        break;
      }
      Path baseFile = languagePath(base);
      if (!Files.isRegularFile(baseFile, LinkOption.NOFOLLOW_LINKS)) {
        break;
      }
      String baseContent = LanguageDirectory.readContent(baseFile);
      overlays.add(parseDownloadedOverlay(base, baseContent));
      meta = metaFromContent(base, baseContent);
      depth++;
    }
    return overlays;
  }

  private static LanguageMeta metaFromContent(String locale, String raw) {
    try {
      return LanguageMeta.parse(raw).withoutSelfBase(locale);
    } catch (IOException failure) {
      return LanguageMeta.empty();
    }
  }

  private static String normalizedLocale(String locale) {
    return locale.replace('-', '_').toLowerCase(Locale.ROOT);
  }

  private void ensureEnglish() {
    try {
      languages.ensureEnglish();
    } catch (IOException failure) {
      Logger.getLogger("HiddenOre").log(Level.WARNING,
          "Cannot refresh English language file " + languagePath(ENGLISH_LOCALE), failure);
    }
  }

  static String englishReference() {
    return LanguageReferenceRenderer.render(CATALOG, englishHeader());
  }

  static void validateContent(String locale, String content) throws IOException {
    LocalizationSnapshot.create(new LocalizationCandidate(CATALOG,
        List.of(loadOverlay(content, "upstream:" + locale, locale)), PluralSelector.oneOther()));
  }

  private static List<String> englishHeader() {
    return LanguageFileHeader.render(new LanguageFileHeader.Options(
        "HiddenOre", ENGLISH_LOCALE,
        List.of("prefix is added before chat messages. Set it to an empty string to hide it."),
        List.of("Use &0-&f for colors, &k-&o for formatting, &r to reset, and &#RRGGBB for hex colors.",
            "For example: &aGreen text&r. Use \\n inside quoted TOML strings for a new line."),
        Map.ofEntries(
            Map.entry("added", "Messages added by a language update"),
            Map.entry("after", "Message value after editing"),
            Map.entry("amount", "Number of dropped items"),
            Map.entry("argument", "Unexpected command argument"),
            Map.entry("before", "Message value before editing"),
            Map.entry("block", "Mined block type"),
            Map.entry("chance", "Configured trigger probability"),
            Map.entry("command", "Command path"),
            Map.entry("count", "Number of messages"),
            Map.entry("group", "Language editor category"),
            Map.entry("kept", "Local edits kept by a language update"),
            Map.entry("key", "Message or parameter key"),
            Map.entry("line", "Line number within a message"),
            Map.entry("locale", "Language code"),
            Map.entry("material", "Dropped item type"),
            Map.entry("maximum", "Maximum editor input length"),
            Map.entry("number", "Configuration list entry number"),
            Map.entry("page", "Current configuration page"),
            Map.entry("pages", "Total configuration pages"),
            Map.entry("parameter", "Command parameter name"),
            Map.entry("path", "Configuration setting or section"),
            Map.entry("personal", "Personal language code"),
            Map.entry("plugin", "Plugin name"),
            Map.entry("reason", "Error reason"),
            Map.entry("remaining", "Time until the next language check"),
            Map.entry("roll", "Sampled random value"),
            Map.entry("section", "Language editor section"),
            Map.entry("target", "Plugin receiving the language selection"),
            Map.entry("type", "Expected parameter type"),
            Map.entry("updated", "Messages changed by a language update"),
            Map.entry("value", "Current message or parameter value"),
            Map.entry("variables", "Allowed message placeholders"),
            Map.entry("vein", "Ore vein name")
        )));
  }

  private static LocaleOverlay loadOverlay(String raw, String source, String locale) throws IOException {
    LocaleOverlay.Builder overlay = LocaleOverlay.builder(source, locale);
    for (Map.Entry<String, MessageValue> entry : TomlLanguageParser.parseValidValues(raw, CATALOG).entrySet()) {
      MessageValue value = entry.getValue();
      if (value instanceof TextValue text && !PREFIX.id().equals(entry.getKey()) && text.template().isBlank()) {
        continue;
      }
      overlay.put(entry.getKey(), value);
    }
    return overlay.build();
  }

  private LocaleOverlay parseDownloadedOverlay(String locale, String raw) throws IOException {
    return loadOverlay(raw, "languages/" + locale + ".toml", locale);
  }

  public static String requireLocale(String locale, String source) {
    if (locale == null || locale.isBlank()) {
      throw invalid(source, "language", "expected a non-empty locale name");
    }
    String normalized = locale.trim();
    if (!normalized.matches("[A-Za-z0-9_-]{2,32}")) {
      throw invalid(source, "language", "use 2 to 32 letters, digits, underscores, or hyphens");
    }
    return normalized;
  }

  private Component render(String template, MessageArgs arguments) {
    if (template.indexOf('{') < 0 && template.indexOf('}') < 0) {
      return LEGACY.deserialize(ColorFormatter.translateColors(template));
    }
    String expanded = PLACEHOLDERS.matcher(template)
        .replaceAll(match -> expandTrustedArgument(match, arguments));
    Component formatted = LEGACY.deserialize(ColorFormatter.translateColors(expanded));
    return formatted.replaceText(TextReplacementConfig.builder()
        .match(PLACEHOLDERS)
        .replacement((match, builder) -> renderArgument(match, arguments))
        .build());
  }

  private String expandTrustedArgument(MatchResult match, MessageArgs arguments) {
    String name = match.group(1);
    if (name == null) {
      return Matcher.quoteReplacement(match.group());
    }
    MessageArgument argument = arguments.require(name);
    String replacement = argument.kind() == MessageArgumentKind.UNTRUSTED
        ? match.group() : String.valueOf(argument.value()).replace("{", "{{").replace("}", "}}");
    return Matcher.quoteReplacement(replacement);
  }

  private Component renderArgument(MatchResult match, MessageArgs arguments) {
    String name = match.group(1);
    if (name == null) {
      return Component.text(match.group().substring(0, 1));
    }
    return Component.text(String.valueOf(arguments.require(name).value()));
  }

  private IllegalArgumentException invalidReload(String source, LocalizationReloadResult result) {
    Exception failure = result.failure();
    String reason = failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
        ? validationReason(result)
        : failure.getMessage();
    return new IllegalArgumentException(source + ": localization reload rejected; " + reason, failure);
  }

  private String validationReason(LocalizationReloadResult result) {
    if (result.validation().errors().isEmpty()) {
      return "the previous localization snapshot remains active";
    }
    LocalizationIssue issue = result.validation().errors().getFirst();
    return issue.code() + " " + issue.key() + ": " + issue.detail();
  }

  private static IllegalArgumentException invalid(String source, String path, String detail) {
    return new IllegalArgumentException(source + ":" + path + ": " + detail);
  }
}
