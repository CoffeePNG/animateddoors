package com.coffeepng.animateddoors.door;

import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.model.Door;
import com.coffeepng.animateddoors.model.DoorType;
import com.coffeepng.animateddoors.util.BlockRotation;
import org.bukkit.block.BlockFace;

/**
 * Maps a door's closed-layout block positions to their open-state world positions,
 * for each supported {@link DoorType}.
 */
public final class DoorGeometry {

    private DoorGeometry() {
    }

    /** World position of a closed block when the door is open. */
    public static BlockVector3 openPosition(Door door, BlockVector3 closed) {
        if (door.getType() == DoorType.SWING) {
            return BlockRotation.rotate(closed, door.getHingeX(), door.getHingeZ(), door.getQuarterTurns());
        }
        BlockVector3 shift = openShift(door);
        return new BlockVector3(closed.x() + shift.x(), closed.y() + shift.y(), closed.z() + shift.z());
    }

    /**
     * The whole-block displacement applied to every cell of a translating door when it opens.
     * Zero for swing doors, which rotate instead.
     */
    public static BlockVector3 openShift(Door door) {
        return switch (door.getType()) {
            case PORTCULLIS -> new BlockVector3(0, door.getSlide(), 0);
            case SLIDING -> {
                BlockFace face = door.getSlideFace();
                int distance = door.getSlideDistance();
                yield new BlockVector3(face.getModX() * distance, 0, face.getModZ() * distance);
            }
            case SWING -> new BlockVector3(0, 0, 0);
        };
    }

    /** Net quarter-turn rotation applied to block facing over an opening swing (0 for non-swing types). */
    public static int openingQuarterTurns(Door door) {
        return door.getType() == DoorType.SWING ? door.getQuarterTurns() : 0;
    }
}
