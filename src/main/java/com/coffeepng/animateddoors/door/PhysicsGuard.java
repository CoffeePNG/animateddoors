package com.coffeepng.animateddoors.door;

import com.coffeepng.animateddoors.model.BlockVector3;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Cells that are off-limits to block updates while a door is moving.
 *
 * <p>The door's own writes already skip physics ({@code applyPhysics = false}), so it never notifies
 * its neighbours. This covers the updates that come from the other direction during the swap —
 * vanilla scheduled ticks, fluids flowing into cells the door just vacated, gravity blocks, other
 * plugins reacting to the change — which would otherwise pop torches off, break attached blocks or
 * let water into the doorway mid-animation.</p>
 *
 * <p>Cells are reference-counted, so overlapping or neighbouring doors moving at once can't unguard
 * each other's cells.</p>
 */
public class PhysicsGuard {

    private final Map<String, Map<BlockVector3, Integer>> guarded = new HashMap<>();
    private int totalGuarded;

    /** Protect these cells until the matching {@link #release}. */
    public void guard(String world, Collection<BlockVector3> cells) {
        Map<BlockVector3, Integer> counts = guarded.computeIfAbsent(world, k -> new HashMap<>());
        for (BlockVector3 cell : cells) {
            counts.merge(cell, 1, Integer::sum);
            totalGuarded++;
        }
    }

    /** Drop one guard on each cell. */
    public void release(String world, Collection<BlockVector3> cells) {
        Map<BlockVector3, Integer> counts = guarded.get(world);
        if (counts == null) {
            return;
        }
        for (BlockVector3 cell : cells) {
            Integer count = counts.get(cell);
            if (count == null) {
                continue;
            }
            totalGuarded--;
            if (count <= 1) {
                counts.remove(cell);
            } else {
                counts.put(cell, count - 1);
            }
        }
        if (counts.isEmpty()) {
            guarded.remove(world);
        }
    }

    public boolean isGuarded(String world, int x, int y, int z) {
        Map<BlockVector3, Integer> counts = guarded.get(world);
        return counts != null && counts.containsKey(new BlockVector3(x, y, z));
    }

    /** Fast path: block-update events are extremely hot, so bail out when no door is moving. */
    public boolean isActive() {
        return totalGuarded > 0;
    }

    public void clear() {
        guarded.clear();
        totalGuarded = 0;
    }
}
