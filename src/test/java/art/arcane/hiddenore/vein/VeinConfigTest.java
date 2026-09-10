package art.arcane.hiddenore.vein;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VeinConfigTest {
  private static final Gson JSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

  @Test
  public void constructor_rejectsMissingSection() {
    assertInvalid("veins: expected a table", null);
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
    JsonObject pureRandom = section();
    pureRandom.addProperty("generation", "pure_random");
    pureRandom.addProperty("allow_placed_blocks", true);
    VeinConfig pureRandomConfig = new VeinConfig(pureRandom);
    assertEquals(VeinConfig.GenerationMode.PURE_RANDOM, pureRandomConfig.generation);
    assertTrue(pureRandomConfig.allowPlacedBlocks);

    JsonObject seeded = section();
    seeded.addProperty("generation", "seeded");
    assertEquals(VeinConfig.GenerationMode.SEEDED, new VeinConfig(seeded).generation);
  }

  @Test
  public void constructor_rejectsUnknownOrWronglyTypedGeneration() {
    JsonObject unknown = section();
    unknown.addProperty("generation", "banana");
    assertInvalid("veins.generation: unknown generation mode 'banana'", unknown);

    JsonObject legacyAlias = section();
    legacyAlias.addProperty("generation", "PureRandom");
    assertInvalid("veins.generation: unknown generation mode 'PureRandom'", legacyAlias);

    JsonObject randomAlias = section();
    randomAlias.addProperty("generation", "random");
    assertInvalid("veins.generation: unknown generation mode 'random'", randomAlias);

    JsonObject uppercaseAlias = section();
    uppercaseAlias.addProperty("generation", "PURE_RANDOM");
    assertInvalid("veins.generation: unknown generation mode 'PURE_RANDOM'", uppercaseAlias);

    JsonObject wrongType = section();
    wrongType.addProperty("generation", 1);
    assertInvalid("veins.generation: expected 'seeded' or 'pure_random'", wrongType);
  }

  @Test
  public void constructor_rejectsWronglyTypedAllowPlacedBlocks() {
    JsonObject section = section();
    section.addProperty("allow_placed_blocks", "false");
    assertInvalid("veins.allow_placed_blocks: expected true or false", section);
  }

  @Test
  public void constructor_rejectsMalformedDiscoverySoundSectionAndName() {
    JsonObject scalar = section();
    scalar.addProperty("discovery_sound", "BLOCK_NOTE_BLOCK_PLING");
    assertInvalid("veins.discovery_sound: expected a table", scalar);

    JsonObject blankName = section();
    blankName.add("discovery_sound", JSON.toJsonTree(Map.of("sound", " ")));
    assertInvalid("veins.discovery_sound.sound: expected a non-empty sound name", blankName);

    JsonObject wrongType = section();
    wrongType.add("discovery_sound", JSON.toJsonTree(Map.of("sound", 1)));
    assertInvalid("veins.discovery_sound.sound: expected a non-empty sound name", wrongType);
  }

  @Test
  public void constructor_acceptsInclusiveDiscoverySoundBoundaries() {
    JsonObject minimum = section();
    minimum.add("discovery_sound", JSON.toJsonTree(Map.of(
        "sound", " BLOCK_NOTE_BLOCK_PLING ", "volume", 0.0, "pitch", 0.5)));
    VeinConfig minimumConfig = new VeinConfig(minimum);
    assertEquals("BLOCK_NOTE_BLOCK_PLING", minimumConfig.discoverySound);
    assertEquals(0.0f, minimumConfig.discoveryVolume, 0.0f);
    assertEquals(0.5f, minimumConfig.discoveryPitch, 0.0f);

    JsonObject maximum = section();
    maximum.add("discovery_sound", JSON.toJsonTree(Map.of("volume", Float.MAX_VALUE, "pitch", 2.0)));
    VeinConfig maximumConfig = new VeinConfig(maximum);
    assertEquals(Float.MAX_VALUE, maximumConfig.discoveryVolume, 0.0f);
    assertEquals(2.0f, maximumConfig.discoveryPitch, 0.0f);
  }

  @Test
  public void constructor_rejectsInvalidDiscoveryVolume() {
    assertInvalidSoundNumber("volume", "loud", volumeMessage());
    assertInvalidSoundNumber("volume", "1.0", volumeMessage());
    assertInvalidSoundNumber("volume", -0.01, volumeMessage());
    assertInvalidSoundNumber("volume", Double.NaN, volumeMessage());
    assertInvalidSoundNumber("volume", Double.POSITIVE_INFINITY, volumeMessage());
    assertInvalidSoundNumber("volume", Double.MAX_VALUE, volumeMessage());
  }

  @Test
  public void constructor_rejectsInvalidDiscoveryPitch() {
    String message = "veins.discovery_sound.pitch: must be a finite number between 0.5 and 2 inclusive";
    assertInvalidSoundNumber("pitch", "high", message);
    assertInvalidSoundNumber("pitch", "1.0", message);
    assertInvalidSoundNumber("pitch", 0.499, message);
    assertInvalidSoundNumber("pitch", 2.001, message);
    assertInvalidSoundNumber("pitch", Double.NaN, message);
    assertInvalidSoundNumber("pitch", Double.NEGATIVE_INFINITY, message);
  }

  private static JsonObject section() {
    return new JsonObject();
  }

  private static void assertInvalidSoundNumber(String key, Object value, String expectedMessage) {
    JsonObject section = section();
    section.add("discovery_sound", JSON.toJsonTree(Map.of(key, value)));
    assertInvalid(expectedMessage, section);
  }

  private static String volumeMessage() {
    return "veins.discovery_sound.volume: must be a finite number greater than or equal to 0";
  }

  private static void assertInvalid(String expectedMessage, JsonObject section) {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new VeinConfig(section));
    assertEquals(expectedMessage, exception.getMessage());
  }
}
