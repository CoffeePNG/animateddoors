package com.coffeepng.dynamicdoors.util;

import com.coffeepng.dynamicdoors.model.BlockVector3;
import org.bukkit.Axis;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;

/**
 * Geometric rotation helpers around the vertical (Y) axis, in whole quarter turns.
 *
 * <p>One clockwise quarter turn (viewed from above, +Y up) maps the horizontal offset
 * {@code (x, z) -> (-z, x)}. This is the single source of truth used both for block
 * positions and for {@link BlockData} facing, so the two never disagree.</p>
 */
public final class BlockRotation {

    private BlockRotation() {
    }

    /** Rotate a door block position around the hinge column centre by {@code quarters} clockwise turns. */
    public static BlockVector3 rotate(BlockVector3 pos, int hingeX, int hingeZ, int quarters) {
        int ox = pos.x() - hingeX;
        int oz = pos.z() - hingeZ;
        int[] r = rotateOffset(ox, oz, quarters);
        return new BlockVector3(hingeX + r[0], pos.y(), hingeZ + r[1]);
    }

    /** Rotate a 2D horizontal offset by {@code quarters} clockwise turns. */
    public static int[] rotateOffset(int x, int z, int quarters) {
        int q = ((quarters % 4) + 4) % 4;
        for (int i = 0; i < q; i++) {
            int nx = -z;
            int nz = x;
            x = nx;
            z = nz;
        }
        return new int[]{x, z};
    }

    /** Rotate a {@link BlockFace} horizontally by {@code quarters} clockwise turns. Vertical faces are unchanged. */
    public static BlockFace rotateFace(BlockFace face, int quarters) {
        if (face.getModY() != 0 || (face.getModX() == 0 && face.getModZ() == 0)) {
            return face;
        }
        int[] r = rotateOffset(face.getModX(), face.getModZ(), quarters);
        for (BlockFace candidate : BlockFace.values()) {
            if (candidate.getModX() == r[0] && candidate.getModZ() == r[1] && candidate.getModY() == 0) {
                return candidate;
            }
        }
        return face;
    }

    /**
     * Return a copy of {@code data} with its orientation-related properties rotated by
     * {@code quarters} clockwise turns. Best-effort: unknown block types are returned unchanged.
     */
    public static BlockData rotate(BlockData data, int quarters) {
        int q = ((quarters % 4) + 4) % 4;
        if (q == 0) {
            return data;
        }
        BlockData copy = data.clone();

        if (copy instanceof Orientable orientable) {
            if (q % 2 == 1) {
                Axis axis = orientable.getAxis();
                if (axis == Axis.X) {
                    orientable.setAxis(Axis.Z);
                } else if (axis == Axis.Z) {
                    orientable.setAxis(Axis.X);
                }
            }
            return orientable;
        }

        if (copy instanceof Rotatable rotatable) {
            rotatable.setRotation(rotateFace(rotatable.getRotation(), q));
            return rotatable;
        }

        if (copy instanceof Directional directional) {
            BlockFace facing = directional.getFacing();
            BlockFace rotated = rotateFace(facing, q);
            if (directional.getFaces().contains(rotated)) {
                directional.setFacing(rotated);
            }
            return directional;
        }

        if (copy instanceof MultipleFacing multi) {
            boolean[] present = new boolean[BlockFace.values().length];
            for (BlockFace f : multi.getAllowedFaces()) {
                present[f.ordinal()] = multi.hasFace(f);
            }
            for (BlockFace f : multi.getAllowedFaces()) {
                BlockFace source = rotateFace(f, 4 - q); // where does this face's value come from
                boolean has = source.getModY() != 0 || (source.getModX() == 0 && source.getModZ() == 0)
                        ? present[f.ordinal()]
                        : present[source.ordinal()];
                multi.setFace(f, has);
            }
            return multi;
        }

        return copy;
    }
}
