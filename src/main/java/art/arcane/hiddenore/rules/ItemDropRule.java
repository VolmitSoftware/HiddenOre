package art.arcane.hiddenore.rules;

import art.arcane.hiddenore.util.project.ToolTier;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ItemDropRule {
  public final DropType type;
  public final Material material;
  public final List<String> commands;
  public final ExecutionTarget executionTarget;
  public final double chance;
  public final int minY;
  public final int maxY;
  public final boolean fortuneMultiplier;
  public final Set<ToolTier> toolTiers;
  public final double veinsPerChunk;
  public final int veinMinSize;
  public final int veinMaxSize;
  public final int expDrop;

  public ItemDropRule(Material material, double veinsPerChunk, int veinMinSize, int veinMaxSize, int minY, int maxY, boolean fortuneMultiplier, Set<ToolTier> toolTiers, int expDrop) {
    this.type = DropType.ITEM;
    this.material = material;
    this.commands = null;
    this.executionTarget = ExecutionTarget.CONSOLE;
    this.chance = 0.0;
    this.minY = minY;
    this.maxY = maxY;
    this.fortuneMultiplier = fortuneMultiplier;
    this.toolTiers = toolTiers;
    this.veinsPerChunk = veinsPerChunk;
    this.veinMinSize = Math.max(1, veinMinSize);
    this.veinMaxSize = Math.max(this.veinMinSize, veinMaxSize);
    this.expDrop = expDrop;
  }

  public ItemDropRule(List<String> commands, double chance, int minY, int maxY, ExecutionTarget executionTarget) {
    this.type = DropType.COMMAND;
    this.material = null;
    this.commands = commands;
    this.executionTarget = executionTarget != null ? executionTarget : ExecutionTarget.CONSOLE;
    this.chance = chance;
    this.minY = minY;
    this.maxY = maxY;
    this.fortuneMultiplier = false;
    this.toolTiers = Collections.emptySet();
    this.veinsPerChunk = 0.0;
    this.veinMinSize = 0;
    this.veinMaxSize = 0;
    this.expDrop = 0;
  }

  public double pureRandomChance() {
    if (type != DropType.ITEM || veinsPerChunk <= 0) {
      return 0.0;
    }
    int yRange = maxY - minY + 1;
    if (yRange <= 0) {
      return 0.0;
    }
    double expectedBlocksPerChunk = veinsPerChunk * (veinMinSize + veinMaxSize) / 2.0;
    return expectedBlocksPerChunk / (256.0 * yRange);
  }

  public enum DropType {ITEM, COMMAND}

  public enum ExecutionTarget {CONSOLE, PLAYER}
}
