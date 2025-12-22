package com.volmit.hiddenore.generation;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Blocks {
    public static final List<Material> ORES = List.of(
            Material.COAL_ORE,
            Material.IRON_ORE,
            Material.COPPER_ORE,
            Material.GOLD_ORE,
            Material.DIAMOND_ORE,
            Material.REDSTONE_ORE,
            Material.LAPIS_ORE,
            Material.EMERALD_ORE,

            Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_IRON_ORE,
            Material.DEEPSLATE_COPPER_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.DEEPSLATE_REDSTONE_ORE,
            Material.DEEPSLATE_LAPIS_ORE,
            Material.DEEPSLATE_EMERALD_ORE,

            Material.NETHER_GOLD_ORE,
            Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS
    );

    @NotNull
    public static Material getReplacement(@NotNull Material type) {
        return switch (type) {
            case COAL_ORE, IRON_ORE, COPPER_ORE, GOLD_ORE, DIAMOND_ORE, REDSTONE_ORE, LAPIS_ORE, EMERALD_ORE -> Material.STONE;
            case DEEPSLATE_COAL_ORE, DEEPSLATE_IRON_ORE, DEEPSLATE_COPPER_ORE, DEEPSLATE_GOLD_ORE, DEEPSLATE_DIAMOND_ORE, DEEPSLATE_REDSTONE_ORE, DEEPSLATE_LAPIS_ORE, DEEPSLATE_EMERALD_ORE -> Material.DEEPSLATE;
            case NETHER_GOLD_ORE, NETHER_QUARTZ_ORE, ANCIENT_DEBRIS -> Material.NETHERRACK;
            default -> throw new IllegalArgumentException("Unknown material: " + type);
        };
    }
}
