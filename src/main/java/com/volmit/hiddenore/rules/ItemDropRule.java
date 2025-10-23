package com.volmit.hiddenore.rules;

import com.volmit.hiddenore.util.ToolTier;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ItemDropRule {
    public enum DropType { ITEM, COMMAND }

    public enum ExecutionTarget { CONSOLE, PLAYER }

    public final DropType type;

    public final Material material;

    public final List<String> commands;
    public final ExecutionTarget executionTarget; // for command drops: CONSOLE or PLAYER

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
        this.executionTarget = ExecutionTarget.CONSOLE;
        this.chance = chance;
        this.minY = minY;
        this.maxY = maxY;
        this.fortuneMultiplier = fortuneMultiplier;
        this.toolTiers = toolTiers;
        this.veinMaxSize = veinMaxSize;
        this.expDrop = expDrop;
    }

    public ItemDropRule(List<String> commands, double chance, int minY, int maxY) {
        this(commands, chance, minY, maxY, ExecutionTarget.CONSOLE);
    }

    public ItemDropRule(List<String> commands, double chance, int minY, int maxY, ExecutionTarget executionTarget) {
        this.type = DropType.COMMAND;
        this.material = null;
        this.commands = commands;
        this.executionTarget = executionTarget != null ? executionTarget : ExecutionTarget.CONSOLE;
        this.chance = chance;
        this.minY = minY;
        this.maxY = maxY;
        this.fortuneMultiplier = false; // commands are not affected by enchantments
        this.toolTiers = Collections.emptySet(); // not restricted by tool tier by default
        this.veinMaxSize = 0; // commands do not start veins
        this.expDrop = 0; // no xp for commands
    }
}