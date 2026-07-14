package art.arcane.hiddenore.listeners;

import art.arcane.hiddenore.api.event.HiddenOreDropsEvent;
import art.arcane.hiddenore.rules.ItemDropRule;
import org.bukkit.Material;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MiningListenerContractTest {
  @Test
  public void blockBreakHandler_runsAtHighestAndIgnoresCancelledBreaks() throws Exception {
    Method handler = MiningListener.class.getMethod("onBlockBreak", BlockBreakEvent.class);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertEquals(EventPriority.HIGHEST, annotation.priority());
    assertTrue(annotation.ignoreCancelled());
  }

  @Test
  public void blockBreakFinalizer_runsAtMonitorAndObservesCancellation() throws Exception {
    Method finalizer = MiningListener.class.getMethod("onBlockBreakFinal", BlockBreakEvent.class);
    EventHandler annotation = finalizer.getAnnotation(EventHandler.class);

    assertEquals(EventPriority.MONITOR, annotation.priority());
    assertFalse(annotation.ignoreCancelled());
  }

  @Test
  public void blockDropPreparation_mutatesAtHighestBeforeFinalCancellationCheck() throws Exception {
    Method preparation = MiningListener.class.getMethod("prepareBlockDrop", BlockDropItemEvent.class);
    EventHandler annotation = preparation.getAnnotation(EventHandler.class);

    assertEquals(EventPriority.HIGHEST, annotation.priority());
    assertTrue(annotation.ignoreCancelled());
  }

  @Test
  public void blockDropCommit_runsAtMonitorAndObservesCancellation() throws Exception {
    Method commit = MiningListener.class.getMethod("commitBlockDrop", BlockDropItemEvent.class);
    EventHandler annotation = commit.getAnnotation(EventHandler.class);

    assertEquals(EventPriority.MONITOR, annotation.priority());
    assertFalse(annotation.ignoreCancelled());
  }

  @Test
  public void rewardEvent_isNotCancellableByIntegrations() {
    assertFalse(Cancellable.class.isAssignableFrom(HiddenOreDropsEvent.class));
  }

  @Test
  public void placedRewards_requireTrackingAndRestrictiveMode() {
    assertFalse(MiningListener.blocksHiddenRewards(false, false));
    assertFalse(MiningListener.blocksHiddenRewards(true, false));
    assertFalse(MiningListener.blocksHiddenRewards(true, true));
    assertTrue(MiningListener.blocksHiddenRewards(false, true));
  }

  @Test
  public void breakPreparation_isDiscardedWhenNoDropEventCanFollow() {
    assertFalse(MiningListener.shouldDiscardBreakPreparation(false, true, false));
    assertTrue(MiningListener.shouldDiscardBreakPreparation(true, true, false));
    assertTrue(MiningListener.shouldDiscardBreakPreparation(false, false, false));
    assertTrue(MiningListener.shouldDiscardBreakPreparation(false, true, true));
  }

  @Test
  public void commandGroups_preserveConfiguredTargetRuns() {
    List<MiningListener.CommandExec> commands = List.of(
        command("player-one", ItemDropRule.ExecutionTarget.PLAYER),
        command("console-one", ItemDropRule.ExecutionTarget.CONSOLE),
        command("console-two", ItemDropRule.ExecutionTarget.CONSOLE),
        command("player-two", ItemDropRule.ExecutionTarget.PLAYER),
        command("player-three", ItemDropRule.ExecutionTarget.PLAYER),
        command("console-three", ItemDropRule.ExecutionTarget.CONSOLE)
    );

    assertEquals(1, MiningListener.commandGroupEnd(commands, 0));
    assertEquals(3, MiningListener.commandGroupEnd(commands, 1));
    assertEquals(5, MiningListener.commandGroupEnd(commands, 3));
    assertEquals(6, MiningListener.commandGroupEnd(commands, 5));
  }

  @Test
  public void commandGroups_rejectAnEmptyStartBoundary() {
    assertThrows(IndexOutOfBoundsException.class, () -> MiningListener.commandGroupEnd(List.of(), 0));
  }

  @Test
  public void consoleSalvage_skipsPlayerGroupsAndKeepsConsoleOrder() {
    List<MiningListener.CommandExec> commands = List.of(
        command("player-one", ItemDropRule.ExecutionTarget.PLAYER),
        command("console-one", ItemDropRule.ExecutionTarget.CONSOLE),
        command("console-two", ItemDropRule.ExecutionTarget.CONSOLE),
        command("player-two", ItemDropRule.ExecutionTarget.PLAYER),
        command("player-three", ItemDropRule.ExecutionTarget.PLAYER),
        command("console-three", ItemDropRule.ExecutionTarget.CONSOLE)
    );

    MiningListener.ConsoleSalvage salvage = MiningListener.salvageConsoleCommands(commands, 0);

    assertEquals(2, salvage.skippedPlayerGroups());
    assertEquals(3, salvage.commands().size());
    assertEquals("console-one", salvage.commands().get(0).command);
    assertEquals("console-two", salvage.commands().get(1).command);
    assertEquals("console-three", salvage.commands().get(2).command);
  }

  @Test
  public void consoleSalvage_respectsStartIndexWithoutRerunningEarlierGroups() {
    List<MiningListener.CommandExec> commands = List.of(
        command("console-one", ItemDropRule.ExecutionTarget.CONSOLE),
        command("player-one", ItemDropRule.ExecutionTarget.PLAYER),
        command("console-two", ItemDropRule.ExecutionTarget.CONSOLE)
    );

    MiningListener.ConsoleSalvage salvage = MiningListener.salvageConsoleCommands(commands, 1);

    assertEquals(1, salvage.skippedPlayerGroups());
    assertEquals(1, salvage.commands().size());
    assertEquals("console-two", salvage.commands().get(0).command);
  }

  @Test
  public void consoleSalvage_returnsEmptyWhenOnlyPlayerGroupsRemain() {
    List<MiningListener.CommandExec> commands = List.of(
        command("player-one", ItemDropRule.ExecutionTarget.PLAYER),
        command("player-two", ItemDropRule.ExecutionTarget.PLAYER)
    );

    MiningListener.ConsoleSalvage salvage = MiningListener.salvageConsoleCommands(commands, 0);

    assertEquals(1, salvage.skippedPlayerGroups());
    assertTrue(salvage.commands().isEmpty());
  }

  @Test
  public void consoleSalvage_handlesExhaustedChain() {
    List<MiningListener.CommandExec> commands = List.of(
        command("player-one", ItemDropRule.ExecutionTarget.PLAYER)
    );

    MiningListener.ConsoleSalvage salvage = MiningListener.salvageConsoleCommands(commands, 1);

    assertEquals(0, salvage.skippedPlayerGroups());
    assertTrue(salvage.commands().isEmpty());
  }

  @Test
  public void experienceRoll_acceptsMaximumIntegerWithoutOverflow() {
    assertEquals(0, MiningListener.rollInclusiveExperience(0));
    assertEquals(0, MiningListener.rollInclusiveExperience(-1));
    for (int iteration = 0; iteration < 100; iteration++) {
      int rolled = MiningListener.rollInclusiveExperience(Integer.MAX_VALUE);
      assertTrue(rolled >= 0);
    }
  }

  @Test
  public void toolSnapshot_preservesFinalDurabilityBreakToolAfterOriginalStackIsEmptied() {
    ItemStack original = new TestItemStack(Material.DIAMOND_PICKAXE, 1);
    ItemStack snapshot = MiningListener.snapshotTool(original);

    original.setAmount(0);

    assertNotSame(original, snapshot);
    assertEquals(Material.DIAMOND_PICKAXE, snapshot.getType());
    assertEquals(1, snapshot.getAmount());
  }

  private static MiningListener.CommandExec command(String command, ItemDropRule.ExecutionTarget target) {
    return new MiningListener.CommandExec(command, target);
  }

  private static final class TestItemStack extends ItemStack {
    private final Material type;
    private int amount;

    private TestItemStack(Material type, int amount) {
      this.type = type;
      this.amount = amount;
    }

    @Override
    public Material getType() {
      return type;
    }

    @Override
    public int getAmount() {
      return amount;
    }

    @Override
    public void setAmount(int amount) {
      this.amount = amount;
    }

    @Override
    public ItemStack clone() {
      return new TestItemStack(type, amount);
    }
  }
}
