package art.arcane.hiddenore.generation;

import art.arcane.volmlib.util.config.TomlCodec;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationRulesTest {
  @Test
  public void parsePolicy_missingSectionIsDisabled() {
    GenerationRules.GenerationPolicy policy = GenerationRules.parsePolicy(new JsonObject());

    assertFalse(policy.enabled());
    assertTrue(policy.defaults().isEmpty());
    assertTrue(policy.worldExceptions().isEmpty());
  }

  @Test
  public void parsePolicy_buildsImmutableGlobalAndWorldPolicies() throws Exception {
    JsonObject config = TomlCodec.toJsonElement("""
        [ore-removal]
        enabled = true
        [ore-removal.global]
        default = false
        DIAMOND_ORE = true
        [ore-removal.exceptions."minecraft:overworld"]
        default = true
        DIAMOND_ORE = false
        [ore-removal.exceptions."custom:mining.v2"]
        COAL_ORE = true
        """).getAsJsonObject();

    GenerationRules.GenerationPolicy policy = GenerationRules.parsePolicy(config);

    assertTrue(policy.enabled());
    assertEquals(Material.STONE, policy.defaults().get(Material.DIAMOND_ORE));
    assertFalse(policy.worldExceptions().get("minecraft:overworld").containsKey(Material.DIAMOND_ORE));
    assertEquals(Material.STONE, policy.worldExceptions().get("minecraft:overworld").get(Material.COAL_ORE));
    assertEquals(Map.of(Material.COAL_ORE, Material.STONE), policy.worldExceptions().get("custom:mining.v2"));
    assertThrows(UnsupportedOperationException.class, () -> policy.defaults().put(Material.COAL_ORE, Material.STONE));
    assertThrows(UnsupportedOperationException.class, () -> policy.worldExceptions().put("other", Map.of()));
  }

  @Test
  public void parsePolicy_rejectsMalformedSectionsAndBooleans() throws Exception {
    assertInvalid("ore-removal: expected a table", "ore-removal = 'enabled'");
    assertInvalid("ore-removal.enabled: expected true or false", "[ore-removal]\nenabled = 'true'");
    assertInvalid("ore-removal.enabled: expected true or false", "[ore-removal]\nenabled = 1");
    assertInvalid("ore-removal.global: expected a table", "[ore-removal]\nglobal = true");
    assertInvalid("ore-removal.exceptions: expected a table", "[ore-removal]\nexceptions = true");
    assertInvalid("ore-removal.exceptions.world: expected a table", "[ore-removal.exceptions]\nworld = true");
    assertInvalid("ore-removal.exceptions.world: World identity must be a fully qualified namespaced key: world",
        "[ore-removal.exceptions.world]\ndefault = true");
  }

  @Test
  public void parsePolicy_rejectsUnknownOresAndWronglyTypedOverrides() throws Exception {
    assertInvalid("ore-removal.global.NOT_AN_ORE: unknown ore material 'NOT_AN_ORE'",
        "[ore-removal.global]\nNOT_AN_ORE = true");
    assertInvalid("ore-removal.global.DIAMOND_ORE: expected true or false",
        "[ore-removal.global]\nDIAMOND_ORE = 'true'");
    assertInvalid("ore-removal.global.DIAMOND_ORE.extra: unknown ore material 'DIAMOND_ORE.extra'",
        "[ore-removal.global]\n\"DIAMOND_ORE.extra\" = true");
  }

  @Test
  public void regionBounds_deriveFromPopulatedChunkWithoutPaperAccessors() {
    GenerationRules.RegionBounds bounds = GenerationRules.RegionBounds.of(5, -3, 16);

    assertEquals(1, bounds.bufferChunks());
    assertEquals(64, bounds.xMin());
    assertEquals(-64, bounds.zMin());
    assertEquals(112, bounds.xMax());
    assertEquals(-16, bounds.zMax());
  }

  @Test
  public void regionBounds_zeroBufferCoversOnlyPopulatedChunk() {
    GenerationRules.RegionBounds bounds = GenerationRules.RegionBounds.of(0, 0, 0);

    assertEquals(0, bounds.bufferChunks());
    assertEquals(0, bounds.xMin());
    assertEquals(0, bounds.zMin());
    assertEquals(16, bounds.xMax());
    assertEquals(16, bounds.zMax());
  }

  private static void assertInvalid(String message, String toml) throws Exception {
    JsonObject config = TomlCodec.toJsonElement(toml).getAsJsonObject();
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> GenerationRules.parsePolicy(config));
    assertEquals(message, exception.getMessage());
  }
}
