package art.arcane.hiddenore.listeners;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.api.HiddenVein;
import art.arcane.hiddenore.api.event.HiddenOreDropsEvent;
import art.arcane.hiddenore.rules.ItemDropRule;
import art.arcane.hiddenore.rules.MiningRuleManager;
import art.arcane.hiddenore.util.common.Messages;
import art.arcane.hiddenore.util.project.MiningUtil;
import art.arcane.hiddenore.util.project.SoundResolver;
import art.arcane.hiddenore.util.project.ToolTier;
import art.arcane.hiddenore.vein.ChunkVeins;
import art.arcane.hiddenore.vein.VeinBlock;
import art.arcane.hiddenore.vein.VeinConfig;
import art.arcane.volmlib.util.bukkit.ChunkPositionSet;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MiningListener implements Listener {
  private final HiddenOre plugin;
  private final Map<BreakKey, BreakPreparation> pendingBreaks = new ConcurrentHashMap<>();
  private final Map<BlockDropItemEvent, DropPreparation> pendingDrops = new ConcurrentHashMap<>();

  public MiningListener(HiddenOre plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    if (plugin.isDraining()) {
      return;
    }

    Player player = event.getPlayer();
    if (player.getGameMode() == GameMode.CREATIVE) {
      return;
    }

    ItemStack tool = player.getInventory().getItemInMainHand();
    if (!MiningUtil.isPickaxe(tool.getType())) {
      return;
    }

    HiddenOre.RuntimeState runtime = plugin.getRuntimeState();
    MiningRuleManager rules = runtime.ruleManager();
    if (rules.getGuaranteedDrop(event.getBlock().getType()) == null) {
      return;
    }

    event.setExpToDrop(0);
    if (!event.isDropItems()) {
      return;
    }

    pendingBreaks.put(breakKey(player, event.getBlock()), new BreakPreparation(runtime, snapshotTool(tool)));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onBlockBreakFinal(BlockBreakEvent event) {
    if (!shouldDiscardBreakPreparation(event.isCancelled(), event.isDropItems(), event.getBlock().getType().isAir())) {
      return;
    }
    pendingBreaks.remove(breakKey(event.getPlayer(), event.getBlock()));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void prepareBlockDrop(BlockDropItemEvent event) {
    BreakKey key = breakKey(event.getPlayer(), event.getBlockState());
    BreakPreparation preparation = pendingBreaks.remove(key);
    if (plugin.isDraining() || preparation == null) {
      return;
    }

    event.getItems().clear();
    boolean trackedPlacement = plugin.getPlacedBlocks().contains(event.getBlock());
    pendingDrops.put(event, new DropPreparation(preparation.runtime(), preparation.tool(), trackedPlacement));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void commitBlockDrop(BlockDropItemEvent event) {
    BreakKey key = breakKey(event.getPlayer(), event.getBlockState());
    pendingBreaks.remove(key);
    DropPreparation preparation = pendingDrops.remove(event);
    Block block = event.getBlock();
    try {
      if (!event.isCancelled() && preparation != null) {
        processAcceptedDrop(event, preparation);
      }
    } finally {
      plugin.getPlacedBlocks().remove(block);
    }
  }

  private void processAcceptedDrop(BlockDropItemEvent event, DropPreparation preparation) {
    Player player = event.getPlayer();
    BlockState blockState = event.getBlockState();
    Material blockType = blockState.getType();
    HiddenOre.RuntimeState runtime = preparation.runtime();
    MiningRuleManager rules = runtime.ruleManager();
    Messages messages = runtime.messages();
    Material guaranteedDrop = rules.getGuaranteedDrop(blockType);
    if (guaranteedDrop == null) {
      return;
    }

    ItemStack tool = preparation.tool();
    Block block = event.getBlock();
    boolean debug = plugin.isDebug(player.getUniqueId());
    World world = blockState.getWorld();
    Location blockLocation = blockState.getLocation();
    Location centerLoc = blockLocation.clone().add(0.5, 0.5, 0.5);
    int blockX = blockState.getX();
    int y = blockState.getY();
    int blockZ = blockState.getZ();

    VeinConfig veinConfig = rules.getVeinConfig();
    boolean placed = blocksHiddenRewards(veinConfig.allowPlacedBlocks, preparation.trackedPlacement());
    List<ItemStack> drops = new ArrayList<>();
    int experience = 0;
    HiddenVein vein = null;
    List<CommandExec> commandsToExecute = List.of();

    if (placed) {
      if (debug) {
        HiddenOre.sendMessage(player, messages.component(
            Messages.DEBUG_PLAYER_PLACED,
            MessageArgs.builder()
                .untrusted("block", blockType.name().toLowerCase(Locale.ROOT))
                .build()
        ));
      }
    } else if (veinConfig.generation == VeinConfig.GenerationMode.PURE_RANDOM) {
      ToolTier tier = ToolTier.fromMaterial(tool.getType());
      for (ItemDropRule rule : rules.getItemRules(y)) {
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll >= rule.pureRandomChance()) {
          continue;
        }
        if (tier != null && rule.toolTiers.contains(tier)) {
          int amount = MiningUtil.applyFortune(tool, rule, 1);
          drops.add(new ItemStack(rule.material, amount));
          if (rule.expDrop > 0) {
            experience += rollInclusiveExperience(rule.expDrop);
          }
          vein = new HiddenVein(blockX, y, blockZ, -1, rule.material, HiddenVein.oreDisplayFor(rule.material, y));
          playDiscoverySound(player, veinConfig);
          if (debug) {
            HiddenOre.sendMessage(player, messages.component(
                Messages.DEBUG_RANDOM_DROP,
                MessageArgs.builder()
                    .untrusted("material", rule.material.name().toLowerCase(Locale.ROOT))
                    .untrusted("amount", amount)
                    .build()
            ));
          }
        } else if (debug) {
          HiddenOre.sendMessage(player, messages.component(
              Messages.DEBUG_RANDOM_DROP_LOST,
              MessageArgs.builder()
                  .untrusted("material", rule.material.name().toLowerCase(Locale.ROOT))
                  .build()
          ));
        }
        break;
      }

      commandsToExecute = rollCommands(player, rules, messages, y, debug);
    } else {
      ChunkVeins veins = runtime.veinGenerator().get(world, blockX >> 4, blockZ >> 4);
      int packed = ChunkPositionSet.pack(blockX & 15, y, blockZ & 15, world.getMinHeight());
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
            experience += rollInclusiveExperience(rule.expDrop);
          }
          vein = new HiddenVein(blockX, y, blockZ, veinBlock.veinId(), rule.material, HiddenVein.oreDisplayFor(rule.material, y));
          if (firstOfVein) {
            playDiscoverySound(player, veinConfig);
          }
          if (debug) {
            HiddenOre.sendMessage(player, messages.component(
                firstOfVein ? Messages.DEBUG_VEIN_DROP_DISCOVERED : Messages.DEBUG_VEIN_DROP,
                MessageArgs.builder()
                    .untrusted("vein", veinBlock.veinId())
                    .untrusted("material", rule.material.name().toLowerCase(Locale.ROOT))
                    .untrusted("amount", amount)
                    .build()
            ));
          }
        } else if (debug) {
          HiddenOre.sendMessage(player, messages.component(
              Messages.DEBUG_VEIN_DROP_LOST,
              MessageArgs.builder()
                  .untrusted("vein", veinBlock.veinId())
                  .untrusted("material", rule.material.name().toLowerCase(Locale.ROOT))
                  .build()
          ));
        }
      }

      commandsToExecute = rollCommands(player, rules, messages, y, debug);
    }

    boolean customDrop = !drops.isEmpty() || !commandsToExecute.isEmpty();
    if (!runtime.suppressBlockDrop() || !customDrop) {
      drops.add(new ItemStack(guaranteedDrop, 1));
    }

    HiddenOreDropsEvent dropsEvent = new HiddenOreDropsEvent(player, block, blockType, tool.clone(), vein, drops, experience, runtime.autoPickup());
    Bukkit.getPluginManager().callEvent(dropsEvent);

    executeCommands(player, blockLocation, commandsToExecute);

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

  static boolean blocksHiddenRewards(boolean allowPlacedBlocks, boolean trackedPlacement) {
    return !allowPlacedBlocks && trackedPlacement;
  }

  static boolean shouldDiscardBreakPreparation(boolean cancelled, boolean dropItems, boolean blockAir) {
    return cancelled || !dropItems || blockAir;
  }

  static int rollInclusiveExperience(int maximum) {
    if (maximum <= 0) {
      return 0;
    }
    return (int) ThreadLocalRandom.current().nextLong((long) maximum + 1L);
  }

  static ItemStack snapshotTool(ItemStack tool) {
    return tool.clone();
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

  private void playDiscoverySound(Player player, VeinConfig veinConfig) {
    Sound sound = SoundResolver.resolve(veinConfig.discoverySound, Sound.BLOCK_BEACON_POWER_SELECT);
    player.playSound(player.getLocation(), sound, veinConfig.discoveryVolume, veinConfig.discoveryPitch);
  }

  private List<CommandExec> rollCommands(Player player, MiningRuleManager rules, Messages messages, int y, boolean debug) {
    List<CommandExec> commandsToExecute = new ArrayList<>();
    for (ItemDropRule rule : rules.getCommandRules(y)) {
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
        HiddenOre.sendMessage(player, messages.component(
            success ? Messages.DEBUG_COMMAND_HIT : Messages.DEBUG_COMMAND_MISS,
            MessageArgs.builder()
                .untrusted("chance", rule.chance)
                .untrusted("roll", String.format(Locale.ROOT, "%.4f", roll))
                .build()
        ));
      }
    }

    return List.copyOf(commandsToExecute);
  }

  private void executeCommands(Player player, Location location, List<CommandExec> commands) {
    List<CommandExec> resolvedCommands = new ArrayList<>(commands.size());
    for (CommandExec execution : commands) {
      String resolved = applyCommandPlaceholders(execution.command, player, location);
      String command = resolved.startsWith("/") ? resolved.substring(1) : resolved;
      resolvedCommands.add(new CommandExec(command, execution.target));
    }

    scheduleCommandGroup(player, List.copyOf(resolvedCommands), 0);
  }

  private void scheduleCommandGroup(Player player, List<CommandExec> commands, int startIndex) {
    int groupStart = startIndex;
    while (groupStart < commands.size()) {
      int groupEnd = commandGroupEnd(commands, groupStart);
      int scheduledStart = groupStart;
      int scheduledEnd = groupEnd;
      int continuationIndex = groupEnd;
      ItemDropRule.ExecutionTarget target = commands.get(groupStart).target;
      Runnable task = () -> {
        CommandSender sender = target == ItemDropRule.ExecutionTarget.PLAYER ? player : Bukkit.getConsoleSender();
        try {
          dispatchCommands(sender, commands, scheduledStart, scheduledEnd);
        } finally {
          scheduleCommandGroup(player, commands, continuationIndex);
        }
      };

      Runnable retired = () -> salvageConsoleGroups(player, commands, scheduledStart);
      boolean scheduled = target == ItemDropRule.ExecutionTarget.PLAYER
          ? FoliaScheduler.runEntity(plugin, player, task, 0L, retired)
          : SchedulerUtils.runGlobal(plugin, task);
      if (scheduled) {
        return;
      }

      plugin.getLogger().warning("Failed to schedule " + target.name().toLowerCase(Locale.ROOT) + " command rewards for " + player.getName());
      groupStart = groupEnd;
    }
  }

  private void salvageConsoleGroups(Player player, List<CommandExec> commands, int startIndex) {
    ConsoleSalvage salvage = salvageConsoleCommands(commands, startIndex);
    plugin.getLogger().info("skipped " + salvage.skippedPlayerGroups() + " player reward groups for " + player.getName() + ": offline");
    List<CommandExec> consoleCommands = salvage.commands();
    if (consoleCommands.isEmpty()) {
      return;
    }
    Runnable task = () -> dispatchCommands(Bukkit.getConsoleSender(), consoleCommands, 0, consoleCommands.size());
    if (!SchedulerUtils.runGlobal(plugin, task)) {
      plugin.getLogger().warning("Failed to schedule salvaged console command rewards for " + player.getName());
    }
  }

  static ConsoleSalvage salvageConsoleCommands(List<CommandExec> commands, int startIndex) {
    List<CommandExec> console = new ArrayList<>();
    int skippedPlayerGroups = 0;
    int index = startIndex;
    while (index < commands.size()) {
      int groupEnd = commandGroupEnd(commands, index);
      if (commands.get(index).target == ItemDropRule.ExecutionTarget.PLAYER) {
        skippedPlayerGroups++;
      } else {
        console.addAll(commands.subList(index, groupEnd));
      }
      index = groupEnd;
    }
    return new ConsoleSalvage(List.copyOf(console), skippedPlayerGroups);
  }

  record ConsoleSalvage(List<CommandExec> commands, int skippedPlayerGroups) {
  }

  static int commandGroupEnd(List<CommandExec> commands, int startIndex) {
    ItemDropRule.ExecutionTarget target = commands.get(startIndex).target;
    int endIndex = startIndex + 1;
    while (endIndex < commands.size() && commands.get(endIndex).target == target) {
      endIndex++;
    }
    return endIndex;
  }

  private void dispatchCommands(CommandSender sender, List<CommandExec> commands, int startIndex, int endIndex) {
    for (int index = startIndex; index < endIndex; index++) {
      Bukkit.dispatchCommand(sender, commands.get(index).command);
    }
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

  private BreakKey breakKey(Player player, Block block) {
    return new BreakKey(player.getUniqueId(), block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
  }

  private BreakKey breakKey(Player player, BlockState blockState) {
    return new BreakKey(player.getUniqueId(), blockState.getWorld().getUID(), blockState.getX(), blockState.getY(), blockState.getZ());
  }

  private record BreakKey(UUID playerId, UUID worldId, int x, int y, int z) {
  }

  private record BreakPreparation(HiddenOre.RuntimeState runtime, ItemStack tool) {
  }

  private record DropPreparation(HiddenOre.RuntimeState runtime, ItemStack tool, boolean trackedPlacement) {
  }

  static final class CommandExec {
    final String command;
    final ItemDropRule.ExecutionTarget target;

    CommandExec(String command, ItemDropRule.ExecutionTarget target) {
      this.command = command;
      this.target = target;
    }
  }
}
