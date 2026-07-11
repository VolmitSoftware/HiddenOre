package art.arcane.hiddenore.rules;

import art.arcane.hiddenore.util.project.ToolTier;
import org.bukkit.Material;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ItemDropRuleTest {
  @Test
  public void pureRandomChance_itemRule_matchesExpectedDensity() {
    ItemDropRule rule = new ItemDropRule(Material.COAL, 2.2, 5, 20, 0, 320, true, Set.of(), 2);
    double expectedBlocksPerChunk = 2.2 * (5 + 20) / 2.0;
    double expected = expectedBlocksPerChunk / (256.0 * 321.0);
    assertEquals(expected, rule.pureRandomChance(), 1e-12);
  }

  @Test
  public void pureRandomChance_singleBlockVeins_matchesVeinsPerChunk() {
    ItemDropRule rule = new ItemDropRule(Material.EMERALD, 0.35, 1, 1, 0, 0, true, Set.of(), 7);
    assertEquals(0.35 / 256.0, rule.pureRandomChance(), 1e-12);
  }

  @Test
  public void pureRandomChance_commandRule_isZero() {
    ItemDropRule rule = new ItemDropRule(List.of("say hi"), 0.5, -64, 320, ItemDropRule.ExecutionTarget.CONSOLE);
    assertEquals(0.0, rule.pureRandomChance(), 0.0);
  }

  @Test
  public void pureRandomChance_zeroVeinsPerChunk_isZero() {
    ItemDropRule rule = new ItemDropRule(Material.COAL, 0.0, 5, 20, 0, 320, true, Set.of(), 2);
    assertEquals(0.0, rule.pureRandomChance(), 0.0);
  }

  @Test
  public void pureRandomChance_extremeIntegerRanges_doNotOverflow() {
    ItemDropRule rule = new ItemDropRule(Material.COAL, 1.0, Integer.MAX_VALUE, Integer.MAX_VALUE,
        Integer.MIN_VALUE, Integer.MAX_VALUE, true, Set.of(), 2);
    double expected = Integer.MAX_VALUE / (256.0 * 4294967296.0);
    assertEquals(expected, rule.pureRandomChance(), 1e-12);
  }

  @Test
  public void constructors_captureImmutableCollections() {
    Set<ToolTier> tiers = new HashSet<>();
    tiers.add(ToolTier.IRON_PICKAXE);
    ItemDropRule itemRule = new ItemDropRule(Material.DIAMOND, 0.5, 1, 2, -64, 16, true, tiers, 7);
    List<String> commands = new ArrayList<>(List.of("say hi"));
    ItemDropRule commandRule = new ItemDropRule(commands, 0.5, -64, 16, ItemDropRule.ExecutionTarget.CONSOLE);

    tiers.clear();
    commands.clear();

    assertEquals(Set.of(ToolTier.IRON_PICKAXE), itemRule.toolTiers);
    assertEquals(List.of("say hi"), commandRule.commands);
    assertThrows(UnsupportedOperationException.class, () -> itemRule.toolTiers.clear());
    assertThrows(UnsupportedOperationException.class, () -> commandRule.commands.clear());
  }
}
