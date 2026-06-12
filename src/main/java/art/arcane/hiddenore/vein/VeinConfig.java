package art.arcane.hiddenore.vein;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

public class VeinConfig {
  public final GenerationMode generation;
  public final boolean allowPlacedBlocks;
  public final String discoverySound;
  public final float discoveryVolume;
  public final float discoveryPitch;

  public VeinConfig(ConfigurationSection section) {
    generation = parseGeneration(section == null ? null : section.getString("generation", "seeded"));
    allowPlacedBlocks = section == null || section.getBoolean("allow_placed_blocks", true);
    ConfigurationSection soundSection = section == null ? null : section.getConfigurationSection("discovery_sound");
    if (soundSection != null) {
      discoverySound = soundSection.getString("sound", "BLOCK_BEACON_POWER_SELECT");
      discoveryVolume = (float) soundSection.getDouble("volume", 1.0);
      discoveryPitch = (float) soundSection.getDouble("pitch", 1.0);
    } else {
      discoverySound = "BLOCK_BEACON_POWER_SELECT";
      discoveryVolume = 1.0f;
      discoveryPitch = 1.0f;
    }
  }

  private static GenerationMode parseGeneration(String raw) {
    if (raw == null) {
      return GenerationMode.SEEDED;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_")) {
      case "pure_random", "purerandom", "random" -> GenerationMode.PURE_RANDOM;
      default -> GenerationMode.SEEDED;
    };
  }

  public enum GenerationMode {SEEDED, PURE_RANDOM}
}
