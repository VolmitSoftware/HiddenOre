package art.arcane.hiddenore.util.project;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class SoundResolverTest {
  @Test
  public void resolve_prefersNamespacedRegistryKeys() {
    Sound resolved = SoundResolver.resolve("minecraft:block.beacon.power_select", Sound.ENTITY_EXPERIENCE_ORB_PICKUP);

    assertEquals(NamespacedKey.minecraft("block.beacon.power_select"), Registry.SOUND_EVENT.getKey(resolved));
  }

  @Test
  public void resolve_supportsLegacyFieldNames() {
    Sound resolved = SoundResolver.resolve("BLOCK_BEACON_POWER_SELECT", Sound.ENTITY_EXPERIENCE_ORB_PICKUP);

    assertSame(Sound.BLOCK_BEACON_POWER_SELECT, resolved);
  }

  @Test
  public void resolve_returnsFallbackForMissingValues() {
    Sound fallback = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;

    assertSame(fallback, SoundResolver.resolve(null, fallback));
    assertSame(fallback, SoundResolver.resolve("not_a_real_sound", fallback));
  }
}
