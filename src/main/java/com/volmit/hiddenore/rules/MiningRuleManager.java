package com.volmit.hiddenore.rules;

import com.volmit.hiddenore.rules.ItemDropRule;
import com.volmit.hiddenore.util.ToolTier;
import com.volmit.hiddenore.vein.VeinConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.*;

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

        // Load guaranteed drops (blocks)
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

        // Load drops (support both item drops and command drops)
        List<Map<?, ?>> dropList = plugin.getConfig().getMapList("drops");
        for (Map<?, ?> entry : dropList) {
            // Basic common parse
            Object chanceObj = entry.get("chance");
            double chance = chanceObj instanceof Number ? ((Number) chanceObj).doubleValue() : 0.0;

            Object minYObj = entry.get("min_y");
            int minY = minYObj instanceof Number ? ((Number) minYObj).intValue() : -64;

            Object maxYObj = entry.get("max_y");
            int maxY = maxYObj instanceof Number ? ((Number) maxYObj).intValue() : 320;

            // Detect if this is a command drop: either "type: command" or presence of "command" / "commands" key
            String typeStr = entry.get("type") instanceof String ? ((String) entry.get("type")).toLowerCase(Locale.ROOT) : "";
            Object commandObj = entry.get("command");
            Object commandsObj = entry.get("commands");
            boolean isCommand = "command".equals(typeStr) || commandObj != null || commandsObj != null;

            if (isCommand) {
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
                    if ("player".equals(execAs) || "as_player".equals(execAs)) execTarget = ItemDropRule.ExecutionTarget.PLAYER;
                } else {
                    Object asPlayerObj = entry.get("as_player");
                    if (asPlayerObj instanceof Boolean && (Boolean) asPlayerObj) {
                        execTarget = ItemDropRule.ExecutionTarget.PLAYER;
                    }
                }

                // Command drops: chance only, no fortune, no veins, no xp, default tool tiers empty
                ItemDropRule rule = new ItemDropRule(commands, chance, minY, maxY, execTarget);
                dropRules.add(rule);
                continue;
            }

            // Otherwise it's an item drop
            String itemName = entry.get("item") instanceof String ? (String) entry.get("item") : null;
            if (itemName == null) continue;
            Material mat = Material.matchMaterial(itemName.toUpperCase(Locale.ROOT));
            if (mat == null) continue;

            Object fortuneObj = entry.get("fortune_multiplier");
            boolean fortune = fortuneObj instanceof Boolean && (Boolean) fortuneObj;

            Object veinSizeObj = entry.get("vein_max_size");
            int veinMaxSize = veinSizeObj instanceof Number ? ((Number) veinSizeObj).intValue() : 0;

            List<String> tiersList = (List<String>) entry.get("tool_tiers");
            Set<ToolTier> toolTiers = new HashSet<>();
            if (tiersList != null) {
                for (String s : tiersList) {
                    try {
                        ToolTier tier = ToolTier.valueOf(s.toUpperCase(Locale.ROOT));
                        toolTiers.add(tier);
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            // XP drop
            Object expDropObj = entry.get("exp_drop");
            int expDrop;
            if (expDropObj instanceof Number) {
                expDrop = ((Number) expDropObj).intValue();
            } else {
                // sensible vanilla defaults
                switch (mat) {
                    case COAL: expDrop = 2; break;
                    case DIAMOND: expDrop = 7; break;
                    case EMERALD: expDrop = 7; break;
                    case LAPIS_LAZULI: expDrop = 5; break;
                    case REDSTONE: expDrop = 5; break;
                    case RAW_COPPER: expDrop = 2; break;
                    case QUARTZ: expDrop = 5; break;
                    default: expDrop = 0; break;
                }
            }

            dropRules.add(new ItemDropRule(mat, chance, minY, maxY, fortune, toolTiers, veinMaxSize, expDrop));
        }

        // Load veins
        ConfigurationSection veins = plugin.getConfig().getConfigurationSection("veins");
        veinConfig = veins != null ? new VeinConfig(veins) : null;
    }

    public Material getGuaranteedDrop(Material blockType) {
        return guaranteedDrops.get(blockType);
    }

    public List<ItemDropRule> getApplicableDrops(Material pickaxeMat, int y) {
        ToolTier pickaxeTier = ToolTier.fromMaterial(pickaxeMat);
        List<ItemDropRule> result = new ArrayList<>();
        for (ItemDropRule rule : dropRules) {
            if (y < rule.minY || y > rule.maxY) continue;

            if (rule.type == ItemDropRule.DropType.COMMAND) {
                result.add(rule);
                continue;
            }

            if (pickaxeTier != null && rule.toolTiers.contains(pickaxeTier)) {
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