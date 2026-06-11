package art.arcane.hiddenore.vein;

import art.arcane.hiddenore.rules.ItemDropRule;

public record VeinBlock(int packedPosition, int veinId, ItemDropRule rule) {
}
