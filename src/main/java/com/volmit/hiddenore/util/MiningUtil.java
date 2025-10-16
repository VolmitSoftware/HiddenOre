package com.volmit.hiddenore.util;

import com.volmit.hiddenore.rules.ItemDropRule;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

public class MiningUtil {
    public static boolean isPickaxe(Material mat) {
        return ToolTier.fromMaterial(mat) != null;
    }

    public static int applyFortune(ItemStack tool, ItemDropRule rule, int baseAmount) {
        if (tool == null || !rule.fortuneMultiplier) {
            return baseAmount;
        }
        int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        if (fortune <= 0) return baseAmount;

        // Vanilla-like fortune logic for ores
        int bonus = 0;
        for (int i = 0; i < fortune; ++i) {
            if (Math.random() < 1.0 / (fortune + 2)) {
                bonus++;
            }
        }
        return baseAmount + bonus;
    }

    public static boolean hasSilkTouch(ItemStack tool) {
        return tool != null && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
    }
}