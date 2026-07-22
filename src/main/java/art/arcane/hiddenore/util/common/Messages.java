package art.arcane.hiddenore.util.common;

import art.arcane.volmlib.util.director.DirectorMessages;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.LinesValue;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationIssue;
import art.arcane.volmlib.util.localization.LocalizationManager;
import art.arcane.volmlib.util.localization.LocalizationReloadResult;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Messages {
  public static final TextKey PREFIX = TextKey.of("prefix", "<green>[HiddenOre]</green> ");
  public static final TextKey NO_PERMISSION = TextKey.of(
      "no_permission",
      "<red>You do not have permission to use this command.</red>"
  );
  public static final TextKey RELOADED = TextKey.of(
      "reloaded",
      "<gold>Plugin configuration and translations reloaded.</gold>"
  );
  public static final TextKey RELOAD_FAILED = TextKey.of(
      "reload_failed",
      "<red>Reload failed. The previous configuration is still active; check the console.</red>"
  );
  public static final TextKey PLAYER_ONLY = TextKey.of(
      "player_only",
      "<red>This command can only be used by a player.</red>"
  );
  public static final TextKey DEBUG_ENABLED = TextKey.of(
      "debug_enabled",
      "<green>Debug mode enabled.</green>"
  );
  public static final TextKey DEBUG_DISABLED = TextKey.of(
      "debug_disabled",
      "<red>Debug mode disabled.</red>"
  );
  public static final TextKey CONFIG_RELOADED_MESSAGE = TextKey.of(
      "config_reloaded_message",
      "<green>Configuration updated and reloaded.</green>"
  );
  public static final LinesKey USAGE = LinesKey.of(
      "usage",
      "<aqua>HiddenOre by VolmitSoftware</aqua>",
      "<yellow>Usage: <white>/hiddenore reload</white></yellow>",
      "<yellow>Debug: <white>/hiddenore debug</white></yellow>"
  );
  public static final TextKey DEBUG_PLAYER_PLACED = TextKey.of(
      "debug.player_placed",
      "<red>Player-placed {block}, no hidden drops.</red>"
  );
  public static final TextKey DEBUG_RANDOM_DROP = TextKey.of(
      "debug.random_drop",
      "<green>Random drop: {material} x{amount}</green>"
  );
  public static final TextKey DEBUG_RANDOM_DROP_LOST = TextKey.of(
      "debug.random_drop_lost",
      "<red>Random drop {material} lost because the tool tier is too low.</red>"
  );
  public static final TextKey DEBUG_VEIN_DROP = TextKey.of(
      "debug.vein_drop",
      "<green>Vein {vein}: {material} x{amount}</green>"
  );
  public static final TextKey DEBUG_VEIN_DROP_DISCOVERED = TextKey.of(
      "debug.vein_drop_discovered",
      "<green>Vein {vein}: {material} x{amount} (discovered)</green>"
  );
  public static final TextKey DEBUG_VEIN_DROP_LOST = TextKey.of(
      "debug.vein_drop_lost",
      "<red>Vein {vein}: {material} lost because the tool tier is too low.</red>"
  );
  public static final TextKey DEBUG_COMMAND_HIT = TextKey.of(
      "debug.command_hit",
      "<gray>Command roll: chance={chance}, roll={roll} -> <green>hit</green></gray>"
  );
  public static final TextKey DEBUG_COMMAND_MISS = TextKey.of(
      "debug.command_miss",
      "<gray>Command roll: chance={chance}, roll={roll} -> <red>miss</red></gray>"
  );
  public static final TextKey COMMAND_ROOT_DESCRIPTION = TextKey.of(
      "command.description.root",
      "HiddenOre command root"
  );
  public static final TextKey COMMAND_RELOAD_DESCRIPTION = TextKey.of(
      "command.description.reload",
      "Reload HiddenOre configuration and language files"
  );
  public static final TextKey COMMAND_DEBUG_DESCRIPTION = TextKey.of(
      "command.description.debug",
      "Toggle ore debug mode for yourself"
  );

  private static final String ENGLISH_LOCALE = VolmitLocales.ENGLISH;
  private static final MiniMessage MINI_MESSAGE = MiniMessage.builder().strict(true).build();
  private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();
  private static final Set<String> NON_MESSAGE_PATHS = Set.of(
      "locale",
      "config_reloaded_sound",
      "config_reloaded_sound_volume",
      "config_reloaded_sound_pitch"
  );
  private static final List<MessageKey> PLUGIN_KEYS = List.of(
      PREFIX,
      NO_PERMISSION,
      RELOADED,
      RELOAD_FAILED,
      PLAYER_ONLY,
      DEBUG_ENABLED,
      DEBUG_DISABLED,
      CONFIG_RELOADED_MESSAGE,
      USAGE,
      DEBUG_PLAYER_PLACED,
      DEBUG_RANDOM_DROP,
      DEBUG_RANDOM_DROP_LOST,
      DEBUG_VEIN_DROP,
      DEBUG_VEIN_DROP_DISCOVERED,
      DEBUG_VEIN_DROP_LOST,
      DEBUG_COMMAND_HIT,
      DEBUG_COMMAND_MISS,
      COMMAND_ROOT_DESCRIPTION,
      COMMAND_RELOAD_DESCRIPTION,
      COMMAND_DEBUG_DESCRIPTION
  );
  private static final MessageCatalog CATALOG = createCatalog();

  private final LocalizationManager manager;

  public Messages() {
    manager = new LocalizationManager(LocalizationCandidate.english(CATALOG, PluralSelector.oneOther()));
    validateCatalogTemplates();
  }

  public LocalizationReloadResult reload(YamlConfiguration language, String source) {
    YamlConfiguration configuration = Objects.requireNonNull(language, "Language configuration cannot be null");
    String overlaySource = source == null || source.isBlank() ? "language.yml" : source;
    LocalizationReloadResult result = manager.reload(() -> loadCandidate(configuration, overlaySource));
    if (result.applied()) {
      return result;
    }
    throw invalidReload(overlaySource, result);
  }

  public Component component(TextKey key) {
    return component(key, MessageArgs.empty());
  }

  public Component component(TextKey key, MessageArgs arguments) {
    LocalizationSnapshot snapshot = manager.snapshot();
    ResolvedText resolved = snapshot.resolve(key, arguments);
    String prefix = snapshot.resolve(PREFIX).template();
    return MINI_MESSAGE.deserialize(prefix + interpolate(resolved.template(), resolved.arguments()));
  }

  public List<Component> components(LinesKey key) {
    return components(key, MessageArgs.empty());
  }

  public List<Component> components(LinesKey key, MessageArgs arguments) {
    LocalizationSnapshot snapshot = manager.snapshot();
    ResolvedLines resolved = snapshot.resolve(key, arguments);
    String prefix = snapshot.resolve(PREFIX).template();
    List<Component> components = new ArrayList<>(resolved.lines().size());
    for (String line : resolved.lines()) {
      components.add(MINI_MESSAGE.deserialize(prefix + interpolate(line, resolved.arguments())));
    }
    return List.copyOf(components);
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
    ResolvedText resolved = manager.snapshot().resolve(textKey, arguments);
    String text = PLAIN_SERIALIZER.serialize(
        MINI_MESSAGE.deserialize(interpolate(resolved.template(), resolved.arguments()))
    );
    return text.replace(String.valueOf('\u00A7'), "");
  }

  private static MessageCatalog createCatalog() {
    MessageCatalog.Builder builder = MessageCatalog.builder(ENGLISH_LOCALE);
    builder.addAll(PLUGIN_KEYS);
    builder.addAll(DirectorMessages.keys());
    return builder.build();
  }

  private LocalizationCandidate loadCandidate(YamlConfiguration language, String source) throws Exception {
    String locale = readLocale(language, source);
    List<LocaleOverlay> overlays = new ArrayList<>();
    overlays.add(loadOverlay(language, source, locale));
    LocaleOverlay bundled = loadBundledOverlay(locale);
    if (bundled != null) {
      overlays.add(bundled);
    }
    return new LocalizationCandidate(CATALOG, overlays, PluralSelector.oneOther());
  }

  private LocaleOverlay loadOverlay(YamlConfiguration language, String source, String locale) {
    LocaleOverlay.Builder overlay = LocaleOverlay.builder(source, locale);
    for (Map.Entry<String, Object> entry : language.getValues(true).entrySet()) {
      String path = entry.getKey();
      Object value = entry.getValue();
      if (NON_MESSAGE_PATHS.contains(path) || value instanceof ConfigurationSection) {
        continue;
      }
      addOverlayValue(overlay, source, path, value);
    }
    return overlay.build();
  }

  private LocaleOverlay loadBundledOverlay(String locale) throws Exception {
    if (VolmitLocales.ENGLISH.equals(locale)) {
      return null;
    }

    String resourcePath = "/languages/" + locale + ".yml";
    InputStream input = Messages.class.getResourceAsStream(resourcePath);
    if (input == null) {
      if (VolmitLocales.isBundled(locale)) {
        throw new IllegalArgumentException("Missing bundled language resource: " + resourcePath);
      }
      return null;
    }

    try (InputStream stream = input; InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      YamlConfiguration language = new YamlConfiguration();
      language.load(reader);
      String declaredLocale = readLocale(language, resourcePath);
      if (!locale.equals(declaredLocale)) {
        throw invalid(resourcePath, "locale", "expected " + locale + " but found " + declaredLocale);
      }
      return loadOverlay(language, resourcePath, locale);
    }
  }

  private String readLocale(YamlConfiguration language, String source) {
    Object configured = language.get("locale");
    if (configured == null) {
      return ENGLISH_LOCALE;
    }
    if (!(configured instanceof String locale) || locale.isBlank()) {
      throw invalid(source, "locale", "expected a non-empty locale name");
    }
    return locale.trim();
  }

  private void addOverlayValue(LocaleOverlay.Builder overlay, String source, String path, Object value) {
    MessageKey definition = CATALOG.key(path);
    if (definition instanceof TextKey) {
      String template = requireText(source, path, value, PREFIX.id().equals(path));
      validateTemplate(source + ":" + path, template, sampleArguments(new TextValue(template).placeholders()));
      overlay.text(path, template);
      return;
    }
    if (definition instanceof LinesKey) {
      List<String> lines = requireLines(source, path, value);
      LinesValue linesValue = new LinesValue(lines);
      validateLines(source + ":" + path, linesValue.lines(), sampleArguments(linesValue.placeholders()));
      overlay.lines(path, lines);
      return;
    }
    if (value instanceof String template) {
      overlay.text(path, template);
      return;
    }
    if (value instanceof List<?>) {
      overlay.lines(path, requireLines(source, path, value));
      return;
    }
    throw invalid(source, path, "expected a message string or non-empty list of message strings");
  }

  private String requireText(String source, String path, Object value, boolean allowEmpty) {
    if (!(value instanceof String template) || (!allowEmpty && template.isBlank())) {
      throw invalid(source, path, allowEmpty ? "expected a string" : "expected a non-empty message string");
    }
    return template;
  }

  private List<String> requireLines(String source, String path, Object value) {
    if (!(value instanceof List<?> configured) || configured.isEmpty()) {
      throw invalid(source, path, "expected a non-empty list of message strings");
    }
    List<String> lines = new ArrayList<>(configured.size());
    for (int index = 0; index < configured.size(); index++) {
      Object line = configured.get(index);
      if (!(line instanceof String text) || text.isBlank()) {
        throw invalid(source, path + "[" + index + "]", "expected a non-empty message string");
      }
      lines.add(text);
    }
    return List.copyOf(lines);
  }

  private void validateCatalogTemplates() {
    for (MessageKey key : CATALOG.keys()) {
      MessageValue value = key.englishValue();
      MessageArgs arguments = sampleArguments(value.placeholders());
      if (value instanceof TextValue text) {
        validateTemplate("catalog:" + key.id(), text.template(), arguments);
      } else if (value instanceof LinesValue lines) {
        validateLines("catalog:" + key.id(), lines.lines(), arguments);
      }
    }
  }

  private void validateLines(String path, List<String> lines, MessageArgs arguments) {
    for (int index = 0; index < lines.size(); index++) {
      validateTemplate(path + "[" + index + "]", lines.get(index), arguments);
    }
  }

  private void validateTemplate(String path, String template, MessageArgs arguments) {
    validatePlaceholderPlacement(path, template);
    try {
      MINI_MESSAGE.deserialize(interpolate(template, arguments));
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(path + ": invalid MiniMessage", exception);
    }
  }

  private void validatePlaceholderPlacement(String path, String template) {
    boolean insideTag = false;
    for (int index = 0; index < template.length(); index++) {
      char current = template.charAt(index);
      if (current == '\\') {
        index++;
        continue;
      }
      if (current == '<') {
        insideTag = true;
        continue;
      }
      if (current == '>') {
        insideTag = false;
        continue;
      }
      if (insideTag && current == '{' && (index + 1 >= template.length() || template.charAt(index + 1) != '{')) {
        throw new IllegalArgumentException(path + ": message placeholders cannot be used inside MiniMessage tags");
      }
    }
  }

  private MessageArgs sampleArguments(Set<String> placeholders) {
    MessageArgs.Builder builder = MessageArgs.builder();
    for (String placeholder : placeholders) {
      builder.untrusted(placeholder, "value");
    }
    return builder.build();
  }

  private String interpolate(String template, MessageArgs arguments) {
    StringBuilder output = new StringBuilder(template.length());
    int index = 0;
    while (index < template.length()) {
      char current = template.charAt(index);
      if (current == '{' && index + 1 < template.length() && template.charAt(index + 1) == '{') {
        output.append('{');
        index += 2;
        continue;
      }
      if (current == '}' && index + 1 < template.length() && template.charAt(index + 1) == '}') {
        output.append('}');
        index += 2;
        continue;
      }
      if (current != '{') {
        output.append(current);
        index++;
        continue;
      }
      int end = template.indexOf('}', index + 1);
      String name = template.substring(index + 1, end);
      MessageArgument argument = arguments.require(name);
      String replacement = String.valueOf(argument.value());
      if (argument.kind() == MessageArgumentKind.UNTRUSTED) {
        replacement = MINI_MESSAGE.escapeTags(replacement);
      }
      output.append(replacement);
      index = end + 1;
    }
    return output.toString();
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

  private IllegalArgumentException invalid(String source, String path, String detail) {
    return new IllegalArgumentException(source + ":" + path + ": " + detail);
  }
}
