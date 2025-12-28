package com.volmit.hiddenore.generation;

import com.volmit.hiddenore.HiddenOre;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

import static com.volmit.hiddenore.generation.Blocks.ORES;
import static com.volmit.hiddenore.generation.Blocks.getReplacement;

public class GenerationRules extends BlockPopulator implements Listener {
    private volatile Map<String, Map<Material, Material>> worldExceptions = Map.of();
    private volatile Map<Material, Material> defaults = Map.of();
    private volatile boolean enabled;
    private final HiddenOre plugin;

    public GenerationRules(HiddenOre plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reload();
    }

    public void reload() {
        final ConfigurationSection config = plugin.getConfig().getConfigurationSection("ore-removal");
        if (config == null || !config.getBoolean("enabled", false)) {
            enabled = false;
            defaults = Map.of();
            worldExceptions = Map.of();
            return;
        }

        final ConfigurationSection exceptions = config.getConfigurationSection("exceptions");
        if (exceptions == null) {
            this.worldExceptions = Map.of();
        } else {
            final Map<String, Map<Material, Material>> worldExceptions = new HashMap<>();
            for (final String world : exceptions.getKeys(false)) {
                worldExceptions.put(world, parseReplacements(exceptions.getConfigurationSection(world)));
            }
            this.worldExceptions = Map.copyOf(worldExceptions);
        }

        defaults = parseReplacements(config.getConfigurationSection("global"));
        enabled = true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @NotNull
    @Unmodifiable
    private Map<Material, Material> parseReplacements(@Nullable final ConfigurationSection section) {
        if (section == null) return Map.of();

        final Map<Material, Material> replacements = new HashMap<>();
        final boolean base = section.getBoolean("default", false);
        for (Material type : ORES) {
            if (!section.getBoolean(type.name(), base))
                continue;
            replacements.put(type, getReplacement(type));
        }
        return Map.copyOf(replacements);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldLoad(@NotNull WorldInitEvent event) {
        event.getWorld().getPopulators().add(this);
    }

    @Override
    public void populate(final @NotNull WorldInfo world,
                         final @NotNull Random random,
                         final int chunkX,
                         final int chunkZ,
                         final @NotNull LimitedRegion region) {
        final var blocks = worldExceptions.getOrDefault(world.getName(), defaults);
        if (blocks.isEmpty()) return;

        final int buffer = region.getBuffer() >> 4;
        final int centerX = region.getCenterChunkX();
        final int centerZ = region.getCenterChunkZ();
        final int yMin = world.getMinHeight();
        final int yMax = world.getMaxHeight() - 1;

        final int xCenter = region.getCenterBlockX();
        final int zCenter = region.getCenterBlockZ();
        final int xMin = xCenter - region.getBuffer();
        final int zMin = zCenter - region.getBuffer();
        final int xMax = xCenter + region.getBuffer() + 16;
        final int zMax = zCenter + region.getBuffer() + 16;

        for (int cX = -buffer; cX <= buffer; cX++) {
            for (int cZ = -buffer; cZ <= buffer; cZ++) {
                final int bX = (cX + centerX) << 4;
                final int bZ = (cZ + centerZ) << 4;
                final int minX = Math.max(xMin, bX);
                final int maxX = Math.min(xMax, bX + 16);
                final int minZ = Math.max(zMin, bZ);
                final int maxZ = Math.min(zMax, bZ + 16);

                for (int y = yMax; y >= yMin; y--) {
                    for (int x = minX; x < maxX; x++) {
                        for (int z = minZ; z < maxZ; z++) {
                            final Material type = blocks.get(region.getType(x, y, z));
                            if (type == null) continue;
                            region.setType(x, y, z, type);
                        }
                    }
                }
            }
        }
    }
}
