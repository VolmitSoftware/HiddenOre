package art.arcane.hiddenore.util.project;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MiningUtilTest {
  @Test
  public void applyFortuneRoll_zeroAndOnePreserveBaseAmount() {
    assertEquals(2, MiningUtil.applyFortuneRoll(2, 0));
    assertEquals(2, MiningUtil.applyFortuneRoll(2, 1));
  }

  @Test
  public void applyFortuneRoll_positiveRollMultipliesBaseAmount() {
    assertEquals(6, MiningUtil.applyFortuneRoll(2, 3));
  }

  @Test
  public void applyFortuneRoll_negativeBoundaryDoesNotReduceDrops() {
    assertEquals(2, MiningUtil.applyFortuneRoll(2, -1));
  }
}
