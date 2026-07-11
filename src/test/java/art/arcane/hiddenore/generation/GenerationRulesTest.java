package art.arcane.hiddenore.generation;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationRulesTest {
  @Test
  public void parsePolicy_missingSectionIsDisabled() {
    GenerationRules.GenerationPolicy policy = GenerationRules.parsePolicy(new YamlConfiguration());

    assertFalse(policy.enabled());
    assertTrue(policy.defaults().isEmpty());
    assertTrue(policy.worldExceptions().isEmpty());
  }

  @Test
  public void parsePolicy_buildsImmutableGlobalAndWorldPolicies() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("ore-removal.enabled", true);
    config.set("ore-removal.global.default", false);
    config.set("ore-removal.global.DIAMOND_ORE", true);
    config.set("ore-removal.exceptions.world.default", true);
    config.set("ore-removal.exceptions.world.DIAMOND_ORE", false);

    GenerationRules.GenerationPolicy policy = GenerationRules.parsePolicy(config);

    assertTrue(policy.enabled());
    assertEquals(Material.STONE, policy.defaults().get(Material.DIAMOND_ORE));
    assertFalse(policy.worldExceptions().get("world").containsKey(Material.DIAMOND_ORE));
    assertEquals(Material.STONE, policy.worldExceptions().get("world").get(Material.COAL_ORE));
    assertThrows(UnsupportedOperationException.class, () -> policy.defaults().put(Material.COAL_ORE, Material.STONE));
    assertThrows(UnsupportedOperationException.class, () -> policy.worldExceptions().put("other", Map.of()));
  }

  @Test
  public void parsePolicy_rejectsMalformedSectionsAndBooleans() {
    assertInvalid("ore-removal: expected a configuration section", config("ore-removal", "enabled"));
    assertInvalid("ore-removal.enabled: expected true or false", config("ore-removal.enabled", "yes"));
    assertInvalid("ore-removal.global: expected a configuration section", config("ore-removal.global", true));
    assertInvalid("ore-removal.exceptions: expected a configuration section", config("ore-removal.exceptions", true));
    assertInvalid("ore-removal.exceptions.world: expected a configuration section", config("ore-removal.exceptions.world", true));
  }

  @Test
  public void parsePolicy_rejectsUnknownOresAndWronglyTypedOverrides() {
    assertInvalid("ore-removal.global.NOT_AN_ORE: unknown ore material 'NOT_AN_ORE'",
        config("ore-removal.global.NOT_AN_ORE", true));
    assertInvalid("ore-removal.global.DIAMOND_ORE: expected true or false",
        config("ore-removal.global.DIAMOND_ORE", "yes"));
  }

  private static YamlConfiguration config(String path, Object value) {
    YamlConfiguration config = new YamlConfiguration();
    config.set(path, value);
    return config;
  }

  private static void assertInvalid(String message, YamlConfiguration config) {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> GenerationRules.parsePolicy(config));
    assertEquals(message, exception.getMessage());
  }
}
