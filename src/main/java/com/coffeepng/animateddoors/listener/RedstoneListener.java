package com.coffeepng.animateddoors.listener;

import com.coffeepng.animateddoors.AnimatedDoorsPlugin;
import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.model.Door;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

/**
 * Toggles a door when its configured redstone-trigger block goes from unpowered to powered.
 */
public class RedstoneListener implements Listener {

    private final AnimatedDoorsPlugin plugin;

    public RedstoneListener(AnimatedDoorsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRedstone(BlockRedstoneEvent event) {
        // Only act on the rising edge.
        if (event.getOldCurrent() > 0 || event.getNewCurrent() <= 0) {
            return;
        }
        Block block = event.getBlock();
        BlockVector3 pos = new BlockVector3(block.getX(), block.getY(), block.getZ());
        String world = block.getWorld().getName();

        for (Door door : plugin.getDoorManager().all()) {
            BlockVector3 trigger = door.getRedstoneTrigger();
            if (trigger != null && door.getWorld().equals(world) && trigger.equals(pos)) {
                plugin.attemptToggle(door);
            }
        }
    }
}
