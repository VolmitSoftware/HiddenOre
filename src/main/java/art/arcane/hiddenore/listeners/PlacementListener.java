package art.arcane.hiddenore.listeners;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.volmlib.util.bukkit.ChunkPositionSet;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.List;

public class PlacementListener implements Listener {
  private final HiddenOre plugin;

  public PlacementListener(HiddenOre plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockPlace(BlockPlaceEvent event) {
    if (plugin.getRuleManager().getVeinConfig().allowPlacedBlocks) {
      return;
    }
    Block block = event.getBlockPlaced();
    if (plugin.getRuleManager().getGuaranteedDrop(block.getType()) != null) {
      plugin.getPlacedBlocks().add(block);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPistonExtend(BlockPistonExtendEvent event) {
    shiftTracked(event.getBlocks(), event.getDirection());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPistonRetract(BlockPistonRetractEvent event) {
    shiftTracked(event.getBlocks(), event.getDirection());
  }

  private void shiftTracked(List<Block> moved, BlockFace direction) {
    if (moved.isEmpty()) {
      return;
    }
    ChunkPositionSet placed = plugin.getPlacedBlocks();
    List<Block> tracked = new ArrayList<>(0);
    for (Block block : moved) {
      if (placed.remove(block)) {
        tracked.add(block);
      }
    }
    for (Block block : tracked) {
      placed.add(block.getRelative(direction));
    }
  }
}
