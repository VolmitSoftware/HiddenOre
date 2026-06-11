package art.arcane.hiddenore.vein;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.rules.ItemDropRule;
import art.arcane.volmlib.util.bukkit.ChunkPositionSet;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class SeededVeinGenerator {
  private static final int CACHE_LIMIT = 4096;
  private static final int[][] WALK_DIRECTIONS = {
      {1, 0, 0}, {-1, 0, 0},
      {0, 1, 0}, {0, -1, 0},
      {0, 0, 1}, {0, 0, -1}
  };

  private final HiddenOre plugin;
  private final Map<UUID, Map<Long, ChunkVeins>> cache = new HashMap<>();

  public SeededVeinGenerator(HiddenOre plugin) {
    this.plugin = plugin;
  }

  public ChunkVeins get(World world, int chunkX, int chunkZ) {
    long chunkKey = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    synchronized (cache) {
      Map<Long, ChunkVeins> worldCache = cache.computeIfAbsent(world.getUID(), ignored ->
          new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ChunkVeins> eldest) {
              return size() > CACHE_LIMIT;
            }
          });
      ChunkVeins cached = worldCache.get(chunkKey);
      if (cached != null) {
        return cached;
      }
      ChunkVeins computed = compute(world.getSeed(), world.getMinHeight(), world.getMaxHeight(), chunkX, chunkZ, plugin.getRuleManager().getAllDropRules());
      worldCache.put(chunkKey, computed);
      return computed;
    }
  }

  public static ChunkVeins compute(long worldSeed, int minHeight, int maxHeight, int chunkX, int chunkZ, List<ItemDropRule> rules) {
    Map<Integer, VeinBlock> blocksByPosition = new HashMap<>();
    Map<Integer, int[]> positionsByVein = new HashMap<>();
    int nextVeinId = 0;

    for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
      ItemDropRule rule = rules.get(ruleIndex);
      if (rule.type != ItemDropRule.DropType.ITEM || rule.veinsPerChunk <= 0.0) {
        continue;
      }

      int yLow = Math.max(rule.minY, minHeight);
      int yHigh = Math.min(rule.maxY, maxHeight - 1);
      if (yLow > yHigh) {
        continue;
      }

      Random rng = new Random(mix(worldSeed, chunkX, chunkZ, ruleIndex));
      int veinCount = (int) rule.veinsPerChunk;
      double fraction = rule.veinsPerChunk - veinCount;
      if (rng.nextDouble() < fraction) {
        veinCount++;
      }

      for (int vein = 0; vein < veinCount; vein++) {
        int size = rule.veinMinSize + (rule.veinMaxSize > rule.veinMinSize ? rng.nextInt(rule.veinMaxSize - rule.veinMinSize + 1) : 0);
        int x = rng.nextInt(16);
        int z = rng.nextInt(16);
        int y = yLow + rng.nextInt(yHigh - yLow + 1);

        int veinId = nextVeinId++;
        List<Integer> positions = new ArrayList<>(size);
        claim(blocksByPosition, positions, rule, veinId, x, y, z, minHeight);

        int attempts = size * 4;
        while (positions.size() < size && attempts-- > 0) {
          int[] direction = WALK_DIRECTIONS[rng.nextInt(WALK_DIRECTIONS.length)];
          int nx = x + direction[0];
          int ny = y + direction[1];
          int nz = z + direction[2];
          if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || ny < yLow || ny > yHigh) {
            continue;
          }
          x = nx;
          y = ny;
          z = nz;
          claim(blocksByPosition, positions, rule, veinId, x, y, z, minHeight);
        }

        if (positions.isEmpty()) {
          continue;
        }
        int[] packed = new int[positions.size()];
        for (int i = 0; i < packed.length; i++) {
          packed[i] = positions.get(i);
        }
        positionsByVein.put(veinId, packed);
      }
    }

    if (blocksByPosition.isEmpty()) {
      return ChunkVeins.EMPTY;
    }
    return new ChunkVeins(blocksByPosition, positionsByVein);
  }

  private static void claim(Map<Integer, VeinBlock> blocksByPosition, List<Integer> positions, ItemDropRule rule, int veinId, int x, int y, int z, int minHeight) {
    int packed = ChunkPositionSet.pack(x, y, z, minHeight);
    if (blocksByPosition.containsKey(packed)) {
      return;
    }
    blocksByPosition.put(packed, new VeinBlock(packed, veinId, rule));
    positions.add(packed);
  }

  private static long mix(long worldSeed, int chunkX, int chunkZ, int ruleIndex) {
    long hash = worldSeed;
    hash ^= chunkX * 0x9E3779B97F4A7C15L;
    hash = splitmix(hash);
    hash ^= chunkZ * 0xC2B2AE3D27D4EB4FL;
    hash = splitmix(hash);
    hash ^= ruleIndex * 0xD6E8FEB86659FD93L;
    return splitmix(hash);
  }

  private static long splitmix(long value) {
    long result = value + 0x9E3779B97F4A7C15L;
    result = (result ^ (result >>> 30)) * 0xBF58476D1CE4E5B9L;
    result = (result ^ (result >>> 27)) * 0x94D049BB133111EBL;
    return result ^ (result >>> 31);
  }
}
