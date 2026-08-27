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

    /** Compact "x,y,z" form used for storage keys and lists. */
    public String serialize() {
        return x + "," + y + "," + z;
    }

    /** Parse the {@link #serialize()} form. */
    public static BlockVector3 deserialize(String raw) {
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("expected 'x,y,z', got '" + raw + "'");
        }
        return new BlockVector3(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim()));
    }

    @Override
    public String toString() {
        return x + ", " + y + ", " + z;
    }
}
