package art.arcane.hiddenore.listeners;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.api.HiddenVein;
import art.arcane.hiddenore.api.event.HiddenOreDropsEvent;
import art.arcane.hiddenore.rules.ItemDropRule;
import art.arcane.hiddenore.util.project.MiningUtil;
import art.arcane.hiddenore.util.project.ToolTier;
import art.arcane.hiddenore.vein.ChunkVeins;
import art.arcane.hiddenore.vein.VeinBlock;
import art.arcane.hiddenore.vein.VeinConfig;
import art.arcane.volmlib.util.bukkit.ChunkPositionSet;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class MiningListener implements Listener {
  private final HiddenOre plugin;

  public MiningListener(HiddenOre plugin) {
    this.plugin = plugin;
  }

  @EventHandler(ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (player.getGameMode() == GameMode.CREATIVE) {
      return;
    }

    ItemStack tool = player.getInventory().getItemInMainHand();
    if (!MiningUtil.isPickaxe(tool.getType())) {
      return;
    }

    Block block = event.getBlock();
    Material blockType = block.getType();
    Material guaranteedDrop = plugin.getRuleManager().getGuaranteedDrop(blockType);
    if (guaranteedDrop == null) {
      return;
    }

    boolean debug = plugin.isDebug(player.getUniqueId());
    World world = block.getWorld();
    Location centerLoc = block.getLocation().add(0.5, 0.5, 0.5);
    int y = block.getY();

    event.setDropItems(false);

    boolean placed = plugin.getPlacedBlocks().remove(block);
    List<ItemStack> drops = new ArrayList<>();
    int experience = 0;
    HiddenVein vein = null;
    boolean commandFired = false;

    if (placed) {
      if (debug) {
        HiddenOre.sendMessage(player, plugin.getMessages().parse("<red>player-placed " + blockType.name().toLowerCase(Locale.ROOT) + ", no hidden drops</red>"));
      }
    } else {
      ChunkVeins veins = plugin.getVeinGenerator().get(world, block.getX() >> 4, block.getZ() >> 4);
      int packed = ChunkPositionSet.pack(block.getX() & 15, y, block.getZ() & 15, world.getMinHeight());
      VeinBlock veinBlock = veins.get(packed);

      if (veinBlock != null && !plugin.getConsumedVeins().contains(block)) {
        boolean firstOfVein = isFirstOfVein(block, veins, veinBlock, packed);
        plugin.getConsumedVeins().add(block);
        ItemDropRule rule = veinBlock.rule();
        ToolTier tier = ToolTier.fromMaterial(tool.getType());

        if (tier != null && rule.toolTiers.contains(tier)) {
          int amount = MiningUtil.applyFortune(tool, rule, 1);
          drops.add(new ItemStack(rule.material, amount));
          if (rule.expDrop > 0) {
            experience += ThreadLocalRandom.current().nextInt(rule.expDrop + 1);
          }
          vein = new HiddenVein(block.getX(), y, block.getZ(), veinBlock.veinId(), rule.material, HiddenVein.oreDisplayFor(rule.material, y));
          if (firstOfVein) {
            playDiscoverySound(player);
          }
          if (debug) {
            HiddenOre.sendMessage(player, plugin.getMessages().parse("<green>vein " + veinBlock.veinId() + ": " + rule.material.name().toLowerCase(Locale.ROOT) + " x" + amount + (firstOfVein ? " (discovered)" : "") + "</green>"));
          }
        } else if (debug) {
          HiddenOre.sendMessage(player, plugin.getMessages().parse("<red>vein " + veinBlock.veinId() + ": " + rule.material.name().toLowerCase(Locale.ROOT) + " lost, tool tier too low</red>"));
        }
      }

      commandFired = rollCommands(player, block, y, debug);
    }

    boolean suppressBlockDrop = plugin.getConfig().getBoolean("suppress_block_drop_on_custom_drop", false);
    boolean customDrop = !drops.isEmpty() || commandFired;
    if (!suppressBlockDrop || !customDrop) {
      drops.add(new ItemStack(guaranteedDrop, 1));
    }

    HiddenOreDropsEvent dropsEvent = new HiddenOreDropsEvent(player, block, blockType, tool.clone(), vein, drops, experience, plugin.isAutoPickup());
    Bukkit.getPluginManager().callEvent(dropsEvent);

    for (ItemStack stack : dropsEvent.getDrops()) {
      if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
        continue;
      }
      if (dropsEvent.isToInventory()) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        leftover.values().forEach(item -> world.dropItem(centerLoc, item));
      } else {
        world.dropItem(centerLoc, stack);
      }
    }

    int totalExp = dropsEvent.getExperience();
    if (totalExp > 0) {
      world.spawn(centerLoc, ExperienceOrb.class, orb -> orb.setExperience(totalExp));
    }
  }

  private boolean isFirstOfVein(Block block, ChunkVeins veins, VeinBlock veinBlock, int packed) {
    int[] consumed = plugin.getConsumedVeins().snapshot(block.getChunk());
    if (consumed.length == 0) {
      return true;
    }
    for (int position : veins.positionsOf(veinBlock.veinId())) {
      if (position != packed && ChunkPositionSet.contains(consumed, position)) {
        return false;
      }
    }
    return true;
  }

  private void playDiscoverySound(Player player) {
    VeinConfig veinConfig = plugin.getRuleManager().getVeinConfig();
    try {
      Sound sound = Sound.valueOf(veinConfig.discoverySound);
      player.playSound(player.getLocation(), sound, veinConfig.discoveryVolume, veinConfig.discoveryPitch);
    } catch (IllegalArgumentException ignored) {
    }
  }

  private boolean rollCommands(Player player, Block block, int y, boolean debug) {
    List<CommandExec> commandsToExecute = new ArrayList<>();
    for (ItemDropRule rule : plugin.getRuleManager().getCommandRules(y)) {
      double roll = ThreadLocalRandom.current().nextDouble();
      boolean success = roll < rule.chance;
      if (success && rule.commands != null) {
        for (String raw : rule.commands) {
          if (raw == null) {
            continue;
          }
          String trimmed = raw.trim();
          ItemDropRule.ExecutionTarget target = rule.executionTarget;
          String cmdText = trimmed;
          int colon = trimmed.indexOf(':');
          if (colon > 0) {
            String prefix = trimmed.substring(0, colon).toLowerCase(Locale.ROOT);
            if ("player".equals(prefix) || "console".equals(prefix)) {
              cmdText = trimmed.substring(colon + 1).trim();
              target = "player".equals(prefix) ? ItemDropRule.ExecutionTarget.PLAYER : ItemDropRule.ExecutionTarget.CONSOLE;
            }
          }
          if (!cmdText.isEmpty()) {
            commandsToExecute.add(new CommandExec(cmdText, target));
          }
        }
      }
      if (debug) {
        HiddenOre.sendMessage(player, plugin.getMessages().parse("<gray>command roll: chance=" + rule.chance + ", roll=" + String.format("%.4f", roll) + " -> " + (success ? "<green>hit</green>" : "<red>miss</red>") + "</gray>"));
      }
    }

    if (commandsToExecute.isEmpty()) {
      return false;
    }

    Location loc = block.getLocation();
    SchedulerUtils.runSync(plugin, () -> {
      for (CommandExec exec : commandsToExecute) {
        String cmdWithPlaceholders = applyCommandPlaceholders(exec.command, player, loc);
        String cmd = cmdWithPlaceholders.startsWith("/") ? cmdWithPlaceholders.substring(1) : cmdWithPlaceholders;
        if (exec.target == ItemDropRule.ExecutionTarget.PLAYER) {
          Bukkit.dispatchCommand(player, cmd);
        } else {
          Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
      }
    });
    return true;
  }

  private String applyCommandPlaceholders(String raw, Player player, Location loc) {
    if (raw == null) {
      return "";
    }
    String result = raw;
    result = result.replace("%player%", player.getName());
    result = result.replace("%uuid%", player.getUniqueId().toString());
    result = result.replace("%x%", String.valueOf(loc.getBlockX()));
    result = result.replace("%y%", String.valueOf(loc.getBlockY()));
    result = result.replace("%z%", String.valueOf(loc.getBlockZ()));
    result = result.replace("%world%", loc.getWorld() == null ? "" : loc.getWorld().getName());
    return result;
  }

  private static class CommandExec {
    final String command;
    final ItemDropRule.ExecutionTarget target;

    CommandExec(String command, ItemDropRule.ExecutionTarget target) {
      this.command = command;
      this.target = target;
    }
  }
}
