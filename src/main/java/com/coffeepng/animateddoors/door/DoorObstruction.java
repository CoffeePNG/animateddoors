package com.coffeepng.animateddoors.door;

import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.model.Door;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds blocks standing where a door wants to land.
 *
 * <p>Only destination cells matter: the animation is drawn with display entities that pass through
 * anything, so a block halfway along a swing arc is cosmetic — a block in a <em>destination</em>
 * cell would be overwritten when the real blocks are placed.</p>
 */
public final class DoorObstruction {

    private DoorObstruction() {
    }

    /**
     * True if this block counts as being in the way. Air, water, and replaceable growth (grass,
     * snow layers, fire, …) are things a moving door is expected to sweep aside, so they don't.
     */
    public static boolean blocks(Block block) {
        return !block.getType().isAir() && !block.isLiquid() && !block.isReplaceable();
    }

    /**
     * Cells the door would overwrite by moving.
     *
     * @param opening true for the door's opening move, false for closing
     * @return occupied destination cells that are not part of the door itself
     */
    public static List<BlockVector3> find(World world, Door door, boolean opening) {
        List<BlockVector3> closedCells = door.closedPositions();
        Set<BlockVector3> vacated = new HashSet<>(closedCells.size());
        List<BlockVector3> destinations = new ArrayList<>(closedCells.size());
        for (BlockVector3 closed : closedCells) {
            BlockVector3 openPos = DoorGeometry.openPosition(door, closed);
            vacated.add(opening ? closed : openPos);
            destinations.add(opening ? openPos : closed);
        }
        List<BlockVector3> out = new ArrayList<>();
        for (BlockVector3 to : destinations) {
            // Cells the door is leaving behind are free by the time blocks are placed.
            if (vacated.contains(to)) {
                continue;
            }
            if (blocks(world.getBlockAt(to.x(), to.y(), to.z()))) {
                out.add(to);
            }
        }
        return out;
    }
}
