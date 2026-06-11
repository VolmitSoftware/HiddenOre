package art.arcane.hiddenore.vein;

import java.util.Collection;
import java.util.Map;

public final class ChunkVeins {
  public static final ChunkVeins EMPTY = new ChunkVeins(Map.of(), Map.of());

  private final Map<Integer, VeinBlock> blocksByPosition;
  private final Map<Integer, int[]> positionsByVein;

  public ChunkVeins(Map<Integer, VeinBlock> blocksByPosition, Map<Integer, int[]> positionsByVein) {
    this.blocksByPosition = blocksByPosition;
    this.positionsByVein = positionsByVein;
  }

  public VeinBlock get(int packedPosition) {
    return blocksByPosition.get(packedPosition);
  }

  public Collection<VeinBlock> blocks() {
    return blocksByPosition.values();
  }

  public int[] positionsOf(int veinId) {
    int[] positions = positionsByVein.get(veinId);
    return positions == null ? new int[0] : positions;
  }

  public boolean isEmpty() {
    return blocksByPosition.isEmpty();
  }
}
