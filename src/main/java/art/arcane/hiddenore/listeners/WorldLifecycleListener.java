package art.arcane.hiddenore.listeners;

import art.arcane.hiddenore.HiddenOre;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;

public final class WorldLifecycleListener implements Listener {
  private final HiddenOre plugin;

  public WorldLifecycleListener(HiddenOre plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onWorldUnload(WorldUnloadEvent event) {
    if (plugin.isDraining()) {
      return;
    }
    plugin.getRuntimeState().veinGenerator().clearWorld(event.getWorld().getUID());
  }
}
