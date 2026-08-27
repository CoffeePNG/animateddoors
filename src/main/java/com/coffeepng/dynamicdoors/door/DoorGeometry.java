package com.coffeepng.dynamicdoors.door;

import com.coffeepng.dynamicdoors.model.BlockVector3;
import com.coffeepng.dynamicdoors.model.Door;
import com.coffeepng.dynamicdoors.model.DoorType;
import com.coffeepng.dynamicdoors.util.BlockRotation;

/**
 * Maps a door's closed-layout block positions to their open-state world positions,
 * for each supported {@link DoorType}.
 */
public final class DoorGeometry {

    private DoorGeometry() {
    }

    /** World position of a closed block when the door is open. */
    public static BlockVector3 openPosition(Door door, BlockVector3 closed) {
        if (door.getType() == DoorType.PORTCULLIS) {
            return new BlockVector3(closed.x(), closed.y() + door.getSlide(), closed.z());
        }
        return BlockRotation.rotate(closed, door.getHingeX(), door.getHingeZ(), door.getQuarterTurns());
    }

    /** Net quarter-turn rotation applied to block facing over an opening swing (0 for non-swing types). */
    public static int openingQuarterTurns(Door door) {
        return door.getType() == DoorType.SWING ? door.getQuarterTurns() : 0;
    }
}
