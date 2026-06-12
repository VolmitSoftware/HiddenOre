package art.arcane.hiddenore.rules;

import org.bukkit.Material;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

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
}
