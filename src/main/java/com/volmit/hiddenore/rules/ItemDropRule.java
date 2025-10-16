package com.volmit.hiddenore.rules;

import com.volmit.hiddenore.util.ToolTier;
import org.bukkit.Material;

import java.util.Set;

public class ItemDropRule {
    public final Material material;
    public final double chance;
    public final int minY;
    public final int maxY;
    public final boolean fortuneMultiplier;
    public final Set<ToolTier> toolTiers;
    public final int veinMaxSize;
    public final int expDrop;

    public ItemDropRule(Material material, double chance, int minY, int maxY, boolean fortuneMultiplier, Set<ToolTier> toolTiers, int veinMaxSize, int expDrop) {
        this.material = material;
        this.chance = chance;
        this.minY = minY;
        this.maxY = maxY;
        this.fortuneMultiplier = fortuneMultiplier;
        this.toolTiers = toolTiers;
        this.veinMaxSize = veinMaxSize;
        this.expDrop = expDrop;
    }
}