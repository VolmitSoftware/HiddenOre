package com.volmit.hiddenore.rules;

import com.volmit.hiddenore.util.ToolTier;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;

public class ItemDropRule {
    public enum DropType { ITEM, COMMAND }

    public final DropType type;

    // ITEM fields
    public final Material material;

    // COMMAND fields
    public final List<String> commands;

    // Shared fields
    public final double chance;
    public final int minY;
    public final int maxY;
    public final boolean fortuneMultiplier;
    public final Set<ToolTier> toolTiers;
    public final int veinMaxSize;
    public final int expDrop;

    // Constructor for ITEM drop
    public ItemDropRule(Material material, double chance, int minY, int maxY, boolean fortuneMultiplier, Set<ToolTier> toolTiers, int veinMaxSize, int expDrop) {
        this.type = DropType.ITEM;
        this.material = material;
        this.commands = null;
        this.chance = chance;
        this.minY = minY;
        this.maxY = maxY;
        this.fortuneMultiplier = fortuneMultiplier;
        this.toolTiers = toolTiers;
        this.veinMaxSize = veinMaxSize;
        this.expDrop = expDrop;
    }

    // Constructor for COMMAND drop
    public ItemDropRule(List<String> commands, double chance, int minY, int maxY) {
        this.type = DropType.COMMAND;
        this.material = null;
        this.commands = commands;
        this.chance = chance;
        this.minY = minY;
        this.maxY = maxY;
        this.fortuneMultiplier = false; // commands are not affected by enchantments
        this.toolTiers = java.util.Collections.emptySet(); // not restricted by tool tier
        this.veinMaxSize = 0; // commands do not start veins
        this.expDrop = 0; // no xp for commands
    }
}