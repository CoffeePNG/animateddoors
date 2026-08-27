package com.coffeepng.dynamicdoors.util;

import com.coffeepng.dynamicdoors.model.BlockVector3;
import com.coffeepng.dynamicdoors.model.Door;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Display transforms shared by the real animation and the ghost preview, so a preview always
 * shows exactly the motion the door will perform.
 */
public final class DoorTransform {

    private DoorTransform() {
    }

    /** Hinge centre relative to a display sitting at {@code cell}'s corner. */
    public static Vector3f hingeOffset(Door door, BlockVector3 cell) {
        return new Vector3f(door.getHingeX() + 0.5f - cell.x(), 0f, door.getHingeZ() + 0.5f - cell.z());
    }

    /**
     * Transform for one keyframe.
     *
     * @param swing    true for a swing door (rotate about the hinge), false to translate vertically
     * @param hingeRel hinge offset from {@link #hingeOffset}
     * @param angle    current swing angle in radians
     * @param dy       current vertical offset in blocks
     */
    public static Matrix4f keyframe(boolean swing, Vector3f hingeRel, float angle, float dy) {
        if (!swing) {
            return new Matrix4f().translate(0f, dy, 0f);
        }
        // JOML rotateY(-angle) matches the clockwise (x,z)->(-z,x) convention used everywhere.
        return new Matrix4f()
                .translate(hingeRel.x, hingeRel.y, hingeRel.z)
                .rotateY(-angle)
                .translate(-hingeRel.x, -hingeRel.y, -hingeRel.z);
    }

    /** Slight upscale so a highlight display hugs the block it is drawn over without z-fighting. */
    public static Matrix4f highlight(float scale) {
        float offset = (scale - 1f) / 2f;
        return new Matrix4f().translate(-offset, -offset, -offset).scale(scale);
    }

    public static double easeInOut(double t) {
        return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    }
}
