package art.arcane.hiddenore.vein;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VeinConfigTest {
  @Test
  public void defaults_nullSection_seededAndPlacedAllowed() {
    VeinConfig config = new VeinConfig(null);
    assertEquals(VeinConfig.GenerationMode.SEEDED, config.generation);
    assertTrue(config.allowPlacedBlocks);
  }

  @Test
  public void defaults_emptySection_seededAndPlacedAllowed() throws Exception {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.loadFromString("veins:\n  discovery_sound:\n    sound: \"BLOCK_BEACON_POWER_SELECT\"\n");
    VeinConfig config = new VeinConfig(yaml.getConfigurationSection("veins"));
    assertEquals(VeinConfig.GenerationMode.SEEDED, config.generation);
    assertTrue(config.allowPlacedBlocks);
  }

  @Test
  public void pureRandom_parsed() throws Exception {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.loadFromString("veins:\n  generation: pure_random\n  allow_placed_blocks: false\n");
    VeinConfig config = new VeinConfig(yaml.getConfigurationSection("veins"));
    assertEquals(VeinConfig.GenerationMode.PURE_RANDOM, config.generation);
    assertFalse(config.allowPlacedBlocks);
  }

  @Test
  public void generation_caseAndAliasTolerant() throws Exception {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.loadFromString("veins:\n  generation: PureRandom\n");
    VeinConfig config = new VeinConfig(yaml.getConfigurationSection("veins"));
    assertEquals(VeinConfig.GenerationMode.PURE_RANDOM, config.generation);
  }

  @Test
  public void generation_unknownValue_fallsBackToSeeded() throws Exception {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.loadFromString("veins:\n  generation: banana\n");
    VeinConfig config = new VeinConfig(yaml.getConfigurationSection("veins"));
    assertEquals(VeinConfig.GenerationMode.SEEDED, config.generation);
  }
}
