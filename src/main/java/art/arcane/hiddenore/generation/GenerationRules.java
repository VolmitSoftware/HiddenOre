package art.arcane.hiddenore.generation;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.service.HiddenOreTelemetry;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Level;

import static art.arcane.hiddenore.generation.Blocks.ORES;
import static art.arcane.hiddenore.generation.Blocks.getReplacement;

public final class GenerationRules extends BlockPopulator implements Listener {
  private static final GenerationPolicy DISABLED_POLICY = new GenerationPolicy(false, Map.of(), Map.of());

  private final HiddenOre plugin;
  private volatile boolean started;
  private volatile boolean closed;

  public GenerationRules(HiddenOre plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  public synchronized void start() {
    if (closed) {
      throw new IllegalStateException("Generation rules are closed");
    }
    if (started) {
      return;
    }

    started = true;
    try {
      Bukkit.getPluginManager().registerEvents(this, plugin);
      attachToLoadedWorlds();
    } catch (RuntimeException exception) {
      close();
      throw exception;
    }
  }

  public boolean isEnabled() {
    return !closed && plugin.getRuntimeState().generationPolicy().enabled();
  }

  public synchronized void close() {
    if (closed) {
      return;
    }

    closed = true;
    started = false;
    HandlerList.unregisterAll(this);
    detachFromLoadedWorlds();
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onWorldLoad(@NotNull WorldInitEvent event) {
    attachToWorld(event.getWorld());
  }

  @Override
  public void populate(@NotNull WorldInfo world,
                       @NotNull Random random,
                       int chunkX,
                       int chunkZ,
                       @NotNull LimitedRegion region) {
    if (closed || !started) {
      return;
    }

    GenerationPolicy activePolicy = plugin.getRuntimeState().generationPolicy();
    if (!activePolicy.enabled()) {
      return;
    }

    Map<Material, Material> blocks = activePolicy.worldExceptions().getOrDefault(WorldIdentity.serialize(world), activePolicy.defaults());
    if (blocks.isEmpty()) {
      return;
    }

    int buffer = region.getBuffer() >> 4;
    int centerX = region.getCenterChunkX();
    int centerZ = region.getCenterChunkZ();
    int yMin = world.getMinHeight();
    int yMax = world.getMaxHeight() - 1;

    int xCenter = region.getCenterBlockX();
    int zCenter = region.getCenterBlockZ();
    int xMin = xCenter - region.getBuffer();
    int zMin = zCenter - region.getBuffer();
    int xMax = xCenter + region.getBuffer() + 16;
    int zMax = zCenter + region.getBuffer() + 16;

    long replaced = 0L;
    for (int cX = -buffer; cX <= buffer; cX++) {
      for (int cZ = -buffer; cZ <= buffer; cZ++) {
        int bX = (cX + centerX) << 4;
        int bZ = (cZ + centerZ) << 4;
        int minX = Math.max(xMin, bX);
        int maxX = Math.min(xMax, bX + 16);
        int minZ = Math.max(zMin, bZ);
        int maxZ = Math.min(zMax, bZ + 16);

        for (int y = yMax; y >= yMin; y--) {
          for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
              Material type = blocks.get(region.getType(x, y, z));
              if (type != null) {
                region.setType(x, y, z, type);
                replaced++;
              }
            }
          }
        }
      }
    }
    HiddenOreTelemetry.addOreRemovalBlocks(replaced);
  }

  public static GenerationPolicy parsePolicy(FileConfiguration configuration) {
    FileConfiguration config = Objects.requireNonNull(configuration, "configuration");
    Object rawPolicy = config.get("ore-removal");
    if (rawPolicy == null) {
      return DISABLED_POLICY;
    }

    ConfigurationSection section = config.getConfigurationSection("ore-removal");
    if (section == null) {
      throw invalid("ore-removal", "expected a configuration section");
    }

    boolean enabled = optionalBoolean(section, "enabled", false, "ore-removal.enabled");
    Map<Material, Material> defaults = parseReplacements(section, "global", "ore-removal.global");
    Map<String, Map<Material, Material>> worldExceptions = parseWorldExceptions(section);
    return new GenerationPolicy(enabled, worldExceptions, defaults);
  }

