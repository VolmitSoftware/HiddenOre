package art.arcane.hiddenore.vein;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VeinConfigTest {
  @Test
  public void constructor_rejectsMissingSection() {
    assertInvalid("veins: expected a configuration section", null);
  }

  @Test
  public void constructor_emptySection_usesSafeDefaults() {
    VeinConfig config = new VeinConfig(section());

    assertEquals(VeinConfig.GenerationMode.SEEDED, config.generation);
    assertFalse(config.allowPlacedBlocks);
    assertEquals("BLOCK_BEACON_POWER_SELECT", config.discoverySound);
    assertEquals(1.0f, config.discoveryVolume, 0.0f);
    assertEquals(1.0f, config.discoveryPitch, 0.0f);
  }

  @Test
  public void constructor_parsesCanonicalGenerationValuesAndBoolean() {
    ConfigurationSection pureRandom = section();
    pureRandom.set("generation", "pure_random");
    pureRandom.set("allow_placed_blocks", true);
    VeinConfig pureRandomConfig = new VeinConfig(pureRandom);
    assertEquals(VeinConfig.GenerationMode.PURE_RANDOM, pureRandomConfig.generation);
    assertTrue(pureRandomConfig.allowPlacedBlocks);

    ConfigurationSection seeded = section();
    seeded.set("generation", "seeded");
    assertEquals(VeinConfig.GenerationMode.SEEDED, new VeinConfig(seeded).generation);
  }

  @Test
  public void constructor_rejectsUnknownOrWronglyTypedGeneration() {
    ConfigurationSection unknown = section();
    unknown.set("generation", "banana");
    assertInvalid("veins.generation: unknown generation mode 'banana'", unknown);

    ConfigurationSection legacyAlias = section();
    legacyAlias.set("generation", "PureRandom");
    assertInvalid("veins.generation: unknown generation mode 'PureRandom'", legacyAlias);

    ConfigurationSection randomAlias = section();
    randomAlias.set("generation", "random");
    assertInvalid("veins.generation: unknown generation mode 'random'", randomAlias);

    ConfigurationSection uppercaseAlias = section();
    uppercaseAlias.set("generation", "PURE_RANDOM");
    assertInvalid("veins.generation: unknown generation mode 'PURE_RANDOM'", uppercaseAlias);

    ConfigurationSection wrongType = section();
    wrongType.set("generation", 1);
    assertInvalid("veins.generation: expected 'seeded' or 'pure_random'", wrongType);
  }

  @Test
  public void constructor_rejectsWronglyTypedAllowPlacedBlocks() {
    ConfigurationSection section = section();
    section.set("allow_placed_blocks", "false");
    assertInvalid("veins.allow_placed_blocks: expected true or false", section);
  }

  @Test
  public void constructor_rejectsMalformedDiscoverySoundSectionAndName() {
    ConfigurationSection scalar = section();
    scalar.set("discovery_sound", "BLOCK_NOTE_BLOCK_PLING");
    assertInvalid("veins.discovery_sound: expected a configuration section", scalar);

    ConfigurationSection blankName = section();
    blankName.set("discovery_sound.sound", " ");
    assertInvalid("veins.discovery_sound.sound: expected a non-empty sound name", blankName);

    ConfigurationSection wrongType = section();
    wrongType.set("discovery_sound.sound", 1);
    assertInvalid("veins.discovery_sound.sound: expected a non-empty sound name", wrongType);
  }

  @Test
  public void constructor_acceptsInclusiveDiscoverySoundBoundaries() {
    ConfigurationSection minimum = section();
    minimum.set("discovery_sound.sound", " BLOCK_NOTE_BLOCK_PLING ");
    minimum.set("discovery_sound.volume", 0.0);
    minimum.set("discovery_sound.pitch", 0.5);
    VeinConfig minimumConfig = new VeinConfig(minimum);
    assertEquals("BLOCK_NOTE_BLOCK_PLING", minimumConfig.discoverySound);
    assertEquals(0.0f, minimumConfig.discoveryVolume, 0.0f);
    assertEquals(0.5f, minimumConfig.discoveryPitch, 0.0f);

    ConfigurationSection maximum = section();
    maximum.set("discovery_sound.volume", Float.MAX_VALUE);
    maximum.set("discovery_sound.pitch", 2.0);
    VeinConfig maximumConfig = new VeinConfig(maximum);
    assertEquals(Float.MAX_VALUE, maximumConfig.discoveryVolume, 0.0f);
    assertEquals(2.0f, maximumConfig.discoveryPitch, 0.0f);
  }

  @Test
  public void constructor_rejectsInvalidDiscoveryVolume() {
    assertInvalidSoundNumber("volume", "loud", volumeMessage());
    assertInvalidSoundNumber("volume", -0.01, volumeMessage());
    assertInvalidSoundNumber("volume", Double.NaN, volumeMessage());
    assertInvalidSoundNumber("volume", Double.POSITIVE_INFINITY, volumeMessage());
    assertInvalidSoundNumber("volume", Double.MAX_VALUE, volumeMessage());
  }

  @Test
  public void constructor_rejectsInvalidDiscoveryPitch() {
    String message = "veins.discovery_sound.pitch: must be a finite number between 0.5 and 2 inclusive";
    assertInvalidSoundNumber("pitch", "high", message);
    assertInvalidSoundNumber("pitch", 0.499, message);
    assertInvalidSoundNumber("pitch", 2.001, message);
    assertInvalidSoundNumber("pitch", Double.NaN, message);
    assertInvalidSoundNumber("pitch", Double.NEGATIVE_INFINITY, message);
  }

  private static ConfigurationSection section() {
    YamlConfiguration yaml = new YamlConfiguration();
    return yaml.createSection("veins");
  }

  private static void assertInvalidSoundNumber(String key, Object value, String expectedMessage) {
    ConfigurationSection section = section();
    section.set("discovery_sound." + key, value);
    assertInvalid(expectedMessage, section);
  }

  private static String volumeMessage() {
    return "veins.discovery_sound.volume: must be a finite number greater than or equal to 0";
  }

  private static void assertInvalid(String expectedMessage, ConfigurationSection section) {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new VeinConfig(section));
    assertEquals(expectedMessage, exception.getMessage());
  }
}
