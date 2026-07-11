package art.arcane.hiddenore.util.project;

import art.arcane.hiddenore.rules.ItemDropRule;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public final class MiningUtil {
  private MiningUtil() {
  }

  public static boolean isPickaxe(Material mat) {
    return ToolTier.fromMaterial(mat) != null;
  }

  public static int applyFortune(ItemStack tool, ItemDropRule rule, int baseAmount) {
    if (tool == null || !rule.fortuneMultiplier) {
      return baseAmount;
    }
    int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
    if (fortune <= 0) {
      return baseAmount;
    }

    int roll = ThreadLocalRandom.current().nextInt(fortune + 2);
    return applyFortuneRoll(baseAmount, roll);
  }

  static int applyFortuneRoll(int baseAmount, int roll) {
    return baseAmount * Math.max(1, roll);
  }
}
