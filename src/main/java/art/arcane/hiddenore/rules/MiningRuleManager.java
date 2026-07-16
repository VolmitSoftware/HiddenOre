package art.arcane.hiddenore.rules;

import art.arcane.hiddenore.util.project.ToolTier;
import art.arcane.hiddenore.vein.VeinConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MiningRuleManager {
  private static final String REQUIRED_BLOCKS = "expected a non-empty configuration section; define at least one entry, for example blocks: {stone: {drop: cobblestone}}";
  static final double MAX_VEINS_PER_CHUNK = 64.0;
  static final int MAX_VEIN_SIZE = 256;
  static final long MAX_GENERATION_BLOCK_TARGETS_PER_CHUNK = 1024L;
  static final int MAX_EXP_DROP = 1000;

  private final Map<Material, Material> guaranteedDrops;
  private final List<ItemDropRule> itemRules;
  private final List<ItemDropRule> commandRules;
  private final List<ItemDropRule> dropRules;
  private final VeinConfig veinConfig;

  public MiningRuleManager(FileConfiguration config) {
    FileConfiguration activeConfig = Objects.requireNonNull(config, "config");
    Map<Material, Material> parsedGuaranteedDrops = parseGuaranteedDrops(activeConfig);
    VeinConfig parsedVeinConfig = parseVeinConfig(activeConfig);
    List<ItemDropRule> parsedItemRules = new ArrayList<>();
    List<ItemDropRule> parsedCommandRules = new ArrayList<>();
    List<ItemDropRule> parsedDropRules = parseDropRules(activeConfig, parsedItemRules, parsedCommandRules);

    guaranteedDrops = Map.copyOf(parsedGuaranteedDrops);
    itemRules = List.copyOf(parsedItemRules);
    commandRules = List.copyOf(parsedCommandRules);
    dropRules = List.copyOf(parsedDropRules);
    veinConfig = parsedVeinConfig;
  }

  public Material getGuaranteedDrop(Material blockType) {
    return guaranteedDrops.get(blockType);
  }

  public List<ItemDropRule> getCommandRules(int y) {
    return matchingRules(commandRules, y);
  }

  public List<ItemDropRule> getItemRules(int y) {
    return matchingRules(itemRules, y);
  }

  public List<ItemDropRule> getAllDropRules() {
    return dropRules;
  }

  public VeinConfig getVeinConfig() {
    return veinConfig;
  }

  private static Map<Material, Material> parseGuaranteedDrops(FileConfiguration config) {
    Object rawBlocks = config.get("blocks");
    if (rawBlocks == null) {
      throw invalid("blocks", REQUIRED_BLOCKS);
    }

    ConfigurationSection blocks = config.getConfigurationSection("blocks");
    if (blocks == null) {
      throw invalid("blocks", REQUIRED_BLOCKS);
    }
    if (blocks.getKeys(false).isEmpty()) {
      throw invalid("blocks", REQUIRED_BLOCKS);
    }

    Map<Material, Material> parsedDrops = new LinkedHashMap<>();
    for (String key : blocks.getKeys(false)) {
      String blockPath = "blocks." + key;
      ConfigurationSection blockEntry = blocks.getConfigurationSection(key);
      if (blockEntry == null) {
        throw invalid(blockPath, "expected a configuration section");
      }

      Material block = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
      if (block == null) {
        throw invalid(blockPath, "unknown material '" + key + "'");
      }
      if (!block.isBlock() || isAir(block)) {
        throw invalid(blockPath, "expected a non-air block material");
      }

      String dropName = requiredString(blockEntry.get("drop"), blockPath + ".drop", "material name");
      Material drop = Material.matchMaterial(dropName.toUpperCase(Locale.ROOT));
      if (drop == null) {
        throw invalid(blockPath + ".drop", "unknown material '" + dropName + "'");
      }
      if (!drop.isItem() || isAir(drop)) {
        throw invalid(blockPath + ".drop", "expected a non-air item material");
      }
      parsedDrops.put(block, drop);
    }
    return parsedDrops;
  }

  private static VeinConfig parseVeinConfig(FileConfiguration config) {
    Object rawVeins = config.get("veins");
    if (!(rawVeins instanceof ConfigurationSection)) {
      throw invalid("veins", "expected a configuration section");
    }
    return new VeinConfig((ConfigurationSection) rawVeins);
  }

  private static List<ItemDropRule> parseDropRules(FileConfiguration config, List<ItemDropRule> itemRules,
                                                    List<ItemDropRule> commandRules) {
    Object rawDrops = config.get("drops");
    if (rawDrops == null) {
      throw invalid("drops", "expected a non-empty list");
    }
    if (!(rawDrops instanceof List<?>)) {
      throw invalid("drops", "expected a non-empty list");
    }

    List<?> configuredDrops = (List<?>) rawDrops;
    if (configuredDrops.isEmpty()) {
      throw invalid("drops", "expected a non-empty list");
    }

    List<ItemDropRule> parsedRules = new ArrayList<>(configuredDrops.size());
    long generationBlockTargets = 0L;
    for (int index = 0; index < configuredDrops.size(); index++) {
      Object rawEntry = configuredDrops.get(index);
      String path = "drops[" + index + "]";
      if (!(rawEntry instanceof Map<?, ?>)) {
        throw invalid(path, "expected a map");
      }

      Map<?, ?> entry = (Map<?, ?>) rawEntry;
      ItemDropRule rule = isCommandRule(entry) ? parseCommandRule(entry, path) : parseItemRule(entry, path);
      parsedRules.add(rule);
      if (rule.type == ItemDropRule.DropType.COMMAND) {
        commandRules.add(rule);
      } else {
        long ruleBlockTargets = maximumGenerationBlockTargets(rule);
        if (generationBlockTargets > MAX_GENERATION_BLOCK_TARGETS_PER_CHUNK - ruleBlockTargets) {
          throw invalid(path, "combined worst-case generation work must be less than or equal to "
              + MAX_GENERATION_BLOCK_TARGETS_PER_CHUNK + " target blocks per chunk");
        }
        generationBlockTargets += ruleBlockTargets;
        itemRules.add(rule);
      }
    }
    return parsedRules;
  }

  private static boolean isCommandRule(Map<?, ?> entry) {
    Object typeValue = entry.get("type");
    String type = typeValue instanceof String ? ((String) typeValue).toLowerCase(Locale.ROOT) : "";
    return "command".equals(type) || entry.containsKey("command") || entry.containsKey("commands");
  }

  private static ItemDropRule parseCommandRule(Map<?, ?> entry, String path) {
    int minY = optionalInteger(entry, "min_y", -64, path + ".min_y");
    int maxY = optionalInteger(entry, "max_y", 320, path + ".max_y");
    validateRange(minY, maxY, path);

    double chance = optionalFiniteDouble(entry, "chance", 0.0, path + ".chance");
    if (chance < 0.0 || chance > 1.0) {
      throw invalid(path + ".chance", "must be between 0 and 1 inclusive");
    }

    List<String> commands = parseCommands(entry, path);
    ItemDropRule.ExecutionTarget executionTarget = parseExecutionTarget(entry, path);
    return new ItemDropRule(List.copyOf(commands), chance, minY, maxY, executionTarget);
  }

  private static ItemDropRule parseItemRule(Map<?, ?> entry, String path) {
    int minY = optionalInteger(entry, "min_y", -64, path + ".min_y");
    int maxY = optionalInteger(entry, "max_y", 320, path + ".max_y");
    validateRange(minY, maxY, path);

    String itemName = requiredString(entry.get("item"), path + ".item", "item material name");
    Material material = Material.matchMaterial(itemName.toUpperCase(Locale.ROOT));
    if (material == null) {
      throw invalid(path + ".item", "unknown material '" + itemName + "'");
    }
    if (!material.isItem() || isAir(material)) {
      throw invalid(path + ".item", "expected a non-air item material");
    }

    double veinsPerChunk = optionalFiniteDouble(entry, "veins_per_chunk", 0.0, path + ".veins_per_chunk");
    if (veinsPerChunk < 0.0) {
      throw invalid(path + ".veins_per_chunk", "must be greater than or equal to 0");
    }
    if (veinsPerChunk > MAX_VEINS_PER_CHUNK) {
      throw invalid(path + ".veins_per_chunk", "must be less than or equal to " + MAX_VEINS_PER_CHUNK);
    }

    int veinMinSize = optionalInteger(entry, "vein_min_size", 1, path + ".vein_min_size");
    if (veinMinSize < 1) {
      throw invalid(path + ".vein_min_size", "must be greater than or equal to 1");
    }
    if (veinMinSize > MAX_VEIN_SIZE) {
      throw invalid(path + ".vein_min_size", "must be less than or equal to " + MAX_VEIN_SIZE);
    }
    int veinMaxSize = optionalInteger(entry, "vein_max_size", veinMinSize, path + ".vein_max_size");
    if (veinMaxSize < veinMinSize) {
      throw invalid(path + ".vein_max_size", "must be greater than or equal to " + path + ".vein_min_size");
    }
    if (veinMaxSize > MAX_VEIN_SIZE) {
      throw invalid(path + ".vein_max_size", "must be less than or equal to " + MAX_VEIN_SIZE);
    }

    Object fortuneValue = entry.get("fortune_multiplier");
    if (fortuneValue != null && !(fortuneValue instanceof Boolean)) {
      throw invalid(path + ".fortune_multiplier", "expected true or false");
    }
    boolean fortuneMultiplier = fortuneValue instanceof Boolean && (Boolean) fortuneValue;
    Set<ToolTier> toolTiers = parseToolTiers(entry.get("tool_tiers"), path + ".tool_tiers");
    int expDrop = parseExpDrop(entry, material, path);
    ItemDropRule rule = new ItemDropRule(material, veinsPerChunk, veinMinSize, veinMaxSize, minY, maxY, fortuneMultiplier, toolTiers, expDrop);
    if (rule.pureRandomChance() > 1.0) {
      throw invalid(path + ".veins_per_chunk", "derived pure_random probability must be less than or equal to 1");
    }
    return rule;
  }

  private static long maximumGenerationBlockTargets(ItemDropRule rule) {
    return (long) Math.ceil(rule.veinsPerChunk) * rule.veinMaxSize;
  }

  private static List<String> parseCommands(Map<?, ?> entry, String path) {
    if (entry.containsKey("command")) {
      throw invalid(path + ".command", "unsupported; use 'commands'");
    }

    Object commandList = entry.get("commands");
    if (commandList == null) {
      throw invalid(path + ".commands", "must contain at least one command");
    }
    if (!(commandList instanceof List<?>)) {
      throw invalid(path + ".commands", "expected a list of commands");
    }

    List<?> configuredCommands = (List<?>) commandList;
    if (configuredCommands.isEmpty()) {
      throw invalid(path + ".commands", "must contain at least one command");
    }

    List<String> commands = new ArrayList<>(configuredCommands.size());
    for (int index = 0; index < configuredCommands.size(); index++) {
      Object configuredCommand = configuredCommands.get(index);
      String commandPath = path + ".commands[" + index + "]";
      if (!(configuredCommand instanceof String) || ((String) configuredCommand).isBlank()) {
        throw invalid(commandPath, "expected a non-empty command");
      }
      commands.add((String) configuredCommand);
    }
    return commands;
  }

  private static ItemDropRule.ExecutionTarget parseExecutionTarget(Map<?, ?> entry, String path) {
    if (entry.containsKey("as_player")) {
      throw invalid(path + ".as_player", "unsupported; use 'execute_as'");
    }
    if (entry.containsKey("as_console")) {
      throw invalid(path + ".as_console", "unsupported; use 'execute_as'");
    }

    Object executeAsValue = entry.get("execute_as");
    if (executeAsValue instanceof String) {
      String executeAs = (String) executeAsValue;
      if ("player".equals(executeAs)) {
        return ItemDropRule.ExecutionTarget.PLAYER;
      }
      if ("console".equals(executeAs)) {
        return ItemDropRule.ExecutionTarget.CONSOLE;
      }
      throw invalid(path + ".execute_as", "expected 'player' or 'console'");
    }
    if (executeAsValue != null) {
      throw invalid(path + ".execute_as", "expected 'player' or 'console'");
    }

    return ItemDropRule.ExecutionTarget.CONSOLE;
  }

  private static Set<ToolTier> parseToolTiers(Object value, String path) {
    if (!(value instanceof List<?>)) {
      throw invalid(path, "expected a non-empty list");
    }

    List<?> configuredTiers = (List<?>) value;
    if (configuredTiers.isEmpty()) {
      throw invalid(path, "expected a non-empty list");
    }

    Set<ToolTier> tiers = new LinkedHashSet<>(configuredTiers.size());
    for (int index = 0; index < configuredTiers.size(); index++) {
      Object configuredTier = configuredTiers.get(index);
      String tierPath = path + "[" + index + "]";
      if (!(configuredTier instanceof String) || ((String) configuredTier).isBlank()) {
        throw invalid(tierPath, "expected a tool tier name");
      }

      String tierName = (String) configuredTier;
      try {
        tiers.add(ToolTier.valueOf(tierName.toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException exception) {
        throw invalid(tierPath, "unknown tool tier '" + tierName + "'");
      }
    }
    return Set.copyOf(tiers);
  }

  private static int parseExpDrop(Map<?, ?> entry, Material material, String path) {
    int defaultExp = switch (material) {
      case COAL, RAW_COPPER -> 2;
      case DIAMOND, EMERALD -> 7;
      case LAPIS_LAZULI, REDSTONE, QUARTZ -> 5;
      default -> 0;
    };
    int expDrop = optionalInteger(entry, "exp_drop", defaultExp, path + ".exp_drop");
    if (expDrop < 0) {
      throw invalid(path + ".exp_drop", "must be greater than or equal to 0");
    }
    if (expDrop > MAX_EXP_DROP) {
      throw invalid(path + ".exp_drop", "must be less than or equal to " + MAX_EXP_DROP);
    }
    return expDrop;
  }

  private static String requiredString(Object value, String path, String description) {
    if (!(value instanceof String) || ((String) value).isBlank()) {
      throw invalid(path, "expected a non-empty " + description);
    }
    return (String) value;
  }

  private static int optionalInteger(Map<?, ?> entry, String key, int defaultValue, String path) {
    Object value = entry.get(key);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Number)) {
      throw invalid(path, "expected an integer");
    }

    double numericValue = ((Number) value).doubleValue();
    if (!Double.isFinite(numericValue) || numericValue != Math.rint(numericValue) || numericValue < Integer.MIN_VALUE || numericValue > Integer.MAX_VALUE) {
      throw invalid(path, "expected an integer");
    }
    return (int) numericValue;
  }

  private static double optionalFiniteDouble(Map<?, ?> entry, String key, double defaultValue, String path) {
    Object value = entry.get(key);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Number)) {
      throw invalid(path, "expected a finite number");
    }

    double numericValue = ((Number) value).doubleValue();
    if (!Double.isFinite(numericValue)) {
      throw invalid(path, "expected a finite number");
    }
    return numericValue;
  }

  private static void validateRange(int minY, int maxY, String path) {
    if (minY > maxY) {
      throw invalid(path + ".min_y", "must be less than or equal to " + path + ".max_y");
    }
  }

  private static boolean isAir(Material material) {
    return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
  }

  private static List<ItemDropRule> matchingRules(List<ItemDropRule> rules, int y) {
    List<ItemDropRule> matches = new ArrayList<>();
    for (ItemDropRule rule : rules) {
      if (y >= rule.minY && y <= rule.maxY) {
        matches.add(rule);
      }
    }
    return List.copyOf(matches);
  }

  private static IllegalArgumentException invalid(String path, String message) {
    return new IllegalArgumentException(path + ": " + message);
  }

}
