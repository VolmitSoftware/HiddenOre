package art.arcane.hiddenore.service;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.rules.MiningRuleManager;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderKeyRegistry;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;
import art.arcane.volmlib.util.bukkit.papi.VolmitPlaceholderExpansion;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.OfflinePlayer;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HiddenOrePlaceholderExpansionTest {
  private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000ab01");

  @Test
  public void seeded_reportsTheConfiguredGenerationMode() {
    PlaceholderKeyRegistry seededRegistry = HiddenOrePlaceholderExpansion.registry(() -> state("seeded", 1));
    PlaceholderKeyRegistry randomRegistry = HiddenOrePlaceholderExpansion.registry(() -> state("pure_random", 1));

    assertEquals(PlaceholderValues.TRUE, seededRegistry.resolve(PLAYER, "seeded"));
    assertEquals(PlaceholderValues.FALSE, randomRegistry.resolve(PLAYER, "seeded"));
  }

  @Test
  public void dropRules_countsEveryConfiguredRuleWithoutGroupingSeparators() {
    assertEquals("1", HiddenOrePlaceholderExpansion.registry(() -> state("seeded", 1)).resolve(PLAYER, "drop-rules"));
    assertEquals("4", HiddenOrePlaceholderExpansion.registry(() -> state("seeded", 4)).resolve(PLAYER, "drop-rules"));

    String manyRules = HiddenOrePlaceholderExpansion.registry(() -> state("seeded", 12)).resolve(PLAYER, "drop-rules");
    assertEquals("12", manyRules);
    assertFalse(manyRules.contains(","));
    assertFalse(manyRules.contains("%"));
  }

  @Test
  public void everyKeyAnswersUnavailableBeforeTheRuntimeIsPublished() {
    PlaceholderKeyRegistry registry = HiddenOrePlaceholderExpansion.registry(() -> null);

    assertEquals(PlaceholderValues.FALSE, registry.resolve(PLAYER, PlaceholderKeyRegistry.AVAILABLE));
    assertEquals(PlaceholderValues.UNAVAILABLE, registry.resolve(PLAYER, "seeded"));
    assertEquals(PlaceholderValues.UNAVAILABLE, registry.resolve(PLAYER, "drop-rules"));
  }

  @Test
  public void availableFlipsWithTheVolatileRuntimeRead() {
    AtomicReference<HiddenOre.RuntimeState> published = new AtomicReference<>();
    PlaceholderKeyRegistry registry = HiddenOrePlaceholderExpansion.registry(published::get);

    assertEquals(PlaceholderValues.FALSE, registry.resolve(PLAYER, PlaceholderKeyRegistry.AVAILABLE));

    published.set(state("seeded", 2));

    assertEquals(PlaceholderValues.TRUE, registry.resolve(PLAYER, PlaceholderKeyRegistry.AVAILABLE));
    assertEquals("2", registry.resolve(PLAYER, "drop-rules"));
  }

  @Test
  public void noVeinOrProvenanceOrPerPlayerSurfaceIsPublished() {
    PlaceholderKeyRegistry registry = HiddenOrePlaceholderExpansion.registry(() -> state("seeded", 1));

    assertNull(registry.resolve(PLAYER, "veins"));
    assertNull(registry.resolve(PLAYER, "vein.nearest"));
    assertNull(registry.resolve(PLAYER, "provenance.placed"));
    assertNull(registry.resolve(PLAYER, "player.debug"));
    assertNull(registry.resolve(PLAYER, "seede"));
  }

  @Test
  public void expansionPublishesExactlyThreeKeysAndPapiSafeMetadata() {
    VolmitPlaceholderExpansion expansion = new HiddenOrePlaceholderExpansion(() -> state("seeded", 3),
        Logger.getAnonymousLogger());

    assertEquals("hiddenore", expansion.getIdentifier());
    assertEquals("HiddenOre", expansion.getRequiredPlugin());
    assertTrue(expansion.persist());
    assertEquals(List.of("available", "drop-rules", "seeded"), expansion.getPlaceholders());
    assertEquals(-1, expansion.getIdentifier().indexOf('_'));

    assertEquals("3", expansion.onRequest(player(), "drop-rules"));
    assertEquals("3", expansion.onRequest(player(), "DROP-RULES"));
    assertNull(expansion.onRequest(player(), "drop_rules"));
  }

  @Test
  public void resolutionNeverTouchesThePlayerBeyondGetUniqueId() {
    VolmitPlaceholderExpansion expansion = new HiddenOrePlaceholderExpansion(() -> state("seeded", 3),
        Logger.getAnonymousLogger());

    assertEquals(PlaceholderValues.TRUE, expansion.onRequest(player(), "seeded"));
    assertEquals(PlaceholderValues.TRUE, expansion.onRequest(null, "seeded"));
  }

  @Test
  public void placeholderApiIsTheOnlyPluginThatTriggersRegistration() {
    assertTrue(HiddenOrePlaceholderService.isPlaceholderApi("PlaceholderAPI"));
    assertFalse(HiddenOrePlaceholderService.isPlaceholderApi("placeholderapi"));
    assertFalse(HiddenOrePlaceholderService.isPlaceholderApi("HiddenOre"));
  }

  private static HiddenOre.RuntimeState state(String generation, int dropRules) {
    Gson gson = new Gson();
    JsonObject config = gson.toJsonTree(Map.of(
        "blocks", Map.of("stone", Map.of("drop", "cobblestone")),
        "veins", Map.of("generation", generation)
    )).getAsJsonObject();

    List<Map<String, Object>> drops = new ArrayList<>(dropRules);
    for (int index = 0; index < dropRules; index++) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("type", "command");
      entry.put("commands", List.of("say rule " + index));
      entry.put("chance", 0.5);
      entry.put("min_y", -64);
      entry.put("max_y", 320);
      entry.put("execute_as", "console");
      drops.add(entry);
    }
    config.add("drops", gson.toJsonTree(drops));

    return new HiddenOre.RuntimeState(new MiningRuleManager(config), null, null, null, false, false, true);
  }

  private static OfflinePlayer player() {
    return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(),
        new Class<?>[]{OfflinePlayer.class}, (proxy, method, args) -> switch (method.getName()) {
          case "getUniqueId" -> PLAYER;
          case "hashCode" -> PLAYER.hashCode();
          case "equals" -> proxy == args[0];
          case "toString" -> "OfflinePlayer[" + PLAYER + "]";
          default -> throw new UnsupportedOperationException("placeholder resolution touched " + method.getName());
        });
  }
}
