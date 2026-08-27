package com.coffeepng.dynamicdoors.preview;

import com.coffeepng.dynamicdoors.DynamicDoorsPlugin;
import com.coffeepng.dynamicdoors.door.DoorGeometry;
import com.coffeepng.dynamicdoors.door.DoorObstruction;
import com.coffeepng.dynamicdoors.model.BlockVector3;
import com.coffeepng.dynamicdoors.model.Door;
import com.coffeepng.dynamicdoors.model.DoorType;
import com.coffeepng.dynamicdoors.util.DoorTransform;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ghost previews built from {@link BlockDisplay} entities.
 *
 * <p>Nothing here touches real blocks. Two kinds of preview:</p>
 * <ul>
 *   <li><b>Selection highlight</b> — glowing outlines over exactly the blocks that belong to the
 *       door, so it is obvious before creating it whether a wall or floor snuck in.</li>
 *   <li><b>Motion preview</b> — a ghost copy of the door performing its move, so the hinge,
 *       direction and slide distance can be checked without moving the build.</li>
 * </ul>
 *
 * <p>Previews are shown only to the player who asked for them, and are cleaned up when they expire,
 * when a new preview replaces them, or when the player logs out.</p>
 */
public class PreviewManager {

    /** Colour of a selection highlight. */
    public static final Color SELECTION_COLOR = Color.fromRGB(0x36, 0xD7, 0xFF);
    /** Colour of a motion preview ghost. */
    public static final Color MOTION_COLOR = Color.fromRGB(0x8B, 0xFF, 0x6B);
    /** Colour used for blocks that are in the way of the door's destination. */
    public static final Color OBSTRUCTION_COLOR = Color.fromRGB(0xFF, 0x5C, 0x5C);
    /** Colour of a hinge marker. */
    public static final Color HINGE_COLOR = Color.fromRGB(0xFF, 0xC4, 0x3D);

    private static final float HIGHLIGHT_SCALE = 1.02f;

    private final DynamicDoorsPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public PreviewManager(DynamicDoorsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Everything one player currently has on screen. */
    private static final class Session {
        final List<BlockDisplay> displays = new ArrayList<>();
        final List<BukkitTask> tasks = new ArrayList<>();
    }

    /**
     * Outline a set of blocks for a player.
     *
     * @return how many outlines were drawn (air cells are skipped)
     */
    public int showSelection(Player viewer, World world, Collection<BlockVector3> cells, Color color, int ticks) {
        return draw(restart(viewer), viewer, world, cells, color, ticks);
    }

    /**
     * Add more outlines to whatever this player is already being shown, instead of replacing it.
     *
     * <p>Used to paint a second colour over a preview — the blocks in the way of a door, on top of
     * the door's own blocks — so both can be read at once.</p>
     */
    public int overlay(Player viewer, World world, Collection<BlockVector3> cells, Color color, int ticks) {
        Session session = sessions.get(viewer.getUniqueId());
        if (session == null) {
            session = restart(viewer);
        }
        return draw(session, viewer, world, cells, color, ticks);
    }

    private int draw(Session session, Player viewer, World world, Collection<BlockVector3> cells,
                     Color color, int ticks) {
        int drawn = 0;
        for (BlockVector3 cell : cells) {
            BlockData data = world.getBlockAt(cell.x(), cell.y(), cell.z()).getBlockData();
            if (data.getMaterial().isAir()) {
                continue;
            }
            BlockDisplay display = spawn(viewer, world, cell, data, color);
            display.setTransformationMatrix(DoorTransform.highlight(HIGHLIGHT_SCALE));
            session.displays.add(display);
            drawn++;
        }
        expireAfter(viewer, session, ticks);
        return drawn;
    }

    /**
     * Draw a small glowing marker in the middle of each cell, whatever is actually there.
     *
     * <p>Unlike {@link #showSelection} this also marks empty cells, so it can point at things that
     * aren't blocks — like a hinge column running through open air.</p>
     */
    public int showMarkers(Player viewer, World world, Collection<BlockVector3> cells,
                           BlockData marker, Color color, int ticks) {
        Session session = restart(viewer);
        for (BlockVector3 cell : cells) {
            BlockDisplay display = spawn(viewer, world, cell, marker, color);
            // A quarter-size cube floating at the centre of the cell.
            display.setTransformationMatrix(new Matrix4f().translate(0.375f, 0.375f, 0.375f).scale(0.25f));
            session.displays.add(display);
        }
        expireAfter(viewer, session, ticks);
        return session.displays.size();
    }

    /**
     * Play a ghost run of a door's move for one player. Real blocks stay put.
     *
     * @param opening true to preview opening, false to preview closing
     * @return how many ghost blocks were spawned
     */
    public int showMotion(Player viewer, Door door, boolean opening, int holdTicks) {
        World world = plugin.getServer().getWorld(door.getWorld());
        if (world == null) {
            return 0;
        }
        Session session = restart(viewer);

        boolean swing = door.getType() == DoorType.SWING;
        int openQuarters = DoorGeometry.openingQuarterTurns(door);
        int finalQuarters = opening ? openQuarters : -openQuarters;
        double targetAngle = Math.toRadians(90.0 * finalQuarters);
        float slideTotal = opening ? door.getSlide() : -door.getSlide();

        List<Vector3f> hingeRel = new ArrayList<>();
        for (BlockVector3 closed : door.closedPositions()) {
            BlockVector3 openPos = DoorGeometry.openPosition(door, closed);
            BlockVector3 from = opening ? closed : openPos;
            BlockData data = world.getBlockAt(from.x(), from.y(), from.z()).getBlockData();
            if (data.getMaterial().isAir()) {
                continue;
            }
            BlockDisplay display = spawn(viewer, world, from, data, MOTION_COLOR);
            session.displays.add(display);
            hingeRel.add(DoorTransform.hingeOffset(door, from));
        }
        if (session.displays.isEmpty()) {
            cancel(viewer);
            return 0;
        }

        int stepTicks = Math.max(1, plugin.getStepTicks());
        int steps = Math.max(1, Math.max(stepTicks, plugin.getDurationTicks()) / stepTicks);

        BukkitTask task = new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                double eased = DoorTransform.easeInOut(Math.min(1.0, (double) step / steps));
                float angle = (float) (eased * targetAngle);
                float dy = (float) (eased * slideTotal);
                for (int i = 0; i < session.displays.size(); i++) {
                    BlockDisplay display = session.displays.get(i);
                    if (!display.isValid()) {
                        continue;
                    }
                    Matrix4f m = DoorTransform.keyframe(swing, hingeRel.get(i), angle, dy);
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(stepTicks);
                    display.setTransformationMatrix(m);
                }
                if (step >= steps) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, stepTicks);
        session.tasks.add(task);

        expireAfter(viewer, session, steps * stepTicks + Math.max(0, holdTicks));
        return session.displays.size();
    }

