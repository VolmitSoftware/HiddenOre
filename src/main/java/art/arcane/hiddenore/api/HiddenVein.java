package art.arcane.hiddenore.api;

import org.bukkit.Material;

public record HiddenVein(int x, int y, int z, int veinId, Material item, Material oreDisplay) {
  public static Material oreDisplayFor(Material item, int y) {
    Material surface = switch (item) {
      case COAL -> Material.COAL_ORE;
      case RAW_COPPER -> Material.COPPER_ORE;
      case RAW_IRON -> Material.IRON_ORE;
      case RAW_GOLD -> Material.GOLD_ORE;
      case REDSTONE -> Material.REDSTONE_ORE;
      case LAPIS_LAZULI -> Material.LAPIS_ORE;
      case DIAMOND -> Material.DIAMOND_ORE;
      case EMERALD -> Material.EMERALD_ORE;
      case QUARTZ -> Material.NETHER_QUARTZ_ORE;
      case GOLD_NUGGET -> Material.NETHER_GOLD_ORE;
      case NETHERITE_SCRAP -> Material.ANCIENT_DEBRIS;
      default -> null;
    };
    if (surface == null || y >= 0) {
      return surface;
    }
    return switch (surface) {
      case COAL_ORE -> Material.DEEPSLATE_COAL_ORE;
      case COPPER_ORE -> Material.DEEPSLATE_COPPER_ORE;
      case IRON_ORE -> Material.DEEPSLATE_IRON_ORE;
      case GOLD_ORE -> Material.DEEPSLATE_GOLD_ORE;
      case REDSTONE_ORE -> Material.DEEPSLATE_REDSTONE_ORE;
      case LAPIS_ORE -> Material.DEEPSLATE_LAPIS_ORE;
      case DIAMOND_ORE -> Material.DEEPSLATE_DIAMOND_ORE;
      case EMERALD_ORE -> Material.DEEPSLATE_EMERALD_ORE;
      default -> surface;
    };
  }
}
