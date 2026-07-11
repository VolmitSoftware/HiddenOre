package art.arcane.hiddenore.vein;

import org.bukkit.configuration.ConfigurationSection;

public final class VeinConfig {
  private static final String DEFAULT_DISCOVERY_SOUND = "BLOCK_BEACON_POWER_SELECT";
  private static final float DEFAULT_DISCOVERY_VOLUME = 1.0f;
  private static final float DEFAULT_DISCOVERY_PITCH = 1.0f;
  private static final double MIN_DISCOVERY_PITCH = 0.5;
  private static final double MAX_DISCOVERY_PITCH = 2.0;

  public final GenerationMode generation;
  public final boolean allowPlacedBlocks;
  public final String discoverySound;
  public final float discoveryVolume;
  public final float discoveryPitch;

  public VeinConfig(ConfigurationSection section) {
    if (section == null) {
      throw invalid("veins", "expected a configuration section");
    }
    ConfigurationSection activeSection = section;
    generation = parseGeneration(activeSection.get("generation"));
    allowPlacedBlocks = parseAllowPlacedBlocks(activeSection.get("allow_placed_blocks"));

    Object rawSoundSection = activeSection.get("discovery_sound");
    if (rawSoundSection == null) {
      discoverySound = DEFAULT_DISCOVERY_SOUND;
      discoveryVolume = DEFAULT_DISCOVERY_VOLUME;
      discoveryPitch = DEFAULT_DISCOVERY_PITCH;
      return;
    }
    if (!(rawSoundSection instanceof ConfigurationSection)) {
      throw invalid("veins.discovery_sound", "expected a configuration section");
    }

    ConfigurationSection soundSection = (ConfigurationSection) rawSoundSection;
    discoverySound = parseSound(soundSection.get("sound"));
    discoveryVolume = parseVolume(soundSection.get("volume"));
    discoveryPitch = parsePitch(soundSection.get("pitch"));
  }

  private static GenerationMode parseGeneration(Object raw) {
    if (raw == null) {
      return GenerationMode.SEEDED;
    }
    if (!(raw instanceof String)) {
      throw invalid("veins.generation", "expected 'seeded' or 'pure_random'");
    }

    String normalized = ((String) raw).trim();
    return switch (normalized) {
      case "seeded" -> GenerationMode.SEEDED;
      case "pure_random" -> GenerationMode.PURE_RANDOM;
      default -> throw invalid("veins.generation", "unknown generation mode '" + raw + "'");
    };
  }

  private static boolean parseAllowPlacedBlocks(Object raw) {
    if (raw == null) {
      return false;
    }
    if (!(raw instanceof Boolean)) {
      throw invalid("veins.allow_placed_blocks", "expected true or false");
    }
    return (Boolean) raw;
  }

  private static String parseSound(Object raw) {
    if (raw == null) {
      return DEFAULT_DISCOVERY_SOUND;
    }
    if (!(raw instanceof String) || ((String) raw).isBlank()) {
      throw invalid("veins.discovery_sound.sound", "expected a non-empty sound name");
    }
    return ((String) raw).trim();
  }

  private static float parseVolume(Object raw) {
    if (raw == null) {
      return DEFAULT_DISCOVERY_VOLUME;
    }
    if (!(raw instanceof Number)) {
      throw invalid("veins.discovery_sound.volume", "must be a finite number greater than or equal to 0");
    }

    double value = ((Number) raw).doubleValue();
    if (!Double.isFinite(value) || value < 0.0 || value > Float.MAX_VALUE) {
      throw invalid("veins.discovery_sound.volume", "must be a finite number greater than or equal to 0");
    }
    return (float) value;
  }

  private static float parsePitch(Object raw) {
    if (raw == null) {
      return DEFAULT_DISCOVERY_PITCH;
    }
    if (!(raw instanceof Number)) {
      throw invalid("veins.discovery_sound.pitch", "must be a finite number between 0.5 and 2 inclusive");
    }

    double value = ((Number) raw).doubleValue();
    if (!Double.isFinite(value) || value < MIN_DISCOVERY_PITCH || value > MAX_DISCOVERY_PITCH) {
      throw invalid("veins.discovery_sound.pitch", "must be a finite number between 0.5 and 2 inclusive");
    }
    return (float) value;
  }

  private static IllegalArgumentException invalid(String path, String message) {
    return new IllegalArgumentException(path + ": " + message);
  }

  public enum GenerationMode {SEEDED, PURE_RANDOM}
}
