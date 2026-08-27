package com.coffeepng.dynamicdoors.door;

import com.coffeepng.dynamicdoors.model.BlockVector3;
import com.coffeepng.dynamicdoors.model.Door;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory registry of all doors plus toggle cooldown bookkeeping.
 */
public class DoorManager {

    private final Map<UUID, Door> doorsById = new HashMap<>();
    private final Map<UUID, Long> lastToggleTick = new HashMap<>();

    public void add(Door door) {
        doorsById.put(door.getId(), door);
    }

    public void remove(Door door) {
        doorsById.remove(door.getId());
        lastToggleTick.remove(door.getId());
    }

    public Collection<Door> all() {
        return doorsById.values();
    }

    public Optional<Door> byName(String name) {
        for (Door door : doorsById.values()) {
            if (door.getName().equalsIgnoreCase(name)) {
                return Optional.of(door);
            }
        }
        return Optional.empty();
    }

    public boolean nameTaken(String name) {
        return byName(name).isPresent();
    }

    /** Find the door whose current (state-aware) footprint contains the given block, if any. */
    public Optional<Door> doorAt(String world, BlockVector3 block) {
        for (Door door : doorsById.values()) {
            if (!door.getWorld().equals(world)) {
                continue;
            }
            if (!door.isOpen()) {
                if (door.containsClosed(block)) {
                    return Optional.of(door);
                }
                continue;
            }
            for (BlockVector3 pos : currentPositions(door)) {
                if (pos.equals(block)) {
                    return Optional.of(door);
                }
            }
        }
        return Optional.empty();
    }

    /** The block positions the door occupies in the world right now, accounting for open/closed state. */
    public List<BlockVector3> currentPositions(Door door) {
        List<BlockVector3> closed = door.closedPositions();
        if (!door.isOpen()) {
            return closed;
        }
        List<BlockVector3> out = new ArrayList<>(closed.size());
        for (BlockVector3 pos : closed) {
            out.add(DoorGeometry.openPosition(door, pos));
        }
        return out;
    }

    public boolean onCooldown(Door door, long currentTick, long cooldownTicks) {
        Long last = lastToggleTick.get(door.getId());
        return last != null && currentTick - last < cooldownTicks;
    }

    public void markToggled(Door door, long currentTick) {
        lastToggleTick.put(door.getId(), currentTick);
    }
}
