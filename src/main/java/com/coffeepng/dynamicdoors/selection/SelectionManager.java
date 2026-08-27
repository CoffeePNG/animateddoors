package com.coffeepng.dynamicdoors.selection;

import com.coffeepng.dynamicdoors.model.BlockVector3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks each player's in-progress door selection.
 *
 * <p>Two ways to build one: pick blocks one at a time ({@link SelectionMode#BLOCK}) or set two
 * corners and add the box ({@link SelectionMode#REGION}). Either way the selection resolves to an
 * explicit set of blocks, so only what the player actually picked ends up in the door.</p>
 */
public class SelectionManager {

    public static final class Selection {

        private SelectionMode mode;
        private String world;

        /** Blocks picked one by one (BLOCK mode) or committed from a region. */
        private final Set<BlockVector3> blocks = new LinkedHashSet<>();

        public BlockVector3 pos1;
        public BlockVector3 pos2;

        Selection(SelectionMode mode) {
            this.mode = mode;
        }

        public SelectionMode mode() {
            return mode;
        }

        public void setMode(SelectionMode mode) {
            this.mode = mode;
        }

        public String world() {
            return world;
        }

        /**
         * Bind the selection to a world, wiping it first if the player moved to a different one.
         *
         * @return true if a previous selection in another world was discarded
         */
        public boolean bindWorld(String worldName) {
            if (world != null && world.equals(worldName)) {
                return false;
            }
            boolean discarded = world != null && !blocks.isEmpty();
            if (world != null) {
                blocks.clear();
                pos1 = null;
                pos2 = null;
            }
            world = worldName;
            return discarded;
        }

        public boolean regionComplete() {
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

        /** Every block position inside the current two-corner region (empty if incomplete). */
        public List<BlockVector3> regionPositions() {
            if (!regionComplete()) {
                return List.of();
            }
            BlockVector3 min = min();
            BlockVector3 max = max();
            List<BlockVector3> out = new ArrayList<>();
            for (int x = min.x(); x <= max.x(); x++) {
                for (int y = min.y(); y <= max.y(); y++) {
                    for (int z = min.z(); z <= max.z(); z++) {
                        out.add(new BlockVector3(x, y, z));
                    }
                }
            }
            return out;
        }

        /** @return true if the block was newly added */
        public boolean add(BlockVector3 pos) {
            return blocks.add(pos);
        }

        /** @return true if the block was present and got removed */
        public boolean remove(BlockVector3 pos) {
            return blocks.remove(pos);
        }

        public boolean contains(BlockVector3 pos) {
            return blocks.contains(pos);
        }

        public int addAll(Collection<BlockVector3> positions) {
            int added = 0;
            for (BlockVector3 pos : positions) {
                if (blocks.add(pos)) {
                    added++;
                }
            }
            return added;
        }

        public int removeAll(Collection<BlockVector3> positions) {
            int removed = 0;
            for (BlockVector3 pos : positions) {
                if (blocks.remove(pos)) {
                    removed++;
                }
            }
            return removed;
        }

        /** The picked blocks, in pick order. */
        public List<BlockVector3> blocks() {
            return new ArrayList<>(blocks);
        }

        public int size() {
            return blocks.size();
        }

        public boolean isEmpty() {
            return blocks.isEmpty();
        }

        public void clearBlocks() {
            blocks.clear();
            pos1 = null;
            pos2 = null;
        }
    }

    private final Map<UUID, Selection> selections = new HashMap<>();
    private SelectionMode defaultMode = SelectionMode.BLOCK;

    public void setDefaultMode(SelectionMode defaultMode) {
        this.defaultMode = defaultMode;
    }

    public SelectionMode getDefaultMode() {
        return defaultMode;
    }

    public Selection get(UUID player) {
        return selections.computeIfAbsent(player, k -> new Selection(defaultMode));
    }

    public void clear(UUID player) {
        selections.remove(player);
    }
}
