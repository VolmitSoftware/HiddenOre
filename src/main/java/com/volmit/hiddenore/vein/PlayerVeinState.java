package com.volmit.hiddenore.vein;

import org.bukkit.Location;

public class PlayerVeinState {
    public int veinTokens = 0;
    public long lastMineTime = 0;
    public Location lastMinedBlock = null;
    public long cooldownUntil = 0;

    // For tracking which drop is being extended as a vein
    public String currentDropKey = null;
}