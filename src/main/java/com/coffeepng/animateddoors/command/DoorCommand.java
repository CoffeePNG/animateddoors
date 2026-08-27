package com.coffeepng.animateddoors.command;

import com.coffeepng.animateddoors.AnimatedDoorsPlugin;
import com.coffeepng.animateddoors.door.DoorAnimator;
import com.coffeepng.animateddoors.door.ToggleResult;
import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.model.Door;
import com.coffeepng.animateddoors.model.DoorType;
import com.coffeepng.animateddoors.preview.PreviewManager;
import com.coffeepng.animateddoors.selection.SelectionManager;
import com.coffeepng.animateddoors.selection.SelectionMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class DoorCommand implements TabExecutor {

    private static final List<String> SUBS = List.of(
            "help", "wand", "mode", "add", "sub", "clear", "finish", "create", "edit", "update",
            "preview", "remove", "list", "info", "hinge", "type", "direction", "slide",
            "powerblock", "trigger", "toggle", "reload");

    private final AnimatedDoorsPlugin plugin;

    public DoorCommand(AnimatedDoorsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(sender);
            case "wand" -> wand(sender);
            case "mode" -> mode(sender, args);
            case "add" -> regionInto(sender, true);
            case "sub", "subtract" -> regionInto(sender, false);
            case "clear" -> clear(sender);
            case "finish", "done" -> finish(sender);
            case "create" -> create(sender, args);
            case "edit" -> edit(sender, args);
            case "update" -> update(sender, args);
            case "preview" -> preview(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "hinge" -> hinge(sender, args);
            case "type" -> type(sender, args);
            case "direction" -> direction(sender, args);
            case "slide" -> slide(sender, args);
            case "powerblock", "power" -> powerBlock(sender, args);
            case "trigger" -> trigger(sender, args);
            case "toggle" -> toggle(sender, args);
            case "reload" -> reload(sender);
            default -> msg(sender, NamedTextColor.RED, "Unknown subcommand. Try /door help");
        }
        return true;
    }

    private void help(CommandSender sender) {
        msg(sender, NamedTextColor.GOLD, "AnimatedDoors — selection:");
        line(sender, "/door wand", "get the selection wand");
        line(sender, "/door mode <block|region>", "pick blocks one by one, or use two corners");
        line(sender, "/door add", "(block mode) add the wand's corner box to your picks");
        line(sender, "/door sub", "(block mode) subtract the wand's corner box from your picks");
        line(sender, "/door finish", "preview exactly which blocks will move");
        line(sender, "/door clear", "clear your selection");
        msg(sender, NamedTextColor.GOLD, "AnimatedDoors — doors:");
        line(sender, "/door create <name>", "create a door from your selection");
        line(sender, "/door edit <name>", "load a door's blocks back into your selection");
        line(sender, "/door update <name>", "replace a door's blocks with your selection");
        line(sender, "/door preview <name> [open|close]", "ghost-run the move without touching blocks");
        line(sender, "/door type <name> <swing|portcullis>", "choose swing or vertical-slide motion");
        line(sender, "/door hinge <name>", "(swing) set the hinge to the block you're looking at");
        line(sender, "/door direction <name> <cw|ccw>", "(swing) set the opening direction");
        line(sender, "/door slide <name> <blocks>", "(portcullis) set vertical distance (+up / -down)");
        line(sender, "/door powerblock <name> [clear|show]", "bind/clear/locate the door's power block");
        line(sender, "/door trigger <name> redstone", "bind the block you're looking at as a redstone trigger");
        line(sender, "/door trigger <name> float", "place a floating click-trigger at the block you're looking at");
        line(sender, "/door trigger <name> clear", "remove this door's triggers");
        line(sender, "/door toggle <name>", "open/close a door");
        line(sender, "/door info <name>", "show a door's details");
        line(sender, "/door list", "list all doors");
        line(sender, "/door remove <name>", "delete a door");
        line(sender, "/door reload", "reload the config");
    }

    // ---- selection ---------------------------------------------------------

    private void wand(CommandSender sender) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        Material wand = Material.matchMaterial(plugin.getWandMaterial());
        if (wand == null) {
            wand = Material.BLAZE_ROD;
        }
        player.getInventory().addItem(new ItemStack(wand));
        SelectionManager.Selection sel = plugin.getSelectionManager().get(player.getUniqueId());
        describeMode(player, sel.mode());
    }

    private void mode(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, NamedTextColor.RED, "Usage: /door mode <block|region>");
            return;
        }
        SelectionMode mode = SelectionMode.fromString(args[1]);
        if (mode == null) {
            msg(sender, NamedTextColor.RED, "Mode must be 'block' or 'region'.");
            return;
        }
        plugin.getSelectionManager().get(player.getUniqueId()).setMode(mode);
        describeMode(player, mode);
    }

    private void describeMode(Player player, SelectionMode mode) {
        if (mode == SelectionMode.BLOCK) {
            msg(player, NamedTextColor.GREEN, "Block mode: right-click a block to add it, left-click to remove it.");
            msg(player, NamedTextColor.GRAY, "Shift-left / shift-right-click set box corners for /door add and /door sub.");
        } else {
            msg(player, NamedTextColor.GREEN, "Region mode: left-click corner 1, right-click corner 2.");
        }
        msg(player, NamedTextColor.GRAY, "When it looks right, run /door finish to preview what will move.");
    }

    private void regionInto(CommandSender sender, boolean adding) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        SelectionManager.Selection sel = plugin.getSelectionManager().get(player.getUniqueId());
        if (!sel.regionComplete()) {
            msg(sender, NamedTextColor.RED, "Set both box corners first (shift-left / shift-right-click with the wand).");
            return;
        }
        World world = player.getWorld();
        if (sel.world() != null && !sel.world().equals(world.getName())) {
            msg(sender, NamedTextColor.RED, "Your selection is in " + sel.world() + ".");
            return;
        }
        List<BlockVector3> cells = new ArrayList<>();
        for (BlockVector3 cell : sel.regionPositions()) {
            // Air never moves anything, so it only clutters the door.
            if (adding && world.getBlockAt(cell.x(), cell.y(), cell.z()).getType().isAir()) {
                continue;
            }
            cells.add(cell);
        }
        int changed = adding ? sel.addAll(cells) : sel.removeAll(cells);
        msg(sender, NamedTextColor.GREEN, (adding ? "Added " : "Removed ") + changed + " block(s). "
                + sel.size() + " block(s) selected.");
        plugin.previewSelection(player, sel);
    }

    private void clear(CommandSender sender) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        plugin.getSelectionManager().get(player.getUniqueId()).clearBlocks();
        plugin.getPreviewManager().cancel(player);
        msg(sender, NamedTextColor.GREEN, "Selection cleared.");
    }

    /** Show exactly which blocks the door would take — the "hit finished" preview. */
    private void finish(CommandSender sender) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        SelectionManager.Selection sel = plugin.getSelectionManager().get(player.getUniqueId());
        List<BlockVector3> cells = resolveSelection(player, sel);
        if (cells == null) {
            return;
        }
        World world = player.getWorld();
        int shown = plugin.getPreviewManager().showSelection(
                player, world, cells, PreviewManager.SELECTION_COLOR, plugin.getPreviewTicks());
        msg(sender, NamedTextColor.GOLD, "Preview: " + shown + " block(s) glowing — that is exactly what will move.");
        msg(sender, NamedTextColor.GRAY, "Wrong blocks in there? Left-click them with the wand to drop them, "
                + "then /door finish again. Happy? /door create <name>.");
        warnContainers(sender, world, cells);
    }

    private void create(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, NamedTextColor.RED, "Usage: /door create <name>");
            return;
        }
        String name = args[1];
        if (plugin.getDoorManager().nameTaken(name)) {
            msg(sender, NamedTextColor.RED, "A door named '" + name + "' already exists.");
            return;
        }
        SelectionManager.Selection sel = plugin.getSelectionManager().get(player.getUniqueId());
        List<BlockVector3> cells = resolveSelection(player, sel);
        if (cells == null) {
            return;
        }
        Door door = new Door(UUID.randomUUID(), name, player.getWorld().getName(), cells);
        plugin.getDoorManager().add(door);
        plugin.saveDoors();
        plugin.getSelectionManager().clear(player.getUniqueId());
        plugin.getPreviewManager().showSelection(player, player.getWorld(), cells,
                PreviewManager.SELECTION_COLOR, plugin.getPreviewTicks());
        msg(sender, NamedTextColor.GREEN, "Created door '" + name + "' from " + cells.size() + " block(s). "
                + "Hinge defaults to its min corner; set it with /door hinge " + name
                + " and pick a direction with /door direction " + name + " cw|ccw.");
        msg(sender, NamedTextColor.GRAY, "Check the motion with /door preview " + name + " before wiring it up.");
        warnContainers(sender, player.getWorld(), cells);
    }

    private void edit(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        if (door.isOpen()) {
            msg(sender, NamedTextColor.RED, "Close '" + door.getName() + "' first — blocks are stored in the closed layout.");
            return;
        }
        World world = plugin.getServer().getWorld(door.getWorld());
        if (world == null) {
            msg(sender, NamedTextColor.RED, "World '" + door.getWorld() + "' isn't loaded.");
            return;
        }
        SelectionManager.Selection sel = plugin.getSelectionManager().get(player.getUniqueId());
        sel.clearBlocks();
        sel.bindWorld(door.getWorld());
        sel.setMode(SelectionMode.BLOCK);
        sel.addAll(door.closedPositions());
        plugin.getPreviewManager().showSelection(player, world, door.closedPositions(),
                PreviewManager.SELECTION_COLOR, plugin.getPreviewTicks());
        msg(sender, NamedTextColor.GREEN, "Loaded " + sel.size() + " block(s) from '" + door.getName()
                + "' into your selection (block mode).");
        msg(sender, NamedTextColor.GRAY, "Left-click strays to drop them, right-click to add, then /door update "
                + door.getName() + ".");
    }

    private void update(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        if (door.isOpen()) {
            msg(sender, NamedTextColor.RED, "Close '" + door.getName() + "' first — blocks are stored in the closed layout.");
            return;
        }
        if (door.isAnimating()) {
            msg(sender, NamedTextColor.RED, "'" + door.getName() + "' is moving right now.");
            return;
        }
        SelectionManager.Selection sel = plugin.getSelectionManager().get(player.getUniqueId());
        List<BlockVector3> cells = resolveSelection(player, sel);
        if (cells == null) {
            return;
        }
        if (!door.getWorld().equals(player.getWorld().getName())) {
            msg(sender, NamedTextColor.RED, "'" + door.getName() + "' lives in " + door.getWorld() + ".");
            return;
        }
        int before = door.blockCount();
        door.setBlocks(cells);
        plugin.saveDoors();
        plugin.getPreviewManager().showSelection(player, player.getWorld(), cells,
                PreviewManager.SELECTION_COLOR, plugin.getPreviewTicks());
        msg(sender, NamedTextColor.GREEN, "'" + door.getName() + "' now has " + cells.size()
                + " block(s) (was " + before + ").");
        warnContainers(sender, player.getWorld(), cells);
    }

    private void preview(CommandSender sender, String[] args) {
        if (!requireUse(sender) || !(sender instanceof Player player)) {
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        boolean opening = !door.isOpen();
        if (args.length >= 3) {
            String want = args[2].toLowerCase(Locale.ROOT);
            switch (want) {
                case "open", "opening" -> opening = true;
                case "close", "closing", "shut" -> opening = false;
                default -> {
                    msg(sender, NamedTextColor.RED, "Preview direction must be 'open' or 'close'.");
                    return;
                }
            }
        }
        int ghosts = plugin.getPreviewManager().showMotion(player, door, opening, plugin.getPreviewHoldTicks());
        if (ghosts == 0) {
            msg(sender, NamedTextColor.RED, "Nothing to preview — '" + door.getName() + "' has no solid blocks where it stands.");
            return;
        }
        msg(sender, NamedTextColor.GOLD, "Ghost-running " + door.getName() + " " + (opening ? "open" : "closed")
                + " with " + ghosts + " block(s). No real blocks are touched.");
        List<BlockVector3> blocked = plugin.getPreviewManager().obstructions(door);
        if (!blocked.isEmpty()) {
            msg(sender, NamedTextColor.GOLD, "Heads up: " + blocked.size() + " block(s) sit where this door lands — "
                    + (plugin.isBlockOnObstruction()
                        ? "it will refuse to move until they're cleared."
                        : "a real toggle would overwrite them (restrictions.obstruction = overwrite)."));
        }
    }

    /**
     * The blocks a player's selection resolves to, or null (with a message sent) if there is nothing usable.
     */
    private List<BlockVector3> resolveSelection(Player player, SelectionManager.Selection sel) {
        World world = player.getWorld();
        if (sel.world() != null && !sel.world().equals(world.getName())) {
            msg(player, NamedTextColor.RED, "Your selection is in " + sel.world() + " — go back there or /door clear.");
            return null;
        }
        List<BlockVector3> source;
        if (sel.mode() == SelectionMode.REGION) {
            if (!sel.regionComplete()) {
                msg(player, NamedTextColor.RED, "Set both corners with the wand first (/door wand).");
                return null;
            }
            source = sel.regionPositions();
        } else {
            source = sel.blocks();
            if (source.isEmpty()) {
                if (sel.regionComplete()) {
                    msg(player, NamedTextColor.RED, "No blocks picked yet — right-click blocks with the wand, "
                            + "or run /door add to take the whole corner box.");
                } else {
                    msg(player, NamedTextColor.RED, "No blocks picked yet — right-click the door's blocks with the wand.");
                }
                return null;
            }
        }
        List<BlockVector3> solid = new ArrayList<>(source.size());
        for (BlockVector3 cell : source) {
            if (!world.getBlockAt(cell.x(), cell.y(), cell.z()).getType().isAir()) {
                solid.add(cell);
            }
        }
        if (solid.isEmpty()) {
            msg(player, NamedTextColor.RED, "Every selected cell is air — there's nothing to move.");
            return null;
        }
        int skipped = source.size() - solid.size();
        if (skipped > 0) {
            msg(player, NamedTextColor.GRAY, "Skipped " + skipped + " air cell(s).");
        }
        return solid;
    }

    private void warnContainers(CommandSender sender, World world, List<BlockVector3> cells) {
        int filled = 0;
        for (BlockVector3 cell : cells) {
            if (DoorAnimator.isFilledContainer(world.getBlockAt(cell.x(), cell.y(), cell.z()))) {
                filled++;
            }
        }
        if (filled > 0) {
            msg(sender, NamedTextColor.GOLD, "Heads up: this selection contains " + filled
                    + " container(s) with items. Door contents aren't preserved when it moves, so the door "
                    + "will refuse to move until they're emptied (see restrictions.block-filled-containers).");
        }
    }

    // ---- doors -------------------------------------------------------------

    private void remove(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        plugin.removeFloatingTrigger(door);
        plugin.getDoorManager().remove(door);
        plugin.saveDoors();
        msg(sender, NamedTextColor.GREEN, "Removed door '" + door.getName() + "'.");
    }

    private void list(CommandSender sender) {
        if (!requireUse(sender)) {
            return;
        }
        if (plugin.getDoorManager().all().isEmpty()) {
            msg(sender, NamedTextColor.GRAY, "No doors defined yet.");
            return;
        }
        msg(sender, NamedTextColor.GOLD, "Doors:");
        for (Door door : plugin.getDoorManager().all()) {
            msg(sender, NamedTextColor.YELLOW, " - " + door.getName() + " (" + door.getWorld() + ", "
                    + door.blockCount() + " blocks, " + (door.isOpen() ? "open" : "closed") + ")");
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (!requireUse(sender)) {
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        BlockVector3 min = door.getMin();
        BlockVector3 max = door.getMax();
        long boxCells = (long) (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
        msg(sender, NamedTextColor.GOLD, "Door '" + door.getName() + "':");
        msg(sender, NamedTextColor.YELLOW, "  world: " + door.getWorld());
        msg(sender, NamedTextColor.YELLOW, "  blocks: " + door.blockCount() + " of " + boxCells + " in its bounding box");
        msg(sender, NamedTextColor.YELLOW, "  bounds: " + min + "  to  " + max);
        msg(sender, NamedTextColor.YELLOW, "  type: " + door.getType().name().toLowerCase(Locale.ROOT));
        if (door.getType() == DoorType.SWING) {
            msg(sender, NamedTextColor.YELLOW, "  hinge: " + door.getHingeX() + ", " + door.getHingeZ());
            msg(sender, NamedTextColor.YELLOW, "  direction: "
                    + (door.getQuarterTurns() >= 0 ? "clockwise" : "counter-clockwise"));
        } else {
            msg(sender, NamedTextColor.YELLOW, "  slide: " + Math.abs(door.getSlide()) + " block(s) "
                    + (door.getSlide() >= 0 ? "up" : "down"));
        }
        msg(sender, NamedTextColor.YELLOW, "  state: " + (door.isOpen() ? "open" : "closed"));
        msg(sender, NamedTextColor.YELLOW, "  power block: "
                + (door.getPowerBlock() == null ? "none" : door.getPowerBlock().toString()));
        msg(sender, NamedTextColor.YELLOW, "  redstone trigger: "
                + (door.getRedstoneTrigger() == null ? "none" : door.getRedstoneTrigger().toString()));
        msg(sender, NamedTextColor.YELLOW, "  floating trigger: "
                + (door.getFloatingTrigger() == null ? "none" : door.getFloatingTrigger().toString()));
    }

    private void hinge(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        Block target = player.getTargetBlockExact(6);
        int x;
        int z;
        if (target != null) {
            x = target.getX();
            z = target.getZ();
        } else {
            x = player.getLocation().getBlockX();
            z = player.getLocation().getBlockZ();
        }
        door.setHinge(x, z);
        plugin.saveDoors();
        msg(sender, NamedTextColor.GREEN, "Hinge for '" + door.getName() + "' set to " + x + ", " + z + ".");
        msg(sender, NamedTextColor.GRAY, "See it swing with /door preview " + door.getName() + ".");
    }

    private void direction(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, NamedTextColor.RED, "Usage: /door direction <name> <cw|ccw>");
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        String dir = args[2].toLowerCase(Locale.ROOT);
        switch (dir) {
            case "cw", "clockwise" -> door.setQuarterTurns(1);
            case "ccw", "counter", "counterclockwise" -> door.setQuarterTurns(-1);
            default -> {
                msg(sender, NamedTextColor.RED, "Direction must be 'cw' or 'ccw'.");
                return;
            }
        }
        plugin.saveDoors();
        msg(sender, NamedTextColor.GREEN, "Direction for '" + door.getName() + "' set to "
                + (door.getQuarterTurns() >= 0 ? "clockwise" : "counter-clockwise") + ".");
    }

    private void type(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, NamedTextColor.RED, "Usage: /door type <name> <swing|portcullis>");
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        DoorType type = DoorType.fromString(args[2]);
        if (type == null) {
            msg(sender, NamedTextColor.RED, "Type must be 'swing' or 'portcullis'.");
            return;
        }
        door.setType(type);
        if (type == DoorType.PORTCULLIS && door.getSlide() == 0) {
            // Default: retract straight up by the door's own height.
            door.setSlide(door.height());
        }
        plugin.saveDoors();
        if (type == DoorType.PORTCULLIS) {
            msg(sender, NamedTextColor.GREEN, "'" + door.getName() + "' is now a portcullis, sliding "
                    + Math.abs(door.getSlide()) + " block(s) " + (door.getSlide() >= 0 ? "up" : "down")
                    + ". Adjust with /door slide " + door.getName() + " <blocks>.");
        } else {
            msg(sender, NamedTextColor.GREEN, "'" + door.getName() + "' is now a swing door. "
                    + "Set the hinge and direction with /door hinge and /door direction.");
        }
    }

    private void slide(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, NamedTextColor.RED, "Usage: /door slide <name> <blocks>  (positive = up, negative = down)");
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        int blocks;
        try {
            blocks = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            msg(sender, NamedTextColor.RED, "Slide distance must be a whole number of blocks.");
            return;
        }
        if (blocks == 0) {
            msg(sender, NamedTextColor.RED, "Slide distance can't be 0.");
            return;
        }
        door.setSlide(blocks);
        if (door.getType() != DoorType.PORTCULLIS) {
            door.setType(DoorType.PORTCULLIS);
        }
        plugin.saveDoors();
        msg(sender, NamedTextColor.GREEN, "'" + door.getName() + "' will slide " + Math.abs(blocks)
                + " block(s) " + (blocks >= 0 ? "up" : "down") + ".");
        msg(sender, NamedTextColor.GRAY, "See it move with /door preview " + door.getName() + ".");
    }

    private void powerBlock(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "set";
        switch (action) {
            case "clear", "remove", "none" -> {
                door.setPowerBlock(null);
                plugin.saveDoors();
                msg(sender, NamedTextColor.GREEN, "Power block for '" + door.getName() + "' cleared.");
            }
            case "show", "find", "where" -> {
                BlockVector3 pos = door.getPowerBlock();
                if (pos == null) {
                    msg(sender, NamedTextColor.GRAY, "'" + door.getName() + "' has no power block.");
                    return;
                }
                World world = plugin.getServer().getWorld(door.getWorld());
                if (world == null) {
                    msg(sender, NamedTextColor.RED, "World '" + door.getWorld() + "' isn't loaded.");
                    return;
                }
                plugin.getPreviewManager().showSelection(player, world, List.of(pos),
                        PreviewManager.SELECTION_COLOR, plugin.getPreviewTicks());
                msg(sender, NamedTextColor.GOLD, "Power block for '" + door.getName() + "' is at " + pos
                        + " (glowing) in " + door.getWorld() + ".");
                describePowerBlockState(sender, door, world, pos);
            }
            case "set" -> {
                Block target = player.getTargetBlockExact(6);
                if (target == null) {
                    msg(sender, NamedTextColor.RED, "Look at the block you want to use as the power block.");
                    return;
                }
                if (!target.getWorld().getName().equals(door.getWorld())) {
                    msg(sender, NamedTextColor.RED, "'" + door.getName() + "' lives in " + door.getWorld() + ".");
                    return;
                }
                Material required = plugin.getPowerBlockMaterial();
                if (plugin.requiresPowerBlockMaterial() && target.getType() != required) {
                    msg(sender, NamedTextColor.RED, "A power block must be " + required.name().toLowerCase(Locale.ROOT)
                            + " (place one there, or set power-block.require-material to false).");
                    return;
                }
                BlockVector3 pos = new BlockVector3(target.getX(), target.getY(), target.getZ());
                Door existing = plugin.powerBlockOwner(door.getWorld(), pos);
                if (existing != null && !existing.getId().equals(door.getId())) {
                    msg(sender, NamedTextColor.RED, "That block is already the power block for '"
                            + existing.getName() + "'.");
                    return;
                }
                door.setPowerBlock(pos);
                door.setPowered(target.isBlockPowered() || target.isBlockIndirectlyPowered());
                plugin.saveDoors();
                plugin.getPreviewManager().showSelection(player, target.getWorld(), List.of(pos),
                        PreviewManager.SELECTION_COLOR, plugin.getPreviewTicks());
                msg(sender, NamedTextColor.GREEN, "Power block for '" + door.getName() + "' set to " + pos + ".");
                msg(sender, NamedTextColor.GRAY, "Power it with a lever, button, or redstone to toggle the door"
                        + (plugin.isClickPowerBlock() ? " — or just right-click it." : "."));
                if (plugin.isProtectPowerBlocks()) {
                    msg(sender, NamedTextColor.GRAY, "It's protected from breaking; sneak-break it to unbind it.");
                }
            }
            default -> msg(sender, NamedTextColor.RED, "Usage: /door powerblock <name> [clear|show]");
        }
    }

    /** Report whether a bound power block is actually usable right now. */
    private void describePowerBlockState(CommandSender sender, Door door, World world, BlockVector3 pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Material required = plugin.getPowerBlockMaterial();
        if (plugin.requiresPowerBlockMaterial() && block.getType() != required) {
            msg(sender, NamedTextColor.RED, "  it is " + block.getType().name().toLowerCase(Locale.ROOT)
                    + ", not " + required.name().toLowerCase(Locale.ROOT) + " — redstone won't drive the door.");
            return;
        }
        msg(sender, NamedTextColor.YELLOW, "  currently "
                + (block.isBlockPowered() || block.isBlockIndirectlyPowered() ? "powered" : "unpowered") + ".");
    }

    private void trigger(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, NamedTextColor.RED, "Usage: /door trigger <name> <redstone|float|clear>");
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        switch (type) {
            case "redstone" -> {
                Block target = player.getTargetBlockExact(6);
                if (target == null) {
                    msg(sender, NamedTextColor.RED, "Look at the block you want to use as the redstone trigger.");
                    return;
                }
                door.setRedstoneTrigger(new BlockVector3(target.getX(), target.getY(), target.getZ()));
                plugin.saveDoors();
                msg(sender, NamedTextColor.GREEN, "Redstone trigger set. Power that block to toggle the door.");
            }
            case "float", "floating" -> {
                Block target = player.getTargetBlockExact(6);
                if (target == null) {
                    msg(sender, NamedTextColor.RED, "Look at the block where the floating trigger should sit.");
                    return;
                }
                door.setFloatingTrigger(new BlockVector3(target.getX(), target.getY(), target.getZ()));
                plugin.spawnFloatingTrigger(door);
                plugin.saveDoors();
                msg(sender, NamedTextColor.GREEN, "Floating click-trigger placed. Right-click that spot to toggle.");
            }
            case "clear" -> {
                door.setRedstoneTrigger(null);
                door.setFloatingTrigger(null);
                plugin.removeFloatingTrigger(door);
                plugin.saveDoors();
                msg(sender, NamedTextColor.GREEN, "Triggers for '" + door.getName() + "' cleared.");
            }
            default -> msg(sender, NamedTextColor.RED, "Trigger type must be 'redstone', 'float', or 'clear'.");
        }
    }

    private void toggle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("animateddoors.toggle")) {
            msg(sender, NamedTextColor.RED, "You don't have permission to toggle doors.");
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        ToggleResult result = plugin.attemptToggle(door, sender instanceof Player player ? player : null);
        if (result != ToggleResult.STARTED) {
            msg(sender, NamedTextColor.GRAY, result.message());
        }
    }

    private void reload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        plugin.reload();
        msg(sender, NamedTextColor.GREEN, "Configuration reloaded.");
    }

    // ---- helpers -----------------------------------------------------------

    private Door resolve(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, NamedTextColor.RED, "Usage: /door " + args[0] + " <name>");
            return null;
        }
        Optional<Door> door = plugin.getDoorManager().byName(args[1]);
        if (door.isEmpty()) {
            msg(sender, NamedTextColor.RED, "No door named '" + args[1] + "'.");
            return null;
        }
        return door.get();
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("animateddoors.admin")) {
            msg(sender, NamedTextColor.RED, "You don't have permission to do that.");
            return false;
        }
        return true;
    }

    private boolean requireUse(CommandSender sender) {
        if (!sender.hasPermission("animateddoors.use")) {
            msg(sender, NamedTextColor.RED, "You don't have permission to do that.");
            return false;
        }
        return true;
    }

    private void line(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(Component.text(cmd, NamedTextColor.YELLOW)
                .append(Component.text(" - " + desc, NamedTextColor.GRAY)));
    }

    private void msg(CommandSender sender, NamedTextColor color, String text) {
        sender.sendMessage(Component.text(text, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("remove", "info", "hinge", "type", "direction", "slide",
                "trigger", "toggle", "preview", "edit", "update", "powerblock").contains(sub)) {
            List<String> names = new ArrayList<>();
            plugin.getDoorManager().all().forEach(d -> names.add(d.getName()));
            return filter(names, args[1]);
        }
        if (args.length == 2 && sub.equals("mode")) {
            return filter(List.of("block", "region"), args[1]);
        }
        if (args.length == 3 && sub.equals("type")) {
            return filter(List.of("swing", "portcullis"), args[2]);
        }
        if (args.length == 3 && sub.equals("direction")) {
            return filter(List.of("cw", "ccw"), args[2]);
        }
        if (args.length == 3 && sub.equals("trigger")) {
            return filter(List.of("redstone", "float", "clear"), args[2]);
        }
        if (args.length == 3 && sub.equals("powerblock")) {
            return filter(List.of("clear", "show"), args[2]);
        }
        if (args.length == 3 && sub.equals("preview")) {
            return filter(List.of("open", "close"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
