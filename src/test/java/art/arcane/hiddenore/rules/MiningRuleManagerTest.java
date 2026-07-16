package art.arcane.hiddenore.rules;

import art.arcane.hiddenore.util.project.ToolTier;
import art.arcane.hiddenore.vein.VeinConfig;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class MiningRuleManagerTest {
  @Test
  public void constructor_parsesOrderedImmutableSnapshotAndPartitionsRules() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("blocks.stone.drop", "cobblestone");
    config.set("veins.generation", "pure_random");

    Map<String, Object> itemEntry = validItemRule();
    List<String> commands = new ArrayList<>(List.of("say one", "say two"));
    Map<String, Object> commandEntry = validCommandRule(commands, 1.0, -5, 5);
    List<Map<String, Object>> configuredRules = new ArrayList<>(List.of(itemEntry, commandEntry));
    config.set("drops", configuredRules);

    MiningRuleManager manager = manager(config);
    itemEntry.put("item", "emerald");
    commands.add("say three");
    configuredRules.clear();

    assertEquals(Material.COBBLESTONE, manager.getGuaranteedDrop(Material.STONE));
    assertEquals(1, manager.getItemRules(-64).size());
    assertEquals(1, manager.getItemRules(16).size());
    assertEquals(0, manager.getItemRules(17).size());
    assertEquals(1, manager.getCommandRules(-5).size());
    assertEquals(1, manager.getCommandRules(5).size());
    assertEquals(0, manager.getCommandRules(6).size());

    List<ItemDropRule> allRules = manager.getAllDropRules();
    assertEquals(2, allRules.size());
    assertEquals(ItemDropRule.DropType.ITEM, allRules.get(0).type);
    assertEquals(ItemDropRule.DropType.COMMAND, allRules.get(1).type);
    assertEquals(Material.DIAMOND, allRules.get(0).material);
    assertEquals(List.of("say one", "say two"), allRules.get(1).commands);
    assertEquals(ItemDropRule.ExecutionTarget.PLAYER, allRules.get(1).executionTarget);
    assertEquals(ToolTier.IRON_PICKAXE, allRules.get(0).toolTiers.iterator().next());
    assertEquals(VeinConfig.GenerationMode.PURE_RANDOM, manager.getVeinConfig().generation);

    assertThrows(UnsupportedOperationException.class, () -> manager.getItemRules(0).add(allRules.get(0)));
    assertThrows(UnsupportedOperationException.class, () -> manager.getCommandRules(0).clear());
    assertThrows(UnsupportedOperationException.class, allRules::clear);
    assertThrows(UnsupportedOperationException.class, () -> allRules.get(0).toolTiers.add(ToolTier.DIAMOND_PICKAXE));
    assertThrows(UnsupportedOperationException.class, () -> allRules.get(1).commands.add("say changed"));
    assertSame(allRules.get(0), manager.getItemRules(0).get(0));
  }

  @Test
  public void constructor_acceptsInclusiveNumericBoundaries() {
    YamlConfiguration config = validConfig();
    Map<String, Object> itemEntry = validItemRule();
    itemEntry.put("veins_per_chunk", 0.0);
    itemEntry.put("vein_min_size", 1);
    itemEntry.put("vein_max_size", 1);
    itemEntry.put("min_y", 4);
    itemEntry.put("max_y", 4);
    itemEntry.put("exp_drop", MiningRuleManager.MAX_EXP_DROP);
    Map<String, Object> zeroChanceCommand = validCommandRule(List.of("say zero"), 0.0, 4, 4);
    Map<String, Object> fullChanceCommand = validCommandRule(List.of("say full"), 1.0, 4, 4);
    config.set("drops", List.of(itemEntry, zeroChanceCommand, fullChanceCommand));

    MiningRuleManager manager = manager(config);

    assertEquals(1, manager.getItemRules(4).size());
    assertEquals(MiningRuleManager.MAX_EXP_DROP, manager.getItemRules(4).get(0).expDrop);
    assertEquals(2, manager.getCommandRules(4).size());
    assertEquals(0.0, manager.getCommandRules(4).get(0).chance, 0.0);
    assertEquals(1.0, manager.getCommandRules(4).get(1).chance, 0.0);
  }

  @Test
  public void constructor_rejectsMissingOrMalformedCoreSections() {
    YamlConfiguration missingBlocks = new YamlConfiguration();
    assertInvalid("blocks: expected a non-empty configuration section; define at least one entry, for example blocks: {stone: {drop: cobblestone}}", missingBlocks);

    YamlConfiguration emptyBlocks = new YamlConfiguration();
    emptyBlocks.createSection("blocks");
    assertInvalid("blocks: expected a non-empty configuration section; define at least one entry, for example blocks: {stone: {drop: cobblestone}}", emptyBlocks);

    YamlConfiguration missingDrops = validConfig();
    missingDrops.set("drops", null);
    assertInvalid("drops: expected a non-empty list", missingDrops);

    YamlConfiguration scalarDrops = validConfig();
    scalarDrops.set("drops", "diamond");
    assertInvalid("drops: expected a non-empty list", scalarDrops);

    YamlConfiguration emptyDrops = validConfig();
    emptyDrops.set("drops", List.of());
    assertInvalid("drops: expected a non-empty list", emptyDrops);

    YamlConfiguration missingVeins = validConfig();
    missingVeins.set("veins", null);
    assertInvalid("veins: expected a configuration section", missingVeins);

    YamlConfiguration scalarVeins = validConfig();
    scalarVeins.set("veins", "seeded");
    assertInvalid("veins: expected a configuration section", scalarVeins);
  }

  @Test
  public void constructor_acceptsInclusiveGenerationResourceLimits() {
    YamlConfiguration maximumVeins = validConfig();
    Map<String, Object> maximumVeinsRule = validItemRule();
    maximumVeinsRule.put("veins_per_chunk", MiningRuleManager.MAX_VEINS_PER_CHUNK);
    maximumVeinsRule.put("vein_min_size", 16);
    maximumVeinsRule.put("vein_max_size", 16);
    maximumVeins.set("drops", List.of(maximumVeinsRule));
    assertEquals(MiningRuleManager.MAX_VEINS_PER_CHUNK, manager(maximumVeins).getAllDropRules().get(0).veinsPerChunk, 0.0);

    YamlConfiguration maximumSize = validConfig();
    Map<String, Object> maximumSizeRule = validItemRule();
    maximumSizeRule.put("veins_per_chunk", 4.0);
    maximumSizeRule.put("vein_min_size", MiningRuleManager.MAX_VEIN_SIZE);
    maximumSizeRule.put("vein_max_size", MiningRuleManager.MAX_VEIN_SIZE);
    maximumSize.set("drops", List.of(maximumSizeRule));
    assertEquals(MiningRuleManager.MAX_VEIN_SIZE, manager(maximumSize).getAllDropRules().get(0).veinMaxSize);

    YamlConfiguration maximumCombinedWork = validConfig();
    Map<String, Object> firstRule = validItemRule();
    firstRule.put("veins_per_chunk", 2.0);
    firstRule.put("vein_min_size", MiningRuleManager.MAX_VEIN_SIZE);
    firstRule.put("vein_max_size", MiningRuleManager.MAX_VEIN_SIZE);
    Map<String, Object> secondRule = new LinkedHashMap<>(firstRule);
    maximumCombinedWork.set("drops", List.of(firstRule, secondRule));
    assertEquals(2, manager(maximumCombinedWork).getAllDropRules().size());

    YamlConfiguration fullProbability = validConfig();
    Map<String, Object> fullProbabilityRule = validItemRule();
    fullProbabilityRule.put("veins_per_chunk", 1.0);
    fullProbabilityRule.put("vein_min_size", MiningRuleManager.MAX_VEIN_SIZE);
    fullProbabilityRule.put("vein_max_size", MiningRuleManager.MAX_VEIN_SIZE);
    fullProbabilityRule.put("min_y", 0);
    fullProbabilityRule.put("max_y", 0);
    fullProbability.set("drops", List.of(fullProbabilityRule));
    assertEquals(1.0, manager(fullProbability).getAllDropRules().get(0).pureRandomChance(), 0.0);
  }

  @Test
  public void constructor_rejectsGenerationResourceLimitsAndImpossibleProbability() {
    Map<String, Object> excessiveVeins = validItemRule();
    excessiveVeins.put("veins_per_chunk", MiningRuleManager.MAX_VEINS_PER_CHUNK + 0.01);
    assertInvalid("drops[0].veins_per_chunk: must be less than or equal to " + MiningRuleManager.MAX_VEINS_PER_CHUNK,
        configWithRule(excessiveVeins));

    Map<String, Object> excessiveMinimumSize = validItemRule();
    excessiveMinimumSize.put("vein_min_size", MiningRuleManager.MAX_VEIN_SIZE + 1);
    excessiveMinimumSize.put("vein_max_size", MiningRuleManager.MAX_VEIN_SIZE + 1);
    assertInvalid("drops[0].vein_min_size: must be less than or equal to " + MiningRuleManager.MAX_VEIN_SIZE,
        configWithRule(excessiveMinimumSize));

    Map<String, Object> excessiveMaximumSize = validItemRule();
    excessiveMaximumSize.put("vein_max_size", MiningRuleManager.MAX_VEIN_SIZE + 1);
    assertInvalid("drops[0].vein_max_size: must be less than or equal to " + MiningRuleManager.MAX_VEIN_SIZE,
        configWithRule(excessiveMaximumSize));

    YamlConfiguration excessiveCombinedWork = validConfig();
    Map<String, Object> firstRule = validItemRule();
    firstRule.put("veins_per_chunk", 2.0);
    firstRule.put("vein_min_size", MiningRuleManager.MAX_VEIN_SIZE);
    firstRule.put("vein_max_size", MiningRuleManager.MAX_VEIN_SIZE);
    Map<String, Object> secondRule = new LinkedHashMap<>(firstRule);
    secondRule.put("veins_per_chunk", 3.0);
    excessiveCombinedWork.set("drops", List.of(firstRule, secondRule));
    assertInvalid("drops[1]: combined worst-case generation work must be less than or equal to "
        + MiningRuleManager.MAX_GENERATION_BLOCK_TARGETS_PER_CHUNK + " target blocks per chunk", excessiveCombinedWork);

    Map<String, Object> impossibleProbability = validItemRule();
    impossibleProbability.put("veins_per_chunk", 2.0);
    impossibleProbability.put("vein_min_size", MiningRuleManager.MAX_VEIN_SIZE);
    impossibleProbability.put("vein_max_size", MiningRuleManager.MAX_VEIN_SIZE);
    impossibleProbability.put("min_y", 0);
    impossibleProbability.put("max_y", 0);
    assertInvalid("drops[0].veins_per_chunk: derived pure_random probability must be less than or equal to 1",
        configWithRule(impossibleProbability));
  }

  @Test
  public void constructor_rejectsMissingOrNonSectionBlockEntries() {
    YamlConfiguration scalarBlocks = new YamlConfiguration();
    scalarBlocks.set("blocks", "stone");
    assertInvalid("blocks: expected a non-empty configuration section; define at least one entry, for example blocks: {stone: {drop: cobblestone}}", scalarBlocks);

    YamlConfiguration scalarBlockEntry = new YamlConfiguration();
    scalarBlockEntry.set("blocks.stone", "cobblestone");
    assertInvalid("blocks.stone: expected a configuration section", scalarBlockEntry);

    YamlConfiguration unknownBlock = new YamlConfiguration();
    unknownBlock.set("blocks.not_a_block.drop", "cobblestone");
    assertInvalid("blocks.not_a_block: unknown material 'not_a_block'", unknownBlock);
  }

  @Test
  public void constructor_rejectsMissingOrInvalidBlockDrops() {
    YamlConfiguration missingDrop = new YamlConfiguration();
    missingDrop.createSection("blocks").createSection("stone");
    assertInvalid("blocks.stone.drop: expected a non-empty material name", missingDrop);

    YamlConfiguration invalidDrop = new YamlConfiguration();
    invalidDrop.set("blocks.stone.drop", "not_an_item");
    assertInvalid("blocks.stone.drop: unknown material 'not_an_item'", invalidDrop);

    YamlConfiguration nonBlockBase = new YamlConfiguration();
    nonBlockBase.set("blocks.diamond.drop", "diamond");
    assertInvalid("blocks.diamond: expected a non-air block material", nonBlockBase);

    YamlConfiguration airDrop = new YamlConfiguration();
    airDrop.set("blocks.stone.drop", "air");
    assertInvalid("blocks.stone.drop: expected a non-air item material", airDrop);
  }

  @Test
  public void constructor_rejectsMissingOrInvalidItem() {
    Map<String, Object> missingItem = validItemRule();
    missingItem.remove("item");
    assertInvalid("drops[0].item: expected a non-empty item material name", configWithRule(missingItem));

    Map<String, Object> invalidItem = validItemRule();
    invalidItem.put("item", "not_an_item");
    assertInvalid("drops[0].item: unknown material 'not_an_item'", configWithRule(invalidItem));

    Map<String, Object> airItem = validItemRule();
    airItem.put("item", "air");
    assertInvalid("drops[0].item: expected a non-air item material", configWithRule(airItem));
  }

  @Test
  public void constructor_rejectsInvalidVeinsPerChunk() {
    Map<String, Object> nonNumeric = validItemRule();
    nonNumeric.put("veins_per_chunk", "many");
    assertInvalid("drops[0].veins_per_chunk: expected a finite number", configWithRule(nonNumeric));

    Map<String, Object> nonFinite = validItemRule();
    nonFinite.put("veins_per_chunk", Double.NaN);
    assertInvalid("drops[0].veins_per_chunk: expected a finite number", configWithRule(nonFinite));

    Map<String, Object> negative = validItemRule();
    negative.put("veins_per_chunk", -0.1);
    assertInvalid("drops[0].veins_per_chunk: must be greater than or equal to 0", configWithRule(negative));
  }

  @Test
  public void constructor_rejectsInvalidSizesAndRanges() {
    Map<String, Object> fractionalSize = validItemRule();
    fractionalSize.put("vein_min_size", 1.5);
    assertInvalid("drops[0].vein_min_size: expected an integer", configWithRule(fractionalSize));

    Map<String, Object> zeroSize = validItemRule();
    zeroSize.put("vein_min_size", 0);
    assertInvalid("drops[0].vein_min_size: must be greater than or equal to 1", configWithRule(zeroSize));

    Map<String, Object> reversedSizes = validItemRule();
    reversedSizes.put("vein_min_size", 4);
    reversedSizes.put("vein_max_size", 3);
    assertInvalid("drops[0].vein_max_size: must be greater than or equal to drops[0].vein_min_size", configWithRule(reversedSizes));

    Map<String, Object> reversedRange = validItemRule();
    reversedRange.put("min_y", 10);
    reversedRange.put("max_y", 9);
    assertInvalid("drops[0].min_y: must be less than or equal to drops[0].max_y", configWithRule(reversedRange));
  }

  @Test
  public void constructor_rejectsMalformedOrEmptyToolTierLists() {
    Map<String, Object> missingTiers = validItemRule();
    missingTiers.remove("tool_tiers");
    assertInvalid("drops[0].tool_tiers: expected a non-empty list", configWithRule(missingTiers));

    Map<String, Object> scalarTiers = validItemRule();
    scalarTiers.put("tool_tiers", "IRON_PICKAXE");
    assertInvalid("drops[0].tool_tiers: expected a non-empty list", configWithRule(scalarTiers));

    Map<String, Object> emptyTiers = validItemRule();
    emptyTiers.put("tool_tiers", List.of());
    assertInvalid("drops[0].tool_tiers: expected a non-empty list", configWithRule(emptyTiers));

    Map<String, Object> malformedTier = validItemRule();
    malformedTier.put("tool_tiers", List.of(1));
    assertInvalid("drops[0].tool_tiers[0]: expected a tool tier name", configWithRule(malformedTier));
  }

  @Test
  public void constructor_rejectsInvalidToolTier() {
    Map<String, Object> invalidTier = validItemRule();
    invalidTier.put("tool_tiers", List.of("MYTHRIL_PICKAXE"));
    assertInvalid("drops[0].tool_tiers[0]: unknown tool tier 'MYTHRIL_PICKAXE'", configWithRule(invalidTier));
  }

  @Test
  public void constructor_rejectsNegativeExperience() {
    Map<String, Object> negativeExperience = validItemRule();
    negativeExperience.put("exp_drop", -1);
    assertInvalid("drops[0].exp_drop: must be greater than or equal to 0", configWithRule(negativeExperience));

    Map<String, Object> excessiveExperience = validItemRule();
    excessiveExperience.put("exp_drop", MiningRuleManager.MAX_EXP_DROP + 1);
    assertInvalid("drops[0].exp_drop: must be less than or equal to " + MiningRuleManager.MAX_EXP_DROP,
        configWithRule(excessiveExperience));
  }

  @Test
  public void constructor_rejectsInvalidFortuneAndExecutionTargets() {
    Map<String, Object> invalidFortune = validItemRule();
    invalidFortune.put("fortune_multiplier", "yes");
    assertInvalid("drops[0].fortune_multiplier: expected true or false", configWithRule(invalidFortune));

    Map<String, Object> invalidTarget = validCommandRule(List.of("say hi"), 0.5, -64, 320);
    invalidTarget.put("execute_as", "operator");
    assertInvalid("drops[0].execute_as: expected 'player' or 'console'", configWithRule(invalidTarget));

    Map<String, Object> invalidLegacyTarget = validCommandRule(List.of("say hi"), 0.5, -64, 320);
    invalidLegacyTarget.remove("execute_as");
    invalidLegacyTarget.put("as_player", "yes");
    assertInvalid("drops[0].as_player: unsupported; use 'execute_as'", configWithRule(invalidLegacyTarget));

    Map<String, Object> prefixedPlayerTarget = validCommandRule(List.of("say hi"), 0.5, -64, 320);
    prefixedPlayerTarget.put("execute_as", "as_player");
    assertInvalid("drops[0].execute_as: expected 'player' or 'console'", configWithRule(prefixedPlayerTarget));

    Map<String, Object> prefixedConsoleTarget = validCommandRule(List.of("say hi"), 0.5, -64, 320);
    prefixedConsoleTarget.put("execute_as", "as_console");
    assertInvalid("drops[0].execute_as: expected 'player' or 'console'", configWithRule(prefixedConsoleTarget));

    Map<String, Object> legacyConsoleKey = validCommandRule(List.of("say hi"), 0.5, -64, 320);
    legacyConsoleKey.put("as_console", true);
    assertInvalid("drops[0].as_console: unsupported; use 'execute_as'", configWithRule(legacyConsoleKey));
  }

  @Test
  public void constructor_rejectsInvalidCommandChance() {
    assertInvalidCommandChance("often", "drops[0].chance: expected a finite number");
    assertInvalidCommandChance(Double.POSITIVE_INFINITY, "drops[0].chance: expected a finite number");
    assertInvalidCommandChance(-0.1, "drops[0].chance: must be between 0 and 1 inclusive");
    assertInvalidCommandChance(1.1, "drops[0].chance: must be between 0 and 1 inclusive");
  }

  @Test
  public void constructor_rejectsEmptyOrMalformedCommandLists() {
    Map<String, Object> missingCommands = validCommandRule(List.of("say hi"), 0.5, -64, 320);
    missingCommands.remove("commands");
    assertInvalid("drops[0].commands: must contain at least one command", configWithRule(missingCommands));

    Map<String, Object> emptyCommands = validCommandRule(List.of(), 0.5, -64, 320);
    assertInvalid("drops[0].commands: must contain at least one command", configWithRule(emptyCommands));

    Map<String, Object> malformedCommands = validCommandRule(List.of(1), 0.5, -64, 320);
    assertInvalid("drops[0].commands[0]: expected a non-empty command", configWithRule(malformedCommands));

    Map<String, Object> singularCommand = validCommandRule(List.of("say hi"), 0.5, -64, 320);
    singularCommand.remove("commands");
    singularCommand.put("command", "say legacy");
    assertInvalid("drops[0].command: unsupported; use 'commands'", configWithRule(singularCommand));
  }

  private static Map<String, Object> validItemRule() {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("item", "diamond");
    entry.put("veins_per_chunk", 0.5);
    entry.put("vein_min_size", 3);
    entry.put("vein_max_size", 8);
    entry.put("min_y", -64);
    entry.put("max_y", 16);
    entry.put("fortune_multiplier", true);
    entry.put("tool_tiers", List.of("IRON_PICKAXE"));
    entry.put("exp_drop", 7);
    return entry;
  }

  private static Map<String, Object> validCommandRule(List<?> commands, double chance, int minY, int maxY) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("type", "command");
    entry.put("commands", commands);
    entry.put("chance", chance);
    entry.put("min_y", minY);
    entry.put("max_y", maxY);
    entry.put("execute_as", "player");
    return entry;
  }

  private static YamlConfiguration configWithRule(Map<String, Object> entry) {
    YamlConfiguration config = validConfig();
    config.set("drops", List.of(entry));
    return config;
  }

  private static YamlConfiguration validConfig() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("blocks.stone.drop", "cobblestone");
    config.createSection("veins");
    config.set("drops", List.of(validItemRule()));
    return config;
  }

  private static void assertInvalidCommandChance(Object chance, String expectedMessage) {
    Map<String, Object> command = validCommandRule(List.of("say hi"), 0.5, -64, 320);
    command.put("chance", chance);
    assertInvalid(expectedMessage, configWithRule(command));
  }

  private static void assertInvalid(String expectedMessage, YamlConfiguration config) {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> manager(config));
    assertEquals(expectedMessage, exception.getMessage());
  }

  private static MiningRuleManager manager(YamlConfiguration config) {
    return new MiningRuleManager(config);
  }
}
