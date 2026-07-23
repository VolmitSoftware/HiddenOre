package art.arcane.hiddenore.listeners;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.rules.MiningRuleManager;
import art.arcane.hiddenore.service.HiddenOreTelemetry;
import art.arcane.volmlib.util.bukkit.ChunkPositionSet;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.List;

public final class PlacementListener implements Listener {
  private final HiddenOre plugin;

  public PlacementListener(HiddenOre plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockPlace(BlockPlaceEvent event) {
    if (plugin.isDraining()) {
      return;
    }

    MiningRuleManager rules = plugin.getRuntimeState().ruleManager();
    Block block = event.getBlockPlaced();
    if (shouldTrackPlacement(rules.getGuaranteedDrop(block.getType()))) {
      HiddenOreTelemetry.countPdcWrite();
      plugin.getPlacedBlocks().add(block);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    if (!shouldCleanupWithoutDropEvent(event.isDropItems(), event.getBlock().getType().isAir())) {
      return;
    }
    HiddenOreTelemetry.countPdcWrite();
    plugin.getPlacedBlocks().remove(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPistonExtend(BlockPistonExtendEvent event) {
    if (plugin.isDraining()) {
      return;
    }
    shiftTracked(event.getBlocks(), movedBlockDirection(event.getDirection(), false));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPistonRetract(BlockPistonRetractEvent event) {
    if (plugin.isDraining()) {
      return;
    }
    shiftTracked(event.getBlocks(), movedBlockDirection(event.getDirection(), true));
  }

  static boolean shouldTrackPlacement(Material guaranteedDrop) {
    return guaranteedDrop != null;
  }

  static boolean shouldCleanupWithoutDropEvent(boolean dropItems, boolean blockAir) {
    return !dropItems || blockAir;
  }

  static BlockFace movedBlockDirection(BlockFace operationDirection, boolean retracting) {
    return retracting ? operationDirection.getOppositeFace() : operationDirection;
  }

  private void shiftTracked(List<Block> moved, BlockFace direction) {
    if (moved.isEmpty()) {
      return;
    }
    ChunkPositionSet placed = plugin.getPlacedBlocks();
    List<Block> tracked = new ArrayList<>(moved.size());
    for (Block block : moved) {
      HiddenOreTelemetry.countPdcWrite();
      if (placed.remove(block)) {
        tracked.add(block);
      }
    }
    for (Block block : tracked) {
      HiddenOreTelemetry.countPdcWrite();
      placed.add(block.getRelative(direction));
    }
  }
}
