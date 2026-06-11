package art.arcane.hiddenore.vein;

import org.bukkit.configuration.ConfigurationSection;

public class VeinConfig {
  public final String discoverySound;
  public final float discoveryVolume;
  public final float discoveryPitch;

  public VeinConfig(ConfigurationSection section) {
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
}
