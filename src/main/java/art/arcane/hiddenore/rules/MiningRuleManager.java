package art.arcane.hiddenore.rules;

import art.arcane.hiddenore.util.project.ToolTier;
import art.arcane.hiddenore.vein.VeinConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MiningRuleManager {
  private final Plugin plugin;
  private final Map<Material, Material> guaranteedDrops = new HashMap<>();
  private final List<ItemDropRule> dropRules = new ArrayList<>();
  private VeinConfig veinConfig;

  public MiningRuleManager(Plugin plugin) {
    this.plugin = plugin;
    reload();
  }

  @SuppressWarnings("unchecked")
  public void reload() {
    guaranteedDrops.clear();
    dropRules.clear();

    ConfigurationSection blockSection = plugin.getConfig().getConfigurationSection("blocks");
    if (blockSection != null) {
      for (String key : blockSection.getKeys(false)) {
        Material block = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
        if (block == null) continue;
        String dropName = blockSection.getConfigurationSection(key).getString("drop");
        Material drop = Material.matchMaterial(dropName.toUpperCase(Locale.ROOT));
        if (drop == null) continue;
        guaranteedDrops.put(block, drop);
      }
    }

    List<Map<?, ?>> dropList = plugin.getConfig().getMapList("drops");
    for (Map<?, ?> entry : dropList) {
      Object minYObj = entry.get("min_y");
      int minY = minYObj instanceof Number ? ((Number) minYObj).intValue() : -64;

      Object maxYObj = entry.get("max_y");
      int maxY = maxYObj instanceof Number ? ((Number) maxYObj).intValue() : 320;

      String typeStr = entry.get("type") instanceof String ? ((String) entry.get("type")).toLowerCase(Locale.ROOT) : "";
      Object commandObj = entry.get("command");
      Object commandsObj = entry.get("commands");
      boolean isCommand = "command".equals(typeStr) || commandObj != null || commandsObj != null;

      if (isCommand) {
        Object chanceObj = entry.get("chance");
        double chance = chanceObj instanceof Number ? ((Number) chanceObj).doubleValue() : 0.0;

        List<String> commands = new ArrayList<>();
        if (commandObj instanceof String) {
          commands.add((String) commandObj);
        } else if (commandsObj instanceof List) {
          for (Object o : (List<?>) commandsObj) {
            if (o instanceof String) commands.add((String) o);
          }
        }

        ItemDropRule.ExecutionTarget execTarget = ItemDropRule.ExecutionTarget.CONSOLE;
        Object execAsObj = entry.get("execute_as");
        if (execAsObj instanceof String) {
          String execAs = ((String) execAsObj).toLowerCase(Locale.ROOT);
          if ("player".equals(execAs) || "as_player".equals(execAs))
            execTarget = ItemDropRule.ExecutionTarget.PLAYER;
        } else {
          Object asPlayerObj = entry.get("as_player");
          if (asPlayerObj instanceof Boolean && (Boolean) asPlayerObj) {
            execTarget = ItemDropRule.ExecutionTarget.PLAYER;
          }
        }

        dropRules.add(new ItemDropRule(commands, chance, minY, maxY, execTarget));
        continue;
      }

      String itemName = entry.get("item") instanceof String ? (String) entry.get("item") : null;
      if (itemName == null) continue;
      Material mat = Material.matchMaterial(itemName.toUpperCase(Locale.ROOT));
      if (mat == null) continue;

      Object fortuneObj = entry.get("fortune_multiplier");
      boolean fortune = fortuneObj instanceof Boolean && (Boolean) fortuneObj;

      Object veinsPerChunkObj = entry.get("veins_per_chunk");
      double veinsPerChunk = veinsPerChunkObj instanceof Number ? ((Number) veinsPerChunkObj).doubleValue() : 0.0;

      Object veinMinObj = entry.get("vein_min_size");
      int veinMinSize = veinMinObj instanceof Number ? ((Number) veinMinObj).intValue() : 1;

      Object veinMaxObj = entry.get("vein_max_size");
      int veinMaxSize = veinMaxObj instanceof Number ? ((Number) veinMaxObj).intValue() : veinMinSize;

      List<String> tiersList = (List<String>) entry.get("tool_tiers");
      Set<ToolTier> toolTiers = new HashSet<>();
      if (tiersList != null) {
        for (String s : tiersList) {
          try {
            ToolTier tier = ToolTier.valueOf(s.toUpperCase(Locale.ROOT));
            toolTiers.add(tier);
          } catch (IllegalArgumentException ignored) {
          }
        }
      }

      Object expDropObj = entry.get("exp_drop");
      int expDrop;
      if (expDropObj instanceof Number) {
        expDrop = ((Number) expDropObj).intValue();
      } else {
        expDrop = switch (mat) {
          case COAL, RAW_COPPER -> 2;
          case DIAMOND, EMERALD -> 7;
          case LAPIS_LAZULI, REDSTONE, QUARTZ -> 5;
          default -> 0;
        };
      }

      dropRules.add(new ItemDropRule(mat, veinsPerChunk, veinMinSize, veinMaxSize, minY, maxY, fortune, toolTiers, expDrop));
    }

    veinConfig = new VeinConfig(plugin.getConfig().getConfigurationSection("veins"));
  }

  public Material getGuaranteedDrop(Material blockType) {
    return guaranteedDrops.get(blockType);
  }

  public List<ItemDropRule> getCommandRules(int y) {
    List<ItemDropRule> result = new ArrayList<>();
    for (ItemDropRule rule : dropRules) {
      if (rule.type == ItemDropRule.DropType.COMMAND && y >= rule.minY && y <= rule.maxY) {
        result.add(rule);
      }
    }
    return result;
  }

  public List<ItemDropRule> getAllDropRules() {
    return new ArrayList<>(dropRules);
  }

  public VeinConfig getVeinConfig() {
    return veinConfig;
  }
}
