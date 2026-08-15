package com.coffeepng.animateddoors.selection;

import com.coffeepng.animateddoors.model.BlockVector3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks each player's two selection corners set with the wand.
 */
public class SelectionManager {

    public static final class Selection {
        public BlockVector3 pos1;
        public BlockVector3 pos2;

        public boolean complete() {
            return pos1 != null && pos2 != null;
        }

        public BlockVector3 min() {
            return new BlockVector3(
                    Math.min(pos1.x(), pos2.x()),
                    Math.min(pos1.y(), pos2.y()),
                    Math.min(pos1.z(), pos2.z()));
        }

        public BlockVector3 max() {
            return new BlockVector3(
                    Math.max(pos1.x(), pos2.x()),
                    Math.max(pos1.y(), pos2.y()),
                    Math.max(pos1.z(), pos2.z()));
        }
    }

    private final Map<UUID, Selection> selections = new HashMap<>();

    public Selection get(UUID player) {
        return selections.computeIfAbsent(player, k -> new Selection());
    }

    public void clear(UUID player) {
        selections.remove(player);
    }
}
