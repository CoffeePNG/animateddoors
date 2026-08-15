package com.coffeepng.animateddoors.listener;

import com.coffeepng.animateddoors.AnimatedDoorsPlugin;
import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.model.Door;
import com.coffeepng.animateddoors.selection.SelectionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles the selection wand, right-click-to-toggle on door blocks, and floating trigger clicks.
 */
public class PlayerListener implements Listener {

    private final AnimatedDoorsPlugin plugin;

    public PlayerListener(AnimatedDoorsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();

        // Selection wand takes priority for admins holding it.
        if (isWand(event.getItem()) && player.hasPermission("animateddoors.admin")) {
            handleWand(event, player, block);
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && plugin.isClickToToggle()) {
            BlockVector3 pos = new BlockVector3(block.getX(), block.getY(), block.getZ());
            Optional<Door> door = plugin.getDoorManager().doorAt(block.getWorld().getName(), pos);
            if (door.isPresent() && player.hasPermission("animateddoors.toggle")) {
                event.setCancelled(true);
                toggle(player, door.get());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Interaction interaction)) {
            return;
        }
        UUID doorId = plugin.doorIdOf(interaction);
        if (doorId == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("animateddoors.toggle")) {
            return;
        }
        for (Door door : plugin.getDoorManager().all()) {
            if (door.getId().equals(doorId)) {
                event.setCancelled(true);
                toggle(player, door);
                return;
            }
        }
    }

    private void handleWand(PlayerInteractEvent event, Player player, Block block) {
        event.setCancelled(true);
        SelectionManager.Selection selection = plugin.getSelectionManager().get(player.getUniqueId());
        BlockVector3 pos = new BlockVector3(block.getX(), block.getY(), block.getZ());
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection.pos1 = pos;
            player.sendMessage(Component.text("Position 1 set to " + describe(pos), NamedTextColor.AQUA));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selection.pos2 = pos;
            player.sendMessage(Component.text("Position 2 set to " + describe(pos), NamedTextColor.AQUA));
        }
    }

    private void toggle(Player player, Door door) {
        if (!plugin.attemptToggle(door)) {
            player.sendMessage(Component.text("That door is busy right now.", NamedTextColor.GRAY));
        }
    }

    private boolean isWand(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material wand = Material.matchMaterial(plugin.getWandMaterial());
        return wand != null && item.getType() == wand;
    }

    private static String describe(BlockVector3 pos) {
        return pos.x() + ", " + pos.y() + ", " + pos.z();
    }
}
