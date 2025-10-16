package com.volmit.hiddenore.listeners;

import com.volmit.hiddenore.HiddenOre;
import com.volmit.hiddenore.rules.ItemDropRule;
import com.volmit.hiddenore.util.MiningUtil;
import com.volmit.hiddenore.util.ToolTier;
import com.volmit.hiddenore.vein.PlayerVeinState;
import com.volmit.hiddenore.vein.VeinConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MiningListener implements Listener {
    private final HiddenOre plugin;

    public MiningListener(HiddenOre plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        final Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return; // Skip creative mode

        final UUID uuid = player.getUniqueId();
        final boolean debug = plugin.isDebug(uuid);

        final Material blockType = event.getBlock().getType();
        final ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || !MiningUtil.isPickaxe(tool.getType())) return;

        // Only act if block is in blocks section
        boolean isCustomBlock = plugin.getRuleManager().getGuaranteedDrop(blockType) != null;
        if (!isCustomBlock) {
            // Not a configured block, let vanilla drops occur
            return;
        }

        final int y = event.getBlock().getY();
        final org.bukkit.Location loc = event.getBlock().getLocation();
        final org.bukkit.Location centerLoc = loc.clone().add(0.5, 0.5, 0.5);
        final org.bukkit.World world = event.getBlock().getWorld();
        final ItemStack toolClone = tool.clone();

        final VeinConfig veinCfg = plugin.getRuleManager().getVeinConfig();
        final PlayerVeinState state = plugin.getPlayerVeinState(uuid);

        // Get guaranteed drop (e.g. stone -> cobblestone)
        Material guaranteedDrop = plugin.getRuleManager().getGuaranteedDrop(blockType);

        // Configurable flag: suppress block drop if a custom drop occurs
        boolean suppressBlockDrop = plugin.getConfig().getBoolean("suppress_block_drop_on_custom_drop", false);

        event.setDropItems(false);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Material, Integer> drops = new HashMap<>();
            Map<Material, Integer> expToDrop = new HashMap<>();
            List<Component> debugMessages = debug ? new ArrayList<>() : null;
            long now = System.currentTimeMillis();

            boolean customDropOccurred = false;

            // --- VEIN TOKEN LOGIC ---
            if (state.veinTokens > 0
                    && veinCfg != null
                    && withinWindow(state, now, veinCfg.tokenWindowMs)
                    && withinRange(state, event.getBlock().getLocation(), veinCfg.tokenRange)) {
                // Repeat the last drop
                ItemDropRule rule = findRuleByKey(state.currentDropKey);
                if (rule != null) {
                    int amt = MiningUtil.applyFortune(toolClone, rule, 1);
                    drops.merge(rule.material, amt, Integer::sum);

                    // Calculate XP for this vein drop
                    if (rule.expDrop > 0) {
                        int exp = ThreadLocalRandom.current().nextInt(rule.expDrop + 1);
                        expToDrop.put(rule.material, expToDrop.getOrDefault(rule.material, 0) + exp);
                    }

                    customDropOccurred = true;

                    state.veinTokens--;
                    if (debug) debugMessages.add(plugin.getMessages().parse(
                            "<light_purple>Vein drop: " + rule.material.name().toLowerCase() + " x" + amt +
                                    " (tokens left: " + state.veinTokens + ")</light_purple>"
                    ));
                }
            } else {
                // Normal random drop logic
                List<ItemDropRule> rules = plugin.getRuleManager().getApplicableDrops(tool.getType(), y);
                for (ItemDropRule rule : rules) {
                    double roll = ThreadLocalRandom.current().nextDouble();
                    boolean dropped = false;
                    int amount = 0;

                    if (roll < rule.chance) {
                        amount = MiningUtil.applyFortune(toolClone, rule, 1);
                        drops.merge(rule.material, amount, Integer::sum);
                        dropped = true;
                        customDropOccurred = true;

                        // Calculate XP for this drop
                        if (rule.expDrop > 0) {
                            int exp = ThreadLocalRandom.current().nextInt(rule.expDrop + 1);
                            expToDrop.put(rule.material, expToDrop.getOrDefault(rule.material, 0) + exp);
                        }

                        // Try to start a vein if configured
                        if (veinCfg != null && rule.veinMaxSize > 0 && now >= state.cooldownUntil) {
                            double veinChance = veinCfg.veinBaseChance;
                            veinChance *= 1.0 + computeVeinChanceBonus(player, toolClone, veinCfg);
                            veinChance = Math.max(0.0, Math.min(veinChance, 0.95));
                            double veinRoll = ThreadLocalRandom.current().nextDouble();
                            if (veinRoll < veinChance) {
                                int veinSize = 1 + ThreadLocalRandom.current().nextInt(rule.veinMaxSize);
                                state.veinTokens = veinSize;
                                state.cooldownUntil = now + veinCfg.cooldownMs;
                                state.currentDropKey = veinDropKey(rule);

                                // Play vein start sound if valid
                                try {
                                    Sound veinSound = Sound.valueOf(veinCfg.veinStartSound);
                                    Bukkit.getScheduler().runTask(plugin, () ->
                                            player.playSound(player.getLocation(), veinSound, veinCfg.veinStartSoundVolume, veinCfg.veinStartSoundPitch));
                                } catch (IllegalArgumentException ignored) {}

                                if (debug) debugMessages.add(plugin.getMessages().parse(
                                        "<green>Started vein: " + rule.material.name().toLowerCase() +
                                                " size: " + veinSize + " (chance=" + veinChance + ", roll=" + String.format("%.4f", veinRoll) + ")</green>"
                                ));
                            }
                        }
                    }
                    if (debug) {
                        debugMessages.add(plugin.getMessages().parse(
                                "<gray>Drop roll for <yellow>" + rule.material.name().toLowerCase(Locale.ROOT) +
                                        "</yellow>: chance=<gold>" + rule.chance +
                                        "</gold>, roll=<blue>" + String.format("%.4f", roll) +
                                        "</blue> | " + (dropped ? "<green>SUCCESS!</green>" : "<red>fail</red>") +
                                        (dropped ? " <yellow>Amount: " + amount + "</yellow>" : "") +
                                        " at Y=" + y + (rule.fortuneMultiplier ? " <green>[fortune]</green>" : "")
                        ));
                    }
                }
            }

            // Only add the guaranteed drop if allowed by config and custom drop did not occur
            if (guaranteedDrop != null && (!suppressBlockDrop || !customDropOccurred)) {
                drops.put(guaranteedDrop, 1);
                if (debug) {
                    debugMessages.add(plugin.getMessages().parse(
                            "<gray>Guaranteed drop: <yellow>" + guaranteedDrop.name().toLowerCase(Locale.ROOT) +
                                    "</yellow> for block <gold>" + blockType.name().toLowerCase(Locale.ROOT) +
                                    "</gold> at Y=" + y
                    ));
                }
            }

            // Update vein state
            state.lastMineTime = now;
            state.lastMinedBlock = event.getBlock().getLocation();

            if (drops.isEmpty() && (debug && debugMessages != null && !debugMessages.isEmpty())) {
                debugMessages.add(plugin.getMessages().parse("<red>No drops for this block at Y=" + y + "</red>"));
            }

            if (debug && debugMessages != null && !debugMessages.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Component msg : debugMessages) player.sendMessage(msg);
                });
            }
            if (drops.isEmpty()) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean autoPickup = plugin.isAutoPickup();
                for (Map.Entry<Material, Integer> e : drops.entrySet()) {
                    ItemStack stack = new ItemStack(e.getKey(), e.getValue());
                    if (autoPickup) {
                        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                        leftover.values().forEach(item -> world.dropItem(centerLoc, item));
                    } else {
                        world.dropItem(centerLoc, stack);
                    }
                }
                // XP drop: sum all exp for all materials in this break
                int totalExp = expToDrop.values().stream().mapToInt(Integer::intValue).sum();
                if (totalExp > 0) {
                    world.spawn(centerLoc, org.bukkit.entity.ExperienceOrb.class, orb -> orb.setExperience(totalExp));
                }
            });
        });
    }

    // --- VEIN HELPERS ---

    private boolean withinWindow(PlayerVeinState state, long now, long windowMs) {
        return (now - state.lastMineTime) <= windowMs;
    }

    /**
     * Enforces that the next block mined for a venin drop must be within the token_range of the last mined block.
     */

    private boolean withinRange(PlayerVeinState state, org.bukkit.Location block, double maxRange) {
        if (state.lastMinedBlock == null) return false;
        return state.lastMinedBlock.getWorld().equals(block.getWorld()) &&
                state.lastMinedBlock.distance(block) <= maxRange;
    }

    private double computeVeinChanceBonus(Player player, ItemStack tool, VeinConfig cfg) {
        double total = 0.0;

        // Tool enchantments
        for (Map.Entry<Enchantment, Integer> ench : tool.getEnchantments().entrySet()) {
            String key = ench.getKey().getKey().getKey().toLowerCase(Locale.ROOT);
            Double mod = cfg.enchantModifiers.get(key);
            if (mod != null) {
                total += mod * ench.getValue();
            }
        }

        // Player potion effects
        if (player.hasPotionEffect(PotionEffectType.LUCK)) {
            int level = player.getPotionEffect(PotionEffectType.LUCK).getAmplifier() + 1;
            Double mod = cfg.enchantModifiers.get("luck");
            if (mod != null) total += mod * level;
        }
        if (player.hasPotionEffect(PotionEffectType.UNLUCK)) {
            int level = player.getPotionEffect(PotionEffectType.UNLUCK).getAmplifier() + 1;
            Double mod = cfg.enchantModifiers.get("unluck");
            if (mod != null) total += mod * level;
        }
        return total;
    }

    // Used to uniquely identify a drop for a vein chain
    private String veinDropKey(ItemDropRule rule) {
        return rule.material.name() + ":" + rule.minY + ":" + rule.maxY;
    }

    // Finds a rule by its unique key (for vein chaining)
    private ItemDropRule findRuleByKey(String key) {
        if (key == null) return null;
        for (ItemDropRule rule : plugin.getRuleManager().getAllDropRules()) {
            if (veinDropKey(rule).equals(key)) return rule;
        }
        return null;
    }
}