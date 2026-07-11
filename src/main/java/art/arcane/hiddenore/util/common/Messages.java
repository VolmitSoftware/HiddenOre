package art.arcane.hiddenore.util.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Messages {
  private static final Map<String, String> DEFAULT_MESSAGES = Map.of(
      "no_permission", "<red>You do not have permission to use this command.</red>",
      "reloaded", "<gold>Plugin config & messages reloaded.</gold>",
      "reload_failed", "<red>Reload failed. The previous configuration is still active; check the console.</red>",
      "player_only", "<red>This command can only be used by a player.</red>"
  );
  private static final List<String> DEFAULT_USAGE = List.of(
      "<aqua>HiddenOre by VolmitSoftware</aqua>",
      "<yellow>Usage: <white>/hiddenore reload</white></yellow>",
      "<yellow>Debug: <white>/hiddenore debug</white></yellow>"
  );

  private final MiniMessage miniMessage = MiniMessage.builder().strict(true).build();
  private final String prefix;
  private final Map<String, Component> messages;
  private final List<Component> usage;

  public Messages(YamlConfiguration language) {
    YamlConfiguration lang = Objects.requireNonNull(language, "language");
    prefix = optionalString(lang.get("prefix"), "prefix", "", true);

    Map<String, Component> parsedMessages = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : DEFAULT_MESSAGES.entrySet()) {
      String raw = optionalString(lang.get(entry.getKey()), entry.getKey(), entry.getValue(), false);
      parsedMessages.put(entry.getKey(), parseConfigured(entry.getKey(), raw));
    }
    messages = Map.copyOf(parsedMessages);
    usage = parseUsage(lang.get("usage"));
  }

  public Component get(String key) {
    Component message = messages.get(key);
    return message == null ? parse("<red>Missing message: " + key + "</red>") : message;
  }

  public List<Component> getList(String key) {
    return "usage".equals(key) ? usage : List.of();
  }

  public Component parse(String raw) {
    return miniMessage.deserialize(prefix + Objects.requireNonNull(raw, "Message text cannot be null"));
  }

  public Component parseConfigured(String path, String raw) {
    try {
      return parse(raw);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(path + ": invalid MiniMessage", exception);
    }
  }

  private List<Component> parseUsage(Object rawUsage) {
    List<?> configuredUsage;
    if (rawUsage == null) {
      configuredUsage = DEFAULT_USAGE;
    } else if (rawUsage instanceof List<?>) {
      configuredUsage = (List<?>) rawUsage;
    } else {
      throw invalid("usage", "expected a non-empty list of messages");
    }
    if (configuredUsage.isEmpty()) {
      throw invalid("usage", "expected a non-empty list of messages");
    }

    List<Component> parsed = new ArrayList<>(configuredUsage.size());
    for (int index = 0; index < configuredUsage.size(); index++) {
      Object value = configuredUsage.get(index);
      if (!(value instanceof String) || ((String) value).isBlank()) {
        throw invalid("usage[" + index + "]", "expected a non-empty message");
      }
      parsed.add(parseConfigured("usage[" + index + "]", (String) value));
    }
    return List.copyOf(parsed);
  }

  private String optionalString(Object value, String path, String defaultValue, boolean allowEmpty) {
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof String) || (!allowEmpty && ((String) value).isBlank())) {
      throw invalid(path, allowEmpty ? "expected a string" : "expected a non-empty message");
    }
    return (String) value;
  }

  private IllegalArgumentException invalid(String path, String message) {
    return new IllegalArgumentException(path + ": " + message);
  }
}
