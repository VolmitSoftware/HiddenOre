package art.arcane.hiddenore.api;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.rules.MiningRuleManager;
import art.arcane.hiddenore.service.HiddenOreTelemetry;
import art.arcane.hiddenore.vein.ChunkVeins;
import art.arcane.hiddenore.vein.VeinBlock;
import art.arcane.hiddenore.vein.VeinConfig;
import art.arcane.volmlib.util.bukkit.ChunkPositionSet;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

public final class HiddenOreAPI {
  public static final int MAX_NEARBY_RADIUS = 128;
  public static final int MAX_NEARBY_RESULTS = 4096;
  private static final int[] EMPTY_POSITIONS = new int[0];

  private final HiddenOre plugin;

  public HiddenOreAPI(HiddenOre plugin) {
    this.plugin = plugin;
  }

  public boolean isManagedBase(Material material) {
    return plugin.getRuntimeState().ruleManager().getGuaranteedDrop(material) != null;
  }

  public boolean isSeeded() {
    return plugin.getRuntimeState().ruleManager().getVeinConfig().generation == VeinConfig.GenerationMode.SEEDED;
  }

  public HiddenVein veinAt(Block block) {
    if (plugin.isDraining()) {
      return null;
    }
    HiddenOre.RuntimeState runtime = plugin.getRuntimeState();
    MiningRuleManager rules = runtime.ruleManager();
    if (!isSeeded(rules)) {
      return null;
    }
    World world = block.getWorld();
    int chunkX = block.getX() >> 4;
    int chunkZ = block.getZ() >> 4;
    requireOwnedChunk(world, chunkX, chunkZ);
    if (!isManagedBase(block.getType(), rules)) {
      return null;
    }
    ChunkVeins veins = runtime.veinGenerator().get(world, chunkX, chunkZ);
    if (veins.isEmpty()) {
      return null;
    }
    int packed = ChunkPositionSet.pack(block.getX() & 15, block.getY(), block.getZ() & 15, world.getMinHeight());
    VeinBlock veinBlock = veins.get(packed);
    if (veinBlock == null) {
      return null;
    }
    HiddenOreTelemetry.countPdcRead();
    if (plugin.getConsumedVeins().contains(block) || isBlockedPlacement(block, rules)) {
      return null;
    }
    return toVein(world, chunkX, chunkZ, veinBlock);
  }

  public List<HiddenVein> veinSiblings(Block block) {
    if (plugin.isDraining()) {
      return List.of();
    }
    HiddenOre.RuntimeState runtime = plugin.getRuntimeState();
    MiningRuleManager rules = runtime.ruleManager();
    if (!isSeeded(rules)) {
      return List.of();
    }
    World world = block.getWorld();
    int chunkX = block.getX() >> 4;
    int chunkZ = block.getZ() >> 4;
    requireOwnedChunk(world, chunkX, chunkZ);
    ChunkVeins veins = runtime.veinGenerator().get(world, chunkX, chunkZ);
    if (veins.isEmpty()) {
      return List.of();
    }
    int packed = ChunkPositionSet.pack(block.getX() & 15, block.getY(), block.getZ() & 15, world.getMinHeight());
    VeinBlock veinBlock = veins.get(packed);
    if (veinBlock == null) {
      return List.of();
    }
    HiddenOreTelemetry.countPdcRead();
    int[] consumed = plugin.getConsumedVeins().snapshot(block.getChunk());
    int[] placed;
    if (rules.getVeinConfig().allowPlacedBlocks) {
      placed = EMPTY_POSITIONS;
    } else {
      HiddenOreTelemetry.countPdcRead();
      placed = plugin.getPlacedBlocks().snapshot(block.getChunk());
    }
    List<HiddenVein> result = new ArrayList<>();
    int minHeight = world.getMinHeight();
    for (int position : veins.positionsOf(veinBlock.veinId())) {
      if (position == packed
          || ChunkPositionSet.contains(consumed, position)
          || ChunkPositionSet.contains(placed, position)) {
        continue;
      }
      int x = (chunkX << 4) + ChunkPositionSet.unpackLocalX(position);
      int y = ChunkPositionSet.unpackY(position, minHeight);
      int z = (chunkZ << 4) + ChunkPositionSet.unpackLocalZ(position);
      if (!isManagedBase(world.getBlockAt(x, y, z).getType(), rules)) {
        continue;
      }
      Material item = veinBlock.rule().material;
      result.add(new HiddenVein(x, y, z, veinBlock.veinId(), item, HiddenVein.oreDisplayFor(item, y)));
    }
    return List.copyOf(result);
  }

