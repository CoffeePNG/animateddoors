package com.coffeepng.dynamicdoors.listener;

import com.coffeepng.dynamicdoors.DynamicDoorsPlugin;
import com.coffeepng.dynamicdoors.door.PhysicsGuard;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/**
 * Holds block updates off a door's cells while it moves.
 *
 * <p>Every handler starts with the guard's fast path, so when no door is animating this costs one
 * boolean read per event.</p>
 */
public class PhysicsGuardListener implements Listener {

    private final DynamicDoorsPlugin plugin;

    public PhysicsGuardListener(DynamicDoorsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Vanilla shape/support updates on (or caused by) a moving door's cells. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        PhysicsGuard guard = plugin.getPhysicsGuard();
        if (!guard.isActive()) {
            return;
        }
        if (isGuarded(guard, event.getBlock()) || isGuarded(guard, event.getSourceBlock())) {
            event.setCancelled(true);
        }
    }

    /** Water or lava trying to run into a cell the door is passing through. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent event) {
        PhysicsGuard guard = plugin.getPhysicsGuard();
        if (!guard.isActive()) {
            return;
        }
        if (isGuarded(guard, event.getToBlock()) || isGuarded(guard, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** A falling sand/gravel block trying to land in the door's path. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFallingBlock(EntityChangeBlockEvent event) {
        PhysicsGuard guard = plugin.getPhysicsGuard();
        if (!guard.isActive() || !(event.getEntity() instanceof FallingBlock)) {
            return;
        }
        if (isGuarded(guard, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private static boolean isGuarded(PhysicsGuard guard, Block block) {
        return block != null
                && guard.isGuarded(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }
}