  private static Map<String, Map<Material, Material>> parseWorldExceptions(ConfigurationSection policy) {
    Object rawExceptions = policy.get("exceptions");
    if (rawExceptions == null) {
      return Map.of();
    }

    ConfigurationSection exceptions = policy.getConfigurationSection("exceptions");
    if (exceptions == null) {
      throw invalid("ore-removal.exceptions", "expected a configuration section");
    }

    Map<String, Map<Material, Material>> worldExceptions = new HashMap<>();
    for (String world : exceptions.getKeys(false)) {
      String path = "ore-removal.exceptions." + world;
      ConfigurationSection worldSection = exceptions.getConfigurationSection(world);
      if (worldSection == null) {
        throw invalid(path, "expected a configuration section");
      }
      String worldKey;
      try {
        worldKey = WorldIdentity.parse(world).toString();
      } catch (IllegalArgumentException exception) {
        throw invalid(path, exception.getMessage());
      }
      worldExceptions.put(worldKey, parseReplacements(worldSection, path));
    }
    return Map.copyOf(worldExceptions);
  }

  private static Map<Material, Material> parseReplacements(ConfigurationSection parent, String key, String path) {
    Object rawSection = parent.get(key);
    if (rawSection == null) {
      return Map.of();
    }
    ConfigurationSection section = parent.getConfigurationSection(key);
    if (section == null) {
      throw invalid(path, "expected a configuration section");
    }
    return parseReplacements(section, path);
  }

  private static Map<Material, Material> parseReplacements(ConfigurationSection section, String path) {
    Map<Material, Material> replacements = new HashMap<>();
    boolean defaultValue = optionalBoolean(section, "default", false, path + ".default");
    if (defaultValue) {
      for (Material ore : ORES) {
        replacements.put(ore, getReplacement(ore));
      }
    }

    for (String key : section.getKeys(false)) {
      if ("default".equals(key)) {
        continue;
      }
      String materialPath = path + "." + key;
      Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
      if (material == null || !ORES.contains(material)) {
        throw invalid(materialPath, "unknown ore material '" + key + "'");
      }
      Object value = section.get(key);
      if (!(value instanceof Boolean)) {
        throw invalid(materialPath, "expected true or false");
      }
      if ((Boolean) value) {
        replacements.put(material, getReplacement(material));
      } else {
        replacements.remove(material);
      }
    }
    return Map.copyOf(replacements);
  }

  private static boolean optionalBoolean(ConfigurationSection section, String key, boolean defaultValue, String path) {
    Object value = section.get(key);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Boolean)) {
      throw invalid(path, "expected true or false");
    }
    return (Boolean) value;
  }

  private void attachToLoadedWorlds() {
    for (World world : Bukkit.getWorlds()) {
      attachToWorld(world);
    }
  }

  private void detachFromLoadedWorlds() {
    for (World world : Bukkit.getWorlds()) {
      try {
        world.getPopulators().removeIf(populator -> populator == this);
      } catch (RuntimeException exception) {
        plugin.getLogger().log(Level.WARNING, "Failed to detach HiddenOre generation rules from world " + world.getName(), exception);
      }
    }
  }

  private synchronized void attachToWorld(World world) {
    if (closed || !started) {
      return;
    }

    for (BlockPopulator populator : world.getPopulators()) {
      if (populator == this) {
        return;
      }
    }
    world.getPopulators().add(this);
  }

  private static IllegalArgumentException invalid(String path, String message) {
    return new IllegalArgumentException(path + ": " + message);
  }

  public record GenerationPolicy(boolean enabled,
                                 Map<String, Map<Material, Material>> worldExceptions,
                                 Map<Material, Material> defaults) {
    public GenerationPolicy {
      worldExceptions = Map.copyOf(Objects.requireNonNull(worldExceptions, "worldExceptions"));
      defaults = Map.copyOf(Objects.requireNonNull(defaults, "defaults"));
    }
  }
}