    /** Outline the blocks in the way of a door's next move, in red. */
    public int showObstructions(Player viewer, Door door, int ticks) {
        World world = plugin.getServer().getWorld(door.getWorld());
        if (world == null) {
            return 0;
        }
        List<BlockVector3> blocked = DoorObstruction.find(world, door, !door.isOpen());
        if (blocked.isEmpty()) {
            return 0;
        }
        return showSelection(viewer, world, blocked, OBSTRUCTION_COLOR, ticks);
    }

    /** Drop any preview this player has running. */
    public void cancel(Player viewer) {
        Session session = sessions.remove(viewer.getUniqueId());
        if (session != null) {
            despawn(session);
        }
    }

    public void cancelAll() {
        for (Session session : sessions.values()) {
            despawn(session);
        }
        sessions.clear();
    }

    public boolean hasPreview(Player viewer) {
        return sessions.containsKey(viewer.getUniqueId());
    }

    // ---- internals ---------------------------------------------------------

    private Session restart(Player viewer) {
        cancel(viewer);
        Session session = new Session();
        sessions.put(viewer.getUniqueId(), session);
        return session;
    }

    private BlockDisplay spawn(Player viewer, World world, BlockVector3 cell, BlockData data, Color color) {
        Location loc = new Location(world, cell.x(), cell.y(), cell.z());
        BlockDisplay display = world.spawn(loc, BlockDisplay.class, bd -> {
            bd.setBlock(data);
            bd.setPersistent(false);
            bd.setGlowing(true);
            bd.setGlowColorOverride(color);
            bd.setInterpolationDelay(0);
            bd.setViewRange(2.0f);
        });
        // A preview is feedback for one builder, not a light show for the whole server.
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.getUniqueId().equals(viewer.getUniqueId())) {
                other.hideEntity(plugin, display);
            }
        }
        return display;
    }

    private void expireAfter(Player viewer, Session session, int ticks) {
        UUID id = viewer.getUniqueId();
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(id) == session) {
                sessions.remove(id);
                despawn(session);
            }
        }, Math.max(1, ticks));
        session.tasks.add(task);
    }

    private void despawn(Session session) {
        for (BukkitTask task : session.tasks) {
            task.cancel();
        }
        session.tasks.clear();
        for (BlockDisplay display : session.displays) {
            if (display.isValid()) {
                display.remove();
            }
        }
        session.displays.clear();
    }
}
