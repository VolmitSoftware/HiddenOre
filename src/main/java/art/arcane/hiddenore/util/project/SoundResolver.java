package art.arcane.hiddenore.util.project;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.lang.reflect.Field;
import java.util.Locale;

public final class SoundResolver {
  private static final Registry<?> SOUND_REGISTRY = findSoundRegistry();

  private SoundResolver() {
  }

  public static Sound resolve(String configured, Sound fallback) {
    if (configured == null || configured.isBlank()) {
      return fallback;
    }

    String normalized = configured.trim().toLowerCase(Locale.ROOT);
    NamespacedKey key = NamespacedKey.fromString(normalized);
    if (key != null && SOUND_REGISTRY != null) {
      Object registered = SOUND_REGISTRY.get(key);
      if (registered instanceof Sound sound) {
        return sound;
      }
    }

    String fieldName = configured.trim().toUpperCase(Locale.ROOT);
    for (Field field : Sound.class.getFields()) {
      if (fieldName.equals(field.getName())) {
        Object value = readStaticField(field);
        if (value instanceof Sound sound) {
          return sound;
        }
      }
    }
    return fallback;
  }

  private static Registry<?> findSoundRegistry() {
    Field legacy = null;
    for (Field field : Registry.class.getFields()) {
      if ("SOUND_EVENT".equals(field.getName())) {
        return registryValue(field);
      }
      if ("SOUNDS".equals(field.getName())) {
        legacy = field;
      }
    }
    return legacy == null ? null : registryValue(legacy);
  }

  private static Registry<?> registryValue(Field field) {
    Object value = readStaticField(field);
    if (value instanceof Registry<?> registry) {
      return registry;
    }
    throw new IllegalStateException("Bukkit sound registry field has an unexpected type: " + field.getName());
  }

  private static Object readStaticField(Field field) {
    try {
      return field.get(null);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Cannot access Bukkit registry field " + field.getName(), exception);
    }
  }
}
