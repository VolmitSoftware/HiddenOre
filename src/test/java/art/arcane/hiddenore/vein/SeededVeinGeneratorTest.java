package art.arcane.hiddenore.vein;

import art.arcane.hiddenore.rules.ItemDropRule;
import art.arcane.hiddenore.util.project.ToolTier;
import art.arcane.volmlib.util.bukkit.ChunkPositionSet;
import org.bukkit.Material;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SeededVeinGeneratorTest {
  private static final int MIN_HEIGHT = -64;
  private static final int MAX_HEIGHT = 320;

  private static List<ItemDropRule> rules() {
    return List.of(
        new ItemDropRule(Material.COAL, 2.2, 5, 20, 0, 320, true, Set.of(ToolTier.WOODEN_PICKAXE), 2),
        new ItemDropRule(Material.RAW_IRON, 1.8, 4, 12, -64, 320, false, Set.of(ToolTier.STONE_PICKAXE), 0),
        new ItemDropRule(Material.DIAMOND, 0.5, 3, 8, -64, 16, true, Set.of(ToolTier.IRON_PICKAXE), 7)
    );
  }

  private static Map<Integer, Material> flatten(ChunkVeins veins) {
    Map<Integer, Material> result = new HashMap<>();
    for (VeinBlock block : veins.blocks()) {
      result.put(block.packedPosition(), block.rule().material);
    }
    return result;
  }

  @Test
  public void test_compute_sameInputs_produceIdenticalVeins() {
    for (int chunkX = -3; chunkX <= 3; chunkX++) {
      for (int chunkZ = -3; chunkZ <= 3; chunkZ++) {
        ChunkVeins first = SeededVeinGenerator.compute(1234567L, MIN_HEIGHT, MAX_HEIGHT, chunkX, chunkZ, rules());
        ChunkVeins second = SeededVeinGenerator.compute(1234567L, MIN_HEIGHT, MAX_HEIGHT, chunkX, chunkZ, rules());
        assertEquals(flatten(first), flatten(second));
      }
    }
  }

  @Test
  public void test_compute_blocksStayWithinChunkAndRuleYRange() {
    for (int chunkX = -5; chunkX <= 5; chunkX++) {
      for (int chunkZ = -5; chunkZ <= 5; chunkZ++) {
        ChunkVeins veins = SeededVeinGenerator.compute(987654321L, MIN_HEIGHT, MAX_HEIGHT, chunkX, chunkZ, rules());
        for (VeinBlock block : veins.blocks()) {
          int localX = ChunkPositionSet.unpackLocalX(block.packedPosition());
          int localZ = ChunkPositionSet.unpackLocalZ(block.packedPosition());
          int y = ChunkPositionSet.unpackY(block.packedPosition(), MIN_HEIGHT);
          assertTrue(localX >= 0 && localX <= 15);
          assertTrue(localZ >= 0 && localZ <= 15);
          assertTrue(y >= Math.max(block.rule().minY, MIN_HEIGHT));
          assertTrue(y <= Math.min(block.rule().maxY, MAX_HEIGHT - 1));
        }
      }
    }
  }

  @Test
  public void test_compute_veinSizesWithinConfiguredMaximum() {
    for (int chunkX = 0; chunkX < 10; chunkX++) {
      ChunkVeins veins = SeededVeinGenerator.compute(42L, MIN_HEIGHT, MAX_HEIGHT, chunkX, 0, rules());
      for (VeinBlock block : veins.blocks()) {
        int size = veins.positionsOf(block.veinId()).length;
        assertTrue(size >= 1);
        assertTrue(size <= block.rule().veinMaxSize);
      }
    }
  }

  @Test
  public void test_compute_differentSeeds_produceDifferentVeins() {
    boolean anyDifference = false;
    for (int chunkX = 0; chunkX < 20 && !anyDifference; chunkX++) {
      ChunkVeins first = SeededVeinGenerator.compute(1L, MIN_HEIGHT, MAX_HEIGHT, chunkX, 0, rules());
      ChunkVeins second = SeededVeinGenerator.compute(2L, MIN_HEIGHT, MAX_HEIGHT, chunkX, 0, rules());
      anyDifference = !flatten(first).equals(flatten(second));
    }
    assertTrue(anyDifference);
  }

  @Test
  public void test_compute_emptyRules_returnsEmptyVeins() {
    ChunkVeins veins = SeededVeinGenerator.compute(7L, MIN_HEIGHT, MAX_HEIGHT, 0, 0, List.of());
    assertTrue(veins.isEmpty());
    assertFalse(veins.blocks().iterator().hasNext());
  }
}
