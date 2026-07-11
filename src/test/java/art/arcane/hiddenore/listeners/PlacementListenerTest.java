package art.arcane.hiddenore.listeners;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlacementListenerTest {
  @Test
  public void blockPlaceHandler_tracksAcceptedPlacementsAtMonitor() throws Exception {
    Method handler = PlacementListener.class.getMethod("onBlockPlace", BlockPlaceEvent.class);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertEquals(EventPriority.MONITOR, annotation.priority());
    assertTrue(annotation.ignoreCancelled());
  }

  @Test
  public void managedDropPresence_controlsPlacementTracking() {
    assertTrue(PlacementListener.shouldTrackPlacement(Material.COBBLESTONE));
    assertFalse(PlacementListener.shouldTrackPlacement(null));
  }

  @Test
  public void noDropBreakHandler_cleansAtAcceptedMonitorPhase() throws Exception {
    Method handler = PlacementListener.class.getMethod("onBlockBreak", BlockBreakEvent.class);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertEquals(EventPriority.MONITOR, annotation.priority());
    assertTrue(annotation.ignoreCancelled());
    assertTrue(PlacementListener.shouldCleanupWithoutDropEvent(false, false));
    assertTrue(PlacementListener.shouldCleanupWithoutDropEvent(true, true));
    assertFalse(PlacementListener.shouldCleanupWithoutDropEvent(true, false));
  }

  @Test
  public void movedBlockDirection_extension_usesOperationDirectionForEveryAxis() {
    for (BlockFace operationDirection : pistonDirections()) {
      assertEquals(operationDirection, PlacementListener.movedBlockDirection(operationDirection, false));
    }
  }

  @Test
  public void movedBlockDirection_stickyRetraction_usesOppositeDirectionForEveryAxis() {
    for (BlockFace operationDirection : pistonDirections()) {
      assertEquals(operationDirection.getOppositeFace(), PlacementListener.movedBlockDirection(operationDirection, true));
    }
  }

  private static BlockFace[] pistonDirections() {
    return new BlockFace[]{
        BlockFace.NORTH,
        BlockFace.EAST,
        BlockFace.SOUTH,
        BlockFace.WEST,
        BlockFace.UP,
        BlockFace.DOWN
    };
  }
}
