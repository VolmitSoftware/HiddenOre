-keep class art.arcane.hiddenore.api.** { public *; }
-keep class art.arcane.volmlib.integration.** { *; }
-keep class * implements art.arcane.volmlib.integration.IntegrationServiceContract { *; }
-keepclassmembers class * extends org.bukkit.event.Event {
    public static org.bukkit.event.HandlerList getHandlerList();
    public org.bukkit.event.HandlerList getHandlers();
}
