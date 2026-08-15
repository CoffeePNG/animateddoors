package com.coffeepng.animateddoors.door;

import com.coffeepng.animateddoors.AnimatedDoorsPlugin;
import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.model.Door;
import com.coffeepng.animateddoors.util.BlockRotation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Animates a door swing using {@link BlockDisplay} entities.
 *
 * <p>Real blocks are removed for the duration of the swing and re-placed at their rotated
 * destinations when the animation finishes. Each frame the display transformation is rotated
 * around the hinge so the client interpolates a true arc between keyframes.</p>
 */
public class DoorAnimator {

    private final AnimatedDoorsPlugin plugin;

    public DoorAnimator(AnimatedDoorsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Toggle the door (open if closed, close if open) with animation.
     *
     * @return false if the door could not be animated right now (busy, unloaded, empty).
     */
    public boolean toggle(Door door) {
        if (door.isAnimating()) {
            return false;
        }
        World world = plugin.getServer().getWorld(door.getWorld());
        if (world == null) {
            return false;
        }

        boolean opening = !door.isOpen();
        int q = door.getQuarterTurns();
        int fromQuarters = opening ? 0 : q;          // where the real blocks currently sit
        int totalQuarters = opening ? q : -q;        // net rotation applied during this swing

        List<BlockData> datas = new ArrayList<>();
        List<BlockVector3> fromCells = new ArrayList<>();
        List<BlockVector3> toCells = new ArrayList<>();

        for (BlockVector3 closed : door.closedPositions()) {
            BlockVector3 from = BlockRotation.rotate(closed, door.getHingeX(), door.getHingeZ(), fromQuarters);
            BlockData data = world.getBlockAt(from.x(), from.y(), from.z()).getBlockData();
            if (data.getMaterial().isAir()) {
                continue;
            }
            BlockVector3 to = BlockRotation.rotate(closed, door.getHingeX(), door.getHingeZ(),
                    opening ? q : 0);
            datas.add(data);
            fromCells.add(from);
            toCells.add(to);
        }

        if (datas.isEmpty()) {
            return false;
        }

        door.setAnimating(true);

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
        double targetAngle = Math.toRadians(90.0 * totalQuarters);

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                double t = (double) step / steps;
                double eased = easeInOut(Math.min(1.0, t));
                float angle = (float) (eased * targetAngle);
                for (int i = 0; i < displays.size(); i++) {
                    BlockDisplay display = displays.get(i);
                    if (!display.isValid()) {
                        continue;
                    }
                    Vector3f h = hingeRel.get(i);
                    // Rotate the cube about the hinge relative to the display origin.
                    // JOML rotateY(-angle) matches the clockwise (x,z)->(-z,x) convention used everywhere.
                    Matrix4f m = new Matrix4f()
                            .translate(h.x, h.y, h.z)
                            .rotateY(-angle)
                            .translate(-h.x, -h.y, -h.z);
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(stepTicks);
                    display.setTransformationMatrix(m);
                }
                if (step >= steps) {
                    cancel();
                    // Let the final interpolation land, then swap real blocks back in.
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> finish(world, displays, datas, toCells, door, opening, totalQuarters),
                            stepTicks + 1L);
                }
            }
        }.runTaskTimer(plugin, 0L, stepTicks);

        return true;
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
