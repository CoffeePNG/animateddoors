package com.coffeepng.animateddoors.listener;

import com.coffeepng.animateddoors.AnimatedDoorsPlugin;
import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.model.Door;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

/**
 * Drives doors from redstone.
 *
 * <p>A door can have two control points: its <b>power block</b> (a dedicated block of a configured
 * material, gold by default) and a plain <b>redstone trigger</b> block. Either one toggles the door
 * when it goes from unpowered to powered.</p>
 *
 * <p>Redstone events fire for the component that changed, not for the solid block it powers, so the
 * check runs a tick later and asks the control block itself whether it is powered — that way a lever
 * on the side of a power block, a button, a repeater feeding it, or dust running into it all work.</p>
 */
public class RedstoneListener implements Listener {

    private final AnimatedDoorsPlugin plugin;

    public RedstoneListener(AnimatedDoorsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRedstone(BlockRedstoneEvent event) {
        Block changed = event.getBlock();
        String world = changed.getWorld().getName();
        BlockVector3 pos = new BlockVector3(changed.getX(), changed.getY(), changed.getZ());

        boolean recheck = false;
        for (Door door : plugin.getDoorManager().all()) {
            if (!door.getWorld().equals(world)) {
                continue;
            }
            if (near(door.getPowerBlock(), pos) || near(door.getRedstoneTrigger(), pos)) {
                recheck = true;
                break;
            }
        }
        if (!recheck) {
            return;
        }
        // Let the redstone update settle before reading power states.
        plugin.getServer().getScheduler().runTask(plugin, () -> recheckPower(changed.getWorld()));
    }

    /** Toggle every door in this world whose control blocks just went from unpowered to powered. */
    private void recheckPower(World world) {
        for (Door door : plugin.getDoorManager().all()) {
            if (!door.getWorld().equals(world.getName())) {
                continue;
            }
            boolean powered = isPowered(world, door.getPowerBlock(), plugin.requiresPowerBlockMaterial())
                    || isPowered(world, door.getRedstoneTrigger(), false);
            if (powered == door.isPowered()) {
                continue;
            }
            door.setPowered(powered);
            if (powered) {
                plugin.attemptToggle(door);
            }
        }
    }

    /** True if the control block at {@code pos} is currently receiving redstone power. */
    private boolean isPowered(World world, BlockVector3 pos, boolean requireMaterial) {
        if (pos == null) {
            return false;
        }
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (requireMaterial && block.getType() != plugin.getPowerBlockMaterial()) {
            // Someone mined or replaced the power block; it can't drive the door any more.
            return false;
        }
        return block.isBlockPowered() || block.isBlockIndirectlyPowered();
    }

    /** Control points react to redstone changes in their own cell or in any block touching it. */
    private static boolean near(BlockVector3 control, BlockVector3 changed) {
        if (control == null) {
            return false;
        }
        return Math.abs(control.x() - changed.x()) <= 1
                && Math.abs(control.y() - changed.y()) <= 1
                && Math.abs(control.z() - changed.z()) <= 1;
    }
}
