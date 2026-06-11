package art.arcane.hiddenore.api;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.vein.ChunkVeins;
import art.arcane.hiddenore.vein.VeinBlock;
import art.arcane.volmlib.util.bukkit.ChunkPositionSet;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

public final class HiddenOreAPI {
  private final HiddenOre plugin;

  public HiddenOreAPI(HiddenOre plugin) {
    this.plugin = plugin;
  }

  public boolean isManagedBase(Material material) {
    return plugin.getRuleManager().getGuaranteedDrop(material) != null;
  }

  public HiddenVein veinAt(Block block) {
    if (!isManagedBase(block.getType())) {
      return null;
    }
    World world = block.getWorld();
    int chunkX = block.getX() >> 4;
    int chunkZ = block.getZ() >> 4;
    ChunkVeins veins = plugin.getVeinGenerator().get(world, chunkX, chunkZ);
    if (veins.isEmpty()) {
      return null;
    }
    int packed = ChunkPositionSet.pack(block.getX() & 15, block.getY(), block.getZ() & 15, world.getMinHeight());
    VeinBlock veinBlock = veins.get(packed);
    if (veinBlock == null || plugin.getConsumedVeins().contains(block) || plugin.getPlacedBlocks().contains(block)) {
      return null;
    }
    return toVein(world, chunkX, chunkZ, veinBlock);
  }

  public List<HiddenVein> veinSiblings(Block block) {
    World world = block.getWorld();
    int chunkX = block.getX() >> 4;
    int chunkZ = block.getZ() >> 4;
    ChunkVeins veins = plugin.getVeinGenerator().get(world, chunkX, chunkZ);
    if (veins.isEmpty()) {
      return List.of();
    }
    int packed = ChunkPositionSet.pack(block.getX() & 15, block.getY(), block.getZ() & 15, world.getMinHeight());
    VeinBlock veinBlock = veins.get(packed);
    if (veinBlock == null) {
      return List.of();
    }
    int[] consumed = plugin.getConsumedVeins().snapshot(block.getChunk());
    int[] placed = plugin.getPlacedBlocks().snapshot(block.getChunk());
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
      if (!isManagedBase(world.getBlockAt(x, y, z).getType())) {
        continue;
      }
      Material item = veinBlock.rule().material;
      result.add(new HiddenVein(x, y, z, veinBlock.veinId(), item, HiddenVein.oreDisplayFor(item, y)));
    }
    return result;
  }

  public List<HiddenVein> veinsNear(Location center, int radius) {
    World world = center.getWorld();
    if (world == null || radius <= 0) {
      return List.of();
    }

    List<HiddenVein> result = new ArrayList<>();
    int centerX = center.getBlockX();
    int centerY = center.getBlockY();
    int centerZ = center.getBlockZ();
    int radiusSquared = radius * radius;
    int minChunkX = (centerX - radius) >> 4;
    int maxChunkX = (centerX + radius) >> 4;
    int minChunkZ = (centerZ - radius) >> 4;
    int maxChunkZ = (centerZ + radius) >> 4;
    int minHeight = world.getMinHeight();

    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          continue;
        }
        ChunkVeins veins = plugin.getVeinGenerator().get(world, chunkX, chunkZ);
        if (veins.isEmpty()) {
          continue;
        }
        int[] consumed = plugin.getConsumedVeins().snapshot(world.getChunkAt(chunkX, chunkZ));
        int[] placed = plugin.getPlacedBlocks().snapshot(world.getChunkAt(chunkX, chunkZ));
        for (VeinBlock veinBlock : veins.blocks()) {
          int x = (chunkX << 4) + ChunkPositionSet.unpackLocalX(veinBlock.packedPosition());
          int y = ChunkPositionSet.unpackY(veinBlock.packedPosition(), minHeight);
          int z = (chunkZ << 4) + ChunkPositionSet.unpackLocalZ(veinBlock.packedPosition());
          int dx = x - centerX;
          int dy = y - centerY;
          int dz = z - centerZ;
          if (dx * dx + dy * dy + dz * dz > radiusSquared) {
            continue;
          }
          if (ChunkPositionSet.contains(consumed, veinBlock.packedPosition())
              || ChunkPositionSet.contains(placed, veinBlock.packedPosition())) {
            continue;
          }
          if (!isManagedBase(world.getBlockAt(x, y, z).getType())) {
            continue;
          }
          result.add(toVein(world, chunkX, chunkZ, veinBlock));
        }
      }
    }
    return result;
  }

  private HiddenVein toVein(World world, int chunkX, int chunkZ, VeinBlock veinBlock) {
    int minHeight = world.getMinHeight();
    int x = (chunkX << 4) + ChunkPositionSet.unpackLocalX(veinBlock.packedPosition());
    int y = ChunkPositionSet.unpackY(veinBlock.packedPosition(), minHeight);
    int z = (chunkZ << 4) + ChunkPositionSet.unpackLocalZ(veinBlock.packedPosition());
    Material item = veinBlock.rule().material;
    return new HiddenVein(x, y, z, veinBlock.veinId(), item, HiddenVein.oreDisplayFor(item, y));
  }
}
