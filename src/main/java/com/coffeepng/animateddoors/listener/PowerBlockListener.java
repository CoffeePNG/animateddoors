package com.coffeepng.animateddoors.listener;

import com.coffeepng.animateddoors.AnimatedDoorsPlugin;
import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.model.Door;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

/**
 * Keeps power blocks from being destroyed by accident.
 *
 * <p>A power block is the door's control point: lose it and the door goes dead with no obvious
 * reason why. Breaking one is refused (an admin can still take it by sneaking), and explosions and
 * fire leave it alone.</p>
 */
public class PowerBlockListener implements Listener {

    private final AnimatedDoorsPlugin plugin;

    public PowerBlockListener(AnimatedDoorsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.isProtectPowerBlocks()) {
            return;
        }
        Door door = doorPoweredBy(event.getBlock());
        if (door == null) {
            return;
        }
        if (event.getPlayer().isSneaking() && event.getPlayer().hasPermission("animateddoors.admin")) {
            // Deliberate removal by a builder: let it go, but don't leave a dangling reference.
            door.setPowerBlock(null);
            plugin.saveDoors();
            event.getPlayer().sendMessage(Component.text(
                    "Removed the power block for '" + door.getName() + "'.", NamedTextColor.YELLOW));
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text(
                "That's the power block for door '" + door.getName() + "'. Sneak-break it to unbind it, "
                        + "or move it with /door powerblock " + door.getName() + ".", NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (plugin.isProtectPowerBlocks() && doorPoweredBy(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.isProtectPowerBlocks()) {
            shieldPowerBlocks(event.blockList());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.isProtectPowerBlocks()) {
            shieldPowerBlocks(event.blockList());
        }
    }

    private void shieldPowerBlocks(List<Block> blocks) {
        blocks.removeIf(block -> doorPoweredBy(block) != null);
    }

    /** The door whose power block sits at this block, if any. */
    private Door doorPoweredBy(Block block) {
        BlockVector3 pos = new BlockVector3(block.getX(), block.getY(), block.getZ());
        String world = block.getWorld().getName();
        for (Door door : plugin.getDoorManager().all()) {
            if (door.getWorld().equals(world) && pos.equals(door.getPowerBlock())) {
                return door;
            }
        }
        return null;
    }
}
