package art.arcane.hiddenore.generation;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.service.HiddenOreTelemetry;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
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

    // Spigot's LimitedRegion lacks Paper's getCenter* accessors; the populated chunk is the region center.
    RegionBounds bounds = RegionBounds.of(chunkX, chunkZ, region.getBuffer());
    int yMin = world.getMinHeight();
    int yMax = world.getMaxHeight() - 1;

    long replaced = 0L;
    for (int cX = -bounds.bufferChunks(); cX <= bounds.bufferChunks(); cX++) {
      for (int cZ = -bounds.bufferChunks(); cZ <= bounds.bufferChunks(); cZ++) {
        int bX = (cX + chunkX) << 4;
        int bZ = (cZ + chunkZ) << 4;
        int minX = Math.max(bounds.xMin(), bX);
        int maxX = Math.min(bounds.xMax(), bX + 16);
        int minZ = Math.max(bounds.zMin(), bZ);
        int maxZ = Math.min(bounds.zMax(), bZ + 16);

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

  public static GenerationPolicy parsePolicy(JsonObject configuration) {
    JsonObject config = Objects.requireNonNull(configuration, "configuration");
    JsonElement rawPolicy = config.get("ore-removal");
    if (rawPolicy == null) {
      return DISABLED_POLICY;
    }

    if (!(rawPolicy instanceof JsonObject section)) {
      throw invalid("ore-removal", "expected a table");
    }

    boolean enabled = optionalBoolean(section, "enabled", false, "ore-removal.enabled");
    Map<Material, Material> defaults = parseReplacements(section, "global", "ore-removal.global");
    Map<String, Map<Material, Material>> worldExceptions = parseWorldExceptions(section);
    return new GenerationPolicy(enabled, worldExceptions, defaults);
  }

  private static Map<String, Map<Material, Material>> parseWorldExceptions(JsonObject policy) {
    JsonElement rawExceptions = policy.get("exceptions");
    if (rawExceptions == null) {
      return Map.of();
    }

    if (!(rawExceptions instanceof JsonObject exceptions)) {
      throw invalid("ore-removal.exceptions", "expected a table");
    }

    Map<String, Map<Material, Material>> worldExceptions = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : exceptions.entrySet()) {
      String world = entry.getKey();
      String path = "ore-removal.exceptions." + world;
      if (!(entry.getValue() instanceof JsonObject worldSection)) {
        throw invalid(path, "expected a table");
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

  private static Map<Material, Material> parseReplacements(JsonObject parent, String key, String path) {
    JsonElement rawSection = parent.get(key);
    if (rawSection == null) {
      return Map.of();
    }
    if (!(rawSection instanceof JsonObject section)) {
      throw invalid(path, "expected a table");
    }
    return parseReplacements(section, path);
  }

  private static Map<Material, Material> parseReplacements(JsonObject section, String path) {
    Map<Material, Material> replacements = new HashMap<>();
    boolean defaultValue = optionalBoolean(section, "default", false, path + ".default");
    if (defaultValue) {
      for (Material ore : ORES) {
        replacements.put(ore, getReplacement(ore));
      }
    }

    for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
      String key = entry.getKey();
      if ("default".equals(key)) {
        continue;
      }
      String materialPath = path + "." + key;
      Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
      if (material == null || !ORES.contains(material)) {
        throw invalid(materialPath, "unknown ore material '" + key + "'");
      }
      JsonElement value = entry.getValue();
      if (!(value instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
        throw invalid(materialPath, "expected true or false");
      }
      if (primitive.getAsBoolean()) {
        replacements.put(material, getReplacement(material));
      } else {
        replacements.remove(material);
      }
    }
    return Map.copyOf(replacements);
  }

  private static boolean optionalBoolean(JsonObject section, String key, boolean defaultValue, String path) {
    JsonElement value = section.get(key);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
      throw invalid(path, "expected true or false");
    }
    return primitive.getAsBoolean();
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
        plugin.logException(Level.WARNING, exception,
            "Failed to detach HiddenOre generation rules from world %s.", world.getName());
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

  record RegionBounds(int bufferChunks, int xMin, int zMin, int xMax, int zMax) {
    static RegionBounds of(int chunkX, int chunkZ, int bufferBlocks) {
      int xCenter = chunkX << 4;
      int zCenter = chunkZ << 4;
      return new RegionBounds(bufferBlocks >> 4,
          xCenter - bufferBlocks,
          zCenter - bufferBlocks,
          xCenter + bufferBlocks + 16,
          zCenter + bufferBlocks + 16);
    }
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
