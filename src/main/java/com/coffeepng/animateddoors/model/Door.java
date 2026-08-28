package com.coffeepng.animateddoors.model;

import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single animated door.
 *
 * <p>The door footprint is always stored in its CLOSED layout ({@link #min}..{@link #max}).
 * Open-state world positions are derived by rotating the closed positions around the hinge.</p>
 */
public class Door {

    private final UUID id;
    private String name;
    private String world;

    private BlockVector3 min;
    private BlockVector3 max;

    /** Vertical hinge column. Rotation happens around the centre of this column (hingeX+0.5, hingeZ+0.5). */
    private int hingeX;
    private int hingeZ;

    private DoorType type = DoorType.SWING;

    /** +1 = clockwise (viewed from above), -1 = counter-clockwise. Swing doors only. */
    private int quarterTurns = 1;

    /** Vertical slide distance in blocks; + = up, - = down. Portcullis doors only. */
    private int slide;

    /** Horizontal direction the door retracts towards when opening. Sliding doors only. */
    private BlockFace slideFace = BlockFace.EAST;

    /** Horizontal slide distance in blocks (always positive). Sliding doors only. */
    private int slideDistance;

    private boolean open;

    // Optional triggers.
    private BlockVector3 redstoneTrigger;
    private BlockVector3 floatingTrigger;
    private UUID floatingEntityId;

    // Runtime-only state.
    private transient boolean animating;

    public Door(UUID id, String name, String world, BlockVector3 min, BlockVector3 max) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.min = min;
        this.max = max;
        this.hingeX = min.x();
        this.hingeZ = min.z();
    }

    /** Every block position of the door in its closed layout (inclusive box). */
    public List<BlockVector3> closedPositions() {
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

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public BlockVector3 getMin() {
        return min;
    }

    public void setMin(BlockVector3 min) {
        this.min = min;
    }

    public BlockVector3 getMax() {
        return max;
    }

    public void setMax(BlockVector3 max) {
        this.max = max;
    }

    public int getHingeX() {
        return hingeX;
    }

    public int getHingeZ() {
        return hingeZ;
    }

    public void setHinge(int hingeX, int hingeZ) {
        this.hingeX = hingeX;
        this.hingeZ = hingeZ;
    }

    public DoorType getType() {
        return type;
    }

    public void setType(DoorType type) {
        this.type = type;
    }

    public int getQuarterTurns() {
        return quarterTurns;
    }

    public void setQuarterTurns(int quarterTurns) {
        this.quarterTurns = quarterTurns;
    }

    public int getSlide() {
        return slide;
    }

    public void setSlide(int slide) {
        this.slide = slide;
    }

    public BlockFace getSlideFace() {
        return slideFace;
    }

    /**
     * Set the horizontal direction a sliding door retracts towards.
     *
     * @throws IllegalArgumentException if {@code face} is not one of the four cardinal directions
     */
    public void setSlideFace(BlockFace face) {
        if (face == null || face.getModY() != 0 || Math.abs(face.getModX()) + Math.abs(face.getModZ()) != 1) {
            throw new IllegalArgumentException("Slide direction must be north, south, east or west.");
        }
        this.slideFace = face;
    }

    public int getSlideDistance() {
        return slideDistance;
    }

    public void setSlideDistance(int slideDistance) {
        this.slideDistance = slideDistance;
    }

    /** Height of the door's closed footprint in blocks. */
    public int height() {
        return max.y() - min.y() + 1;
    }

    /** Width of the door's closed footprint along X, in blocks. */
    public int widthX() {
        return max.x() - min.x() + 1;
    }

    /** Width of the door's closed footprint along Z, in blocks. */
    public int widthZ() {
        return max.z() - min.z() + 1;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public BlockVector3 getRedstoneTrigger() {
        return redstoneTrigger;
    }

    public void setRedstoneTrigger(BlockVector3 redstoneTrigger) {
        this.redstoneTrigger = redstoneTrigger;
    }

    public BlockVector3 getFloatingTrigger() {
        return floatingTrigger;
    }

    public void setFloatingTrigger(BlockVector3 floatingTrigger) {
        this.floatingTrigger = floatingTrigger;
    }

    public UUID getFloatingEntityId() {
        return floatingEntityId;
    }

    public void setFloatingEntityId(UUID floatingEntityId) {
        this.floatingEntityId = floatingEntityId;
    }

    public boolean isAnimating() {
        return animating;
    }

    public void setAnimating(boolean animating) {
        this.animating = animating;
    }
}
