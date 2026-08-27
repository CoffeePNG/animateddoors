package com.coffeepng.animateddoors.listener;

import com.coffeepng.animateddoors.AnimatedDoorsPlugin;
import com.coffeepng.animateddoors.door.ToggleResult;
import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.model.Door;
import com.coffeepng.animateddoors.selection.SelectionManager;
import com.coffeepng.animateddoors.selection.SelectionMode;
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
import org.bukkit.event.player.PlayerQuitEvent;
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

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        BlockVector3 pos = new BlockVector3(block.getX(), block.getY(), block.getZ());
        String world = block.getWorld().getName();

        // A power block is a doorknob you can also wire up: clicking it toggles its door.
        if (plugin.isClickPowerBlock()) {
            Door powered = plugin.powerBlockOwner(world, pos);
            if (powered != null && player.hasPermission("animateddoors.toggle")) {
                event.setCancelled(true);
                toggle(player, powered);
                return;
            }
        }

        if (plugin.isClickToToggle()) {
            Optional<Door> door = plugin.getDoorManager().doorAt(world, pos);
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
        if (selection.bindWorld(block.getWorld().getName())) {
            player.sendMessage(Component.text("Selection reset — you changed worlds.", NamedTextColor.GRAY));
        }
        BlockVector3 pos = new BlockVector3(block.getX(), block.getY(), block.getZ());
        boolean left = event.getAction() == Action.LEFT_CLICK_BLOCK;
        boolean right = event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) {
            return;
        }

        // In block mode the plain clicks pick blocks and the shifted ones set box corners,
        // so a big flat door can still be grabbed in one go with /door add.
        boolean corners = selection.mode() == SelectionMode.REGION || player.isSneaking();
        if (corners) {
            if (left) {
                selection.pos1 = pos;
                player.sendMessage(Component.text("Corner 1 set to " + pos, NamedTextColor.AQUA));
            } else {
                selection.pos2 = pos;
                player.sendMessage(Component.text("Corner 2 set to " + pos, NamedTextColor.AQUA));
            }
            if (selection.mode() == SelectionMode.REGION && selection.regionComplete()) {
                plugin.previewSelection(player, selection);
            } else if (selection.regionComplete()) {
                player.sendMessage(Component.text("Box ready — /door add to take it, /door sub to drop it.",
                        NamedTextColor.GRAY));
            }
            return;
        }

        if (right) {
            if (selection.add(pos)) {
                player.sendMessage(Component.text("Added " + pos + " (" + selection.size() + " selected)",
                        NamedTextColor.AQUA));
            } else {
                player.sendMessage(Component.text("Already selected: " + pos, NamedTextColor.GRAY));
            }
        } else {
            if (selection.remove(pos)) {
                player.sendMessage(Component.text("Removed " + pos + " (" + selection.size() + " selected)",
                        NamedTextColor.AQUA));
            } else {
                player.sendMessage(Component.text("Not selected: " + pos, NamedTextColor.GRAY));
            }
        }
        plugin.previewSelection(player, selection);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPreviewManager().cancel(event.getPlayer());
    }

    private void toggle(Player player, Door door) {
        ToggleResult result = plugin.attemptToggle(door, player);
        if (result != ToggleResult.STARTED) {
            player.sendMessage(Component.text(result.message(), NamedTextColor.GRAY));
        }
    }

    private boolean isWand(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material wand = Material.matchMaterial(plugin.getWandMaterial());
        return wand != null && item.getType() == wand;
    }
}