  public List<HiddenVein> veinsNear(Location center, int radius) {
    if (plugin.isDraining()) {
      return List.of();
    }
    HiddenOre.RuntimeState runtime = plugin.getRuntimeState();
    MiningRuleManager rules = runtime.ruleManager();
    World world = center.getWorld();
    if (!isSeeded(rules) || world == null || radius <= 0) {
      return List.of();
    }
    if (radius > MAX_NEARBY_RADIUS) {
      throw new IllegalArgumentException("HiddenOre nearby radius cannot exceed " + MAX_NEARBY_RADIUS);
    }

    List<HiddenVein> result = new ArrayList<>();
    int centerX = center.getBlockX();
    int centerY = center.getBlockY();
    int centerZ = center.getBlockZ();
    long radiusSquared = (long) radius * radius;
    int minChunkX = chunkAtOffset(centerX, -radius);
    int maxChunkX = chunkAtOffset(centerX, radius);
    int minChunkZ = chunkAtOffset(centerZ, -radius);
    int maxChunkZ = chunkAtOffset(centerZ, radius);
    int minHeight = world.getMinHeight();

    nearbyChunks:
    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        if (!ownsChunk(world, chunkX, chunkZ)) {
          continue;
        }
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          continue;
        }
        ChunkVeins veins = runtime.veinGenerator().get(world, chunkX, chunkZ);
        if (veins.isEmpty()) {
          continue;
        }
        HiddenOreTelemetry.countPdcRead();
        int[] consumed = plugin.getConsumedVeins().snapshot(world.getChunkAt(chunkX, chunkZ));
        int[] placed;
        if (rules.getVeinConfig().allowPlacedBlocks) {
          placed = EMPTY_POSITIONS;
        } else {
          HiddenOreTelemetry.countPdcRead();
          placed = plugin.getPlacedBlocks().snapshot(world.getChunkAt(chunkX, chunkZ));
        }
        for (VeinBlock veinBlock : veins.blocks()) {
          int x = (chunkX << 4) + ChunkPositionSet.unpackLocalX(veinBlock.packedPosition());
          int y = ChunkPositionSet.unpackY(veinBlock.packedPosition(), minHeight);
          int z = (chunkZ << 4) + ChunkPositionSet.unpackLocalZ(veinBlock.packedPosition());
          long dx = (long) x - centerX;
          long dy = (long) y - centerY;
          long dz = (long) z - centerZ;
          if (dx * dx + dy * dy + dz * dz > radiusSquared) {
            continue;
          }
          if (ChunkPositionSet.contains(consumed, veinBlock.packedPosition())
              || ChunkPositionSet.contains(placed, veinBlock.packedPosition())) {
            continue;
          }
          if (!isManagedBase(world.getBlockAt(x, y, z).getType(), rules)) {
            continue;
          }
          result.add(toVein(world, chunkX, chunkZ, veinBlock));
          if (result.size() >= MAX_NEARBY_RESULTS) {
            break nearbyChunks;
          }
        }
      }
    }
    return List.copyOf(result);
  }

  private HiddenVein toVein(World world, int chunkX, int chunkZ, VeinBlock veinBlock) {
    int minHeight = world.getMinHeight();
    int x = (chunkX << 4) + ChunkPositionSet.unpackLocalX(veinBlock.packedPosition());
    int y = ChunkPositionSet.unpackY(veinBlock.packedPosition(), minHeight);
    int z = (chunkZ << 4) + ChunkPositionSet.unpackLocalZ(veinBlock.packedPosition());
    Material item = veinBlock.rule().material;
    return new HiddenVein(x, y, z, veinBlock.veinId(), item, HiddenVein.oreDisplayFor(item, y));
  }

  private boolean isSeeded(MiningRuleManager rules) {
    return rules.getVeinConfig().generation == VeinConfig.GenerationMode.SEEDED;
  }

  private boolean isManagedBase(Material material, MiningRuleManager rules) {
    return rules.getGuaranteedDrop(material) != null;
  }

  private boolean isBlockedPlacement(Block block, MiningRuleManager rules) {
    if (rules.getVeinConfig().allowPlacedBlocks) {
      return false;
    }
    HiddenOreTelemetry.countPdcRead();
    return plugin.getPlacedBlocks().contains(block);
  }

  private void requireOwnedChunk(World world, int chunkX, int chunkZ) {
    if (!ownsChunk(world, chunkX, chunkZ)) {
      throw new IllegalStateException("HiddenOre API access requires the owning world region thread");
    }
  }

  private static boolean ownsChunk(World world, int chunkX, int chunkZ) {
    return FoliaScheduler.isOwnedByCurrentRegion(world, chunkX, chunkZ);
  }

  private static int chunkAtOffset(int coordinate, int offset) {
    long value = (long) coordinate + offset;
    long clamped = Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    return ((int) clamped) >> 4;
  }
}
