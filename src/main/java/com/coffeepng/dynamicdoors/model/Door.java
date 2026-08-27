package com.coffeepng.dynamicdoors.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A single animated door.
 *
 * <p>A door is an explicit <em>set</em> of block positions, not a box: only the blocks that were
 * actually picked belong to the door, so a wall or floor that merely shares the bounding box is
 * never dragged along. The set is always stored in the door's CLOSED layout; open-state world
 * positions are derived from it by {@code DoorGeometry}.</p>
 */
public class Door {

    private final UUID id;
    private String name;
    private String world;

    /** The door's blocks in their closed layout. Insertion-ordered so previews look stable. */
    private final Set<BlockVector3> blocks = new LinkedHashSet<>();

    /** Cached bounding box of {@link #blocks}, kept in sync by {@link #setBlocks}. */
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

    private boolean open;

    // Optional triggers.
    /** Dedicated control block (a gold block by default): power it to toggle the door. */
    private BlockVector3 powerBlock;
    private BlockVector3 redstoneTrigger;
    private BlockVector3 floatingTrigger;
    private UUID floatingEntityId;

    // Runtime-only state.
    private transient boolean animating;
    /** Last known powered state of this door's control blocks, for rising-edge detection. */
    private transient boolean powered;

    public Door(UUID id, String name, String world, Collection<BlockVector3> blocks) {
        this.id = id;
        this.name = name;
        this.world = world;
        setBlocks(blocks);
        this.hingeX = min.x();
        this.hingeZ = min.z();
    }

    /** Replace the door's block set. Must not be empty. */
    public void setBlocks(Collection<BlockVector3> newBlocks) {
        if (newBlocks == null || newBlocks.isEmpty()) {
            throw new IllegalArgumentException("a door needs at least one block");
        }
        blocks.clear();
        blocks.addAll(newBlocks);
        recomputeBounds();
    }

    private void recomputeBounds() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockVector3 pos : blocks) {
            minX = Math.min(minX, pos.x());
            minY = Math.min(minY, pos.y());
            minZ = Math.min(minZ, pos.z());
            maxX = Math.max(maxX, pos.x());
            maxY = Math.max(maxY, pos.y());
            maxZ = Math.max(maxZ, pos.z());
        }
        this.min = new BlockVector3(minX, minY, minZ);
        this.max = new BlockVector3(maxX, maxY, maxZ);
    }

    /** Every block position of the door in its closed layout. */
    public List<BlockVector3> closedPositions() {
        return new ArrayList<>(blocks);
    }

    /** True if the given closed-layout position belongs to this door. */
    public boolean containsClosed(BlockVector3 pos) {
        return blocks.contains(pos);
    }

    public int blockCount() {
        return blocks.size();
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

    /** Minimum corner of the door's bounding box (derived from its blocks). */
    public BlockVector3 getMin() {
        return min;
    }

    /** Maximum corner of the door's bounding box (derived from its blocks). */
    public BlockVector3 getMax() {
        return max;
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

    /** Height of the door's closed footprint in blocks. */
    public int height() {
        return max.y() - min.y() + 1;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public BlockVector3 getPowerBlock() {
        return powerBlock;
    }

    public void setPowerBlock(BlockVector3 powerBlock) {
        this.powerBlock = powerBlock;
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

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public boolean isAnimating() {
        return animating;
    }

    public void setAnimating(boolean animating) {
        this.animating = animating;
    }
}
