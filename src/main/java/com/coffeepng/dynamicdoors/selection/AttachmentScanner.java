package com.coffeepng.dynamicdoors.selection;

import com.coffeepng.dynamicdoors.model.BlockVector3;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Hangable;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Bed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the small stuff stuck to a door — torches, buttons, levers, signs, ladders, carpets — that a
 * builder thinks of as part of the door but never clicks when selecting it.
 *
 * <p>Best-effort by design: it works out which neighbouring cell holds each candidate up, and pulls
 * the candidate in when that cell belongs to the selection. Everything it finds is previewed before
 * it is committed, so a wrong guess costs a left-click.</p>
 */
public final class AttachmentScanner {

    private static final BlockFace[] NEIGHBOURS = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    /** Passes to run, so a lantern hanging off a sign hanging off the door comes along too. */
    private static final int MAX_PASSES = 4;

    private AttachmentScanner() {
    }

    /**
     * Blocks attached to {@code selected} but not in it.
     *
     * @param limit stop after this many, so a scan can't run away across a build
     */
    public static List<BlockVector3> find(World world, Collection<BlockVector3> selected, int limit) {
        Set<BlockVector3> inSelection = new HashSet<>(selected);
        Set<BlockVector3> found = new LinkedHashSet<>();
        Set<BlockVector3> frontier = new LinkedHashSet<>(selected);

        for (int pass = 0; pass < MAX_PASSES && !frontier.isEmpty() && found.size() < limit; pass++) {
            Set<BlockVector3> next = new LinkedHashSet<>();
            for (BlockVector3 cell : frontier) {
                for (BlockFace face : NEIGHBOURS) {
                    BlockVector3 candidate = new BlockVector3(
                            cell.x() + face.getModX(), cell.y() + face.getModY(), cell.z() + face.getModZ());
                    if (inSelection.contains(candidate) || found.contains(candidate)) {
                        continue;
                    }
                    Block block = world.getBlockAt(candidate.x(), candidate.y(), candidate.z());
                    if (block.getType().isAir()) {
                        continue;
                    }
                    if (!dependsOn(block, cell)) {
                        continue;
                    }
                    found.add(candidate);
                    next.add(candidate);
                    if (found.size() >= limit) {
                        return new ArrayList<>(found);
                    }
                }
            }
            frontier = next;
        }
        return new ArrayList<>(found);
    }

    /** True if {@code block} is held up by — or is the other half of something in — the cell at {@code support}. */
    private static boolean dependsOn(Block block, BlockVector3 support) {
        for (BlockFace face : supportFaces(block)) {
            if (block.getX() + face.getModX() == support.x()
                    && block.getY() + face.getModY() == support.y()
                    && block.getZ() + face.getModZ() == support.z()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which neighbouring cells this block needs in order to exist: the surface it is stuck to, or
     * the other half of a two-block thing like a door or a bed.
     *
     * @return the faces to look in, empty for a block that stands on its own
     */
    private static List<BlockFace> supportFaces(Block block) {
        BlockData data = block.getBlockData();
        List<BlockFace> faces = new ArrayList<>(2);

        // Buttons, levers, grindstones: floor, ceiling or the wall behind them.
        if (data instanceof FaceAttachable attachable) {
            switch (attachable.getAttachedFace()) {
                case FLOOR -> faces.add(BlockFace.DOWN);
                case CEILING -> faces.add(BlockFace.UP);
                case WALL -> {
                    if (data instanceof Directional directional) {
                        faces.add(directional.getFacing().getOppositeFace());
                    }
                }
                default -> { }
            }
            return faces;
        }

        // Lanterns and hanging signs sit under a block or on top of one.
        if (data instanceof Hangable hangable) {
            faces.add(hangable.isHanging() ? BlockFace.UP : BlockFace.DOWN);
            return faces;
        }

        // Two-block things: each half needs the other.
        if (data instanceof Bed bed) {
            BlockFace toOther = bed.getPart() == Bed.Part.HEAD
                    ? bed.getFacing().getOppositeFace()
                    : bed.getFacing();
            faces.add(toOther);
            return faces;
        }
        if (data instanceof Bisected bisected && !(data instanceof org.bukkit.block.data.type.Stairs)
                && !(data instanceof org.bukkit.block.data.type.TrapDoor)) {
            faces.add(bisected.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP);
            if (bisected.getHalf() == Bisected.Half.BOTTOM) {
                faces.add(BlockFace.DOWN);
            }
            return faces;
        }

        // Vines and glow lichen cling to every face they are grown on.
        if (isSpreadable(block.getType()) && data instanceof MultipleFacing multi) {
            for (BlockFace face : multi.getAllowedFaces()) {
                if (multi.hasFace(face)) {
                    faces.add(face);
                }
            }
            return faces;
        }

        if (isWallMounted(block.getType()) && data instanceof Directional directional) {
            faces.add(directional.getFacing().getOppositeFace());
            return faces;
        }

        if (needsGround(block.getType())) {
            faces.add(BlockFace.DOWN);
        }
        return faces;
    }

    /** Blocks that hang off the side of another block, facing away from it. */
    private static boolean isWallMounted(Material material) {
        if (material == Material.LADDER || material == Material.TRIPWIRE_HOOK || material == Material.COCOA) {
            return true;
        }
        String name = material.name();
        return name.contains("WALL_TORCH")
                || name.endsWith("_WALL_SIGN")
                || name.endsWith("_WALL_HANGING_SIGN")
                || name.endsWith("_WALL_BANNER")
                || name.endsWith("_WALL_HEAD")
                || name.endsWith("_WALL_SKULL")
                || name.endsWith("_WALL_FAN");
    }

    private static boolean isSpreadable(Material material) {
        return material == Material.VINE || material == Material.GLOW_LICHEN
                || material == Material.SCULK_VEIN || material == Material.RESIN_CLUMP;
    }

    /** Blocks that rest on whatever is underneath them. */
    private static boolean needsGround(Material material) {
        if (Tag.RAILS.isTagged(material)
                || Tag.FLOWERS.isTagged(material)
                || Tag.SAPLINGS.isTagged(material)
                || Tag.WOOL_CARPETS.isTagged(material)
                || Tag.PRESSURE_PLATES.isTagged(material)
                || Tag.CANDLES.isTagged(material)
                || Tag.STANDING_SIGNS.isTagged(material)
                || Tag.BANNERS.isTagged(material)) {
            return true;
        }
        return switch (material) {
            case TORCH, SOUL_TORCH, REDSTONE_TORCH, REDSTONE_WIRE, REPEATER, COMPARATOR,
                 SNOW, CAKE, FLOWER_POT, LILY_PAD, TURTLE_EGG, SEA_PICKLE, CONDUIT,
                 DEAD_BUSH, SHORT_GRASS, FERN, SUGAR_CANE, BAMBOO, CACTUS -> true;
            default -> material.name().endsWith("_CANDLE_CAKE") || material.name().endsWith("_HEAD")
                    || material.name().endsWith("_SKULL");
        };
    }
}
