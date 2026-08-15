package com.coffeepng.animateddoors.model;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Immutable integer block coordinate.
 */
public record BlockVector3(int x, int y, int z) {

    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }

    public Location toCenterLocation(World world) {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }
}
