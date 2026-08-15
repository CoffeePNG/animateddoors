package com.coffeepng.animateddoors.door;

import com.coffeepng.animateddoors.AnimatedDoorsPlugin;
import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.model.Door;
import com.coffeepng.animateddoors.model.DoorType;
import com.coffeepng.animateddoors.util.BlockRotation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Animates a door using {@link BlockDisplay} entities.
 *
 * <p>Real blocks are removed for the duration of the move and re-placed at their destinations when the
 * animation finishes. Swing doors rotate their displays around the hinge (a true arc); portcullis doors
 * translate their displays vertically. Either way the client interpolates smoothly between keyframes.</p>
 */
public class DoorAnimator {

    private final AnimatedDoorsPlugin plugin;

    public DoorAnimator(AnimatedDoorsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Toggle the door (open if closed, close if open) with animation.
     *
     * @return the outcome; {@link ToggleResult#STARTED} means the swing began.
     */
    public ToggleResult toggle(Door door) {
        if (door.isAnimating()) {
            return ToggleResult.BUSY;
        }
        World world = plugin.getServer().getWorld(door.getWorld());
        if (world == null) {
            return ToggleResult.NO_WORLD;
        }

        boolean opening = !door.isOpen();
        boolean swing = door.getType() == DoorType.SWING;

        List<BlockData> datas = new ArrayList<>();
        List<BlockVector3> fromCells = new ArrayList<>();
        List<BlockVector3> toCells = new ArrayList<>();

        for (BlockVector3 closed : door.closedPositions()) {
            BlockVector3 openPos = DoorGeometry.openPosition(door, closed);
            BlockVector3 from = opening ? closed : openPos;   // where the real blocks currently sit
            BlockVector3 to = opening ? openPos : closed;     // where they'll land
            Block block = world.getBlockAt(from.x(), from.y(), from.z());
            BlockData data = block.getBlockData();
            if (data.getMaterial().isAir()) {
                continue;
            }
            // Refuse to move a container that still holds items — its contents would be lost.
            if (plugin.isBlockFilledContainers() && isFilledContainer(block)) {
                return ToggleResult.CONTAINER_WITH_ITEMS;
            }
            datas.add(data);
            fromCells.add(from);
            toCells.add(to);
        }

        if (datas.isEmpty()) {
            return ToggleResult.EMPTY;
        }

        door.setAnimating(true);

        // Net rotation applied to block facing over this move (0 for portcullis).
        int openQuarters = DoorGeometry.openingQuarterTurns(door);
        int finalQuarters = opening ? openQuarters : -openQuarters;

        // Remove the real blocks (no physics, so torches/water/redstone don't cascade).
        for (BlockVector3 cell : fromCells) {
            world.getBlockAt(cell.x(), cell.y(), cell.z()).setType(Material.AIR, false);
        }

        // Spawn a display per block, sitting exactly over its (now empty) source cell.
        float hingeCx = door.getHingeX() + 0.5f;
        float hingeCz = door.getHingeZ() + 0.5f;
        List<BlockDisplay> displays = new ArrayList<>(datas.size());
        List<Vector3f> hingeRel = new ArrayList<>(datas.size());
        for (int i = 0; i < datas.size(); i++) {
            BlockVector3 cell = fromCells.get(i);
            BlockData data = datas.get(i);
            Location loc = new Location(world, cell.x(), cell.y(), cell.z());
            BlockDisplay display = world.spawn(loc, BlockDisplay.class, bd -> {
                bd.setBlock(data);
                bd.setPersistent(false);
                bd.setInterpolationDelay(0);
            });
            displays.add(display);
            hingeRel.add(new Vector3f(hingeCx - cell.x(), 0f, hingeCz - cell.z()));
        }

        int stepTicks = Math.max(1, plugin.getStepTicks());
        int duration = Math.max(stepTicks, plugin.getDurationTicks());
        int steps = Math.max(1, duration / stepTicks);
        double targetAngle = Math.toRadians(90.0 * finalQuarters);        // swing
        float slideTotal = opening ? door.getSlide() : -door.getSlide();  // portcullis

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                double t = (double) step / steps;
                double eased = easeInOut(Math.min(1.0, t));
                float angle = (float) (eased * targetAngle);
                float dy = (float) (eased * slideTotal);
                for (int i = 0; i < displays.size(); i++) {
                    BlockDisplay display = displays.get(i);
                    if (!display.isValid()) {
                        continue;
                    }
                    Matrix4f m;
                    if (swing) {
                        Vector3f h = hingeRel.get(i);
                        // Rotate the cube about the hinge relative to the display origin.
                        // JOML rotateY(-angle) matches the clockwise (x,z)->(-z,x) convention used everywhere.
                        m = new Matrix4f()
                                .translate(h.x, h.y, h.z)
                                .rotateY(-angle)
                                .translate(-h.x, -h.y, -h.z);
                    } else {
                        m = new Matrix4f().translate(0f, dy, 0f);
                    }
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(stepTicks);
                    display.setTransformationMatrix(m);
                }
                if (step >= steps) {
                    cancel();
                    // Let the final interpolation land, then swap real blocks back in.
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> finish(world, displays, datas, toCells, door, opening, finalQuarters),
                            stepTicks + 1L);
                }
            }
        }.runTaskTimer(plugin, 0L, stepTicks);

        return ToggleResult.STARTED;
    }

    /** True if the block is a container whose inventory is not empty. */
    public static boolean isFilledContainer(Block block) {
        BlockState state = block.getState(false);
        if (state instanceof Container container) {
            return !container.getInventory().isEmpty();
        }
        return false;
    }

    private void finish(World world, List<BlockDisplay> displays, List<BlockData> datas,
                        List<BlockVector3> toCells, Door door, boolean opening, int totalQuarters) {
        for (BlockDisplay display : displays) {
            if (display.isValid()) {
                display.remove();
            }
        }
        for (int i = 0; i < toCells.size(); i++) {
            BlockVector3 cell = toCells.get(i);
            BlockData rotated = BlockRotation.rotate(datas.get(i), totalQuarters);
            world.getBlockAt(cell.x(), cell.y(), cell.z()).setBlockData(rotated, false);
        }
        door.setOpen(opening);
        door.setAnimating(false);
        plugin.saveDoors();
    }

    private static double easeInOut(double t) {
        return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    }
}
