package com.coffeepng.dynamicdoors.command;

import com.coffeepng.dynamicdoors.DynamicDoorsPlugin;
import com.coffeepng.dynamicdoors.door.DoorAnimator;
import com.coffeepng.dynamicdoors.door.DoorObstruction;
import com.coffeepng.dynamicdoors.door.ToggleResult;
import com.coffeepng.dynamicdoors.model.BlockVector3;
import com.coffeepng.dynamicdoors.model.Door;
import com.coffeepng.dynamicdoors.model.DoorType;
import com.coffeepng.dynamicdoors.preview.PreviewManager;
import com.coffeepng.dynamicdoors.selection.AttachmentScanner;
import com.coffeepng.dynamicdoors.selection.SelectionManager;
import com.coffeepng.dynamicdoors.selection.SelectionMode;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DoorCommand implements TabExecutor {

    private static final List<String> SUBS = List.of(
            "help", "wand", "mode", "add", "sub", "clear", "finish", "create", "edit", "update",
            "attach", "select", "preview", "remove", "list", "info", "hinge", "type", "direction", "slide",
            "powerblock", "trigger", "toggle", "reload");

    /** How far away a builder can point at a hinge block. */
    private static final int HINGE_REACH = 8;

    private final DynamicDoorsPlugin plugin;

    public DoorCommand(DynamicDoorsPlugin plugin) {
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
            case "attach" -> attach(sender);
            case "finish", "done" -> finish(sender);
            case "select", "use" -> select(sender, args);
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
        msg(sender, NamedTextColor.GOLD, "DynamicDoors — selection:");
        line(sender, "/door wand", "get the selection wand");
        line(sender, "/door mode <block|region>", "pick blocks one by one, or use two corners");
        line(sender, "/door add", "(block mode) add the wand's corner box to your picks");
        line(sender, "/door sub", "(block mode) subtract the wand's corner box from your picks");
        line(sender, "/door attach", "pull in torches, buttons, signs … stuck to your selection");
        line(sender, "/door finish", "preview exactly which blocks will move");
        line(sender, "/door clear", "clear your selection");
        msg(sender, NamedTextColor.GOLD, "DynamicDoors — doors:");
        line(sender, "/door select <name>", "work on this door; other commands can then omit the name");
        line(sender, "/door create <name>", "create a door from your selection");
        line(sender, "/door edit <name>", "load a door's blocks back into your selection");
        line(sender, "/door update <name>", "replace a door's blocks with your selection");
        line(sender, "/door preview <name> [open|close]", "ghost-run the move without touching blocks");
        line(sender, "/door type <name> <swing|portcullis>", "choose swing or vertical-slide motion");
        line(sender, "/door hinge <name> [here|show|<x> <z>]", "(swing) set or show the column the door pivots around");
        line(sender, "/door direction <name> <cw|ccw>", "(swing) set the opening direction");
        line(sender, "/door slide <name> <blocks>", "(portcullis) set vertical distance (+up / -down)");
        line(sender, "/door powerblock <name> [wand|clear|show]", "bind (by look or wand), clear or locate the power block");
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
        if (plugin.isGivePowerBlockWithWand()) {
            player.getInventory().addItem(new ItemStack(plugin.getPowerBlockMaterial()));
            msg(sender, NamedTextColor.GRAY, "Also handed you a "
                    + plugin.getPowerBlockMaterial().name().toLowerCase(Locale.ROOT)
                    + ": place it where you want the door's control block, then "
                    + "/door powerblock <name> wand and click it.");
        }
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

    /** Pull the bits stuck to the selection — torches, buttons, signs, ladders — into it. */
    private void attach(CommandSender sender) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        SelectionManager.Selection sel = plugin.getSelectionManager().get(player.getUniqueId());
        List<BlockVector3> cells = resolveSelection(player, sel);
        if (cells == null) {
            return;
        }
        World world = player.getWorld();
        List<BlockVector3> found = AttachmentScanner.find(world, cells, plugin.getAttachLimit());
        if (found.isEmpty()) {
            msg(sender, NamedTextColor.GRAY, "Nothing else looks attached to your selection.");
            return;
        }
        if (sel.mode() == SelectionMode.REGION) {
            // Attached blocks sit outside the box by definition, so the box can't describe the result.
            sel.setMode(SelectionMode.BLOCK);
            sel.addAll(cells);
            msg(sender, NamedTextColor.GRAY, "Switched you to block mode — the attached blocks sit outside your box.");
        }
        sel.addAll(found);
        List<BlockVector3> all = sel.blocks();
        plugin.getPreviewManager().showSelection(player, world, all,
                PreviewManager.SELECTION_COLOR, plugin.getPreviewTicks());
        msg(sender, NamedTextColor.GREEN, "Added " + found.size() + " attached block(s): "
                + summarise(world, found) + ". " + all.size() + " block(s) selected.");
        msg(sender, NamedTextColor.GRAY, "Anything it grabbed that isn't part of the door? Left-click it to drop it.");
    }

    /** "2 torches, 1 lever" — a short readable tally of what a scan turned up. */
    private String summarise(World world, List<BlockVector3> cells) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BlockVector3 cell : cells) {
            String name = world.getBlockAt(cell.x(), cell.y(), cell.z())
                    .getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            counts.merge(name, 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (parts.size() == 5) {
                parts.add("and " + (counts.size() - 5) + " more kind(s)");
                break;
            }
            parts.add(entry.getValue() + "x " + entry.getKey());
        }
        return String.join(", ", parts);
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
        previewObstructions(sender, player, world, cells);
        List<BlockVector3> attached = AttachmentScanner.find(world, cells, plugin.getAttachLimit());
        if (!attached.isEmpty()) {
            msg(sender, NamedTextColor.GOLD, attached.size() + " block(s) look attached to this door but aren't "
                    + "in it (" + summarise(world, attached) + ") — /door attach pulls them in.");
        }
        warnContainers(sender, world, cells);
    }

    /**
     * Paint the blocks in the way of this selection red, on top of the selection preview.
     *
     * <p>Where a door lands depends on its hinge, direction and slide, so this can only be worked
     * out once those exist: it uses the door being edited when the selection belongs to one, and
     * otherwise says what's still missing rather than guessing an arc.</p>
     */
    private void previewObstructions(CommandSender sender, Player player, World world, List<BlockVector3> cells) {
        Door motion = editedDoor(player, cells);
        if (motion == null) {
            msg(sender, NamedTextColor.GRAY, "Blocks in the way can't be checked yet — that depends on the hinge "
                    + "and direction. They'll be shown in red as soon as the door exists.");
            return;
        }
        // The selection may differ from the door's current blocks, so measure the selection itself.
        Door provisional = new Door(UUID.randomUUID(), motion.getName(), motion.getWorld(), cells);
        provisional.setHinge(motion.getHingeX(), motion.getHingeZ());
        provisional.setType(motion.getType());
        provisional.setQuarterTurns(motion.getQuarterTurns());
        provisional.setSlide(motion.getSlide());
        provisional.setOpen(motion.isOpen());
        reportObstructions(sender, player, world, provisional);
    }

    /** Outline a real door's obstructions in red over whatever is already being previewed. */
    private void reportObstructions(CommandSender sender, Player player, World world, Door door) {
        List<BlockVector3> blocked = DoorObstruction.find(world, door, !door.isOpen());
        if (blocked.isEmpty()) {
            msg(sender, NamedTextColor.GRAY, "Nothing is in the way of where it lands.");
            return;
        }
        plugin.getPreviewManager().overlay(player, world, blocked,
                PreviewManager.OBSTRUCTION_COLOR, plugin.getPreviewTicks());
        msg(sender, NamedTextColor.RED, blocked.size() + " block(s) in red are where it would land — "
                + (plugin.isBlockOnObstruction()
                    ? "it will refuse to move until they're cleared."
                    : "a toggle would overwrite them (restrictions.obstruction = overwrite)."));
    }

    /** The door this selection is an edit of, if it overlaps the player's selected door. */
    private Door editedDoor(Player player, List<BlockVector3> cells) {
        Door active = plugin.getActiveDoor(player);
        if (active == null || !active.getWorld().equals(player.getWorld().getName())) {
            return null;
        }
        for (BlockVector3 cell : cells) {
            if (active.containsClosed(cell)) {
                return active;
            }
        }
        return null;
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
        plugin.setActiveDoor(player, door);
        plugin.getSelectionManager().clear(player.getUniqueId());
        plugin.getPreviewManager().showSelection(player, player.getWorld(), cells,
                PreviewManager.SELECTION_COLOR, plugin.getPreviewTicks());
        msg(sender, NamedTextColor.GREEN, "Created door '" + name + "' from " + cells.size() + " block(s). "
                + "Hinge defaults to its min corner; set it with /door hinge " + name
                + " and pick a direction with /door direction " + name + " cw|ccw.");
        reportObstructions(sender, player, player.getWorld(), door);
        msg(sender, NamedTextColor.GRAY, "Check the motion with /door preview " + name + " before wiring it up.");
        warnContainers(sender, player.getWorld(), cells);
    }

    private void edit(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        Target subject = target(sender, args);
        if (subject == null) {
            return;
        }
        Door door = subject.door();
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
        Target subject = target(sender, args);
        if (subject == null) {
            return;
        }
        Door door = subject.door();
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
        reportObstructions(sender, player, player.getWorld(), door);
        warnContainers(sender, player.getWorld(), cells);
    }

    private void preview(CommandSender sender, String[] args) {
        if (!requireUse(sender) || !(sender instanceof Player player)) {
            return;
        }
        Target subject = target(sender, args);
        if (subject == null) {
            return;
        }
        Door door = subject.door();
        String[] rest = subject.rest();
        boolean opening = !door.isOpen();
        if (rest.length >= 1) {
            String want = rest[0].toLowerCase(Locale.ROOT);
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
        World world = plugin.getServer().getWorld(door.getWorld());
        if (world != null) {
            List<BlockVector3> blocked = DoorObstruction.find(world, door, opening);
            if (!blocked.isEmpty()) {
                plugin.getPreviewManager().overlay(player, world, blocked,
                        PreviewManager.OBSTRUCTION_COLOR, plugin.getPreviewTicks());
                msg(sender, NamedTextColor.RED, blocked.size() + " block(s) in red sit where this door lands — "
                        + (plugin.isBlockOnObstruction()
                            ? "it will refuse to move until they're cleared."
                            : "a real toggle would overwrite them (restrictions.obstruction = overwrite)."));
            }
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
        // Deleting is the one thing that always wants the name spelled out.
        if (args.length < 2) {
            msg(sender, NamedTextColor.RED, "Name the door to delete: /door remove <name>.");
            return;
        }
        Target subject = target(sender, args);
        if (subject == null) {
            return;
        }
        Door door = subject.door();
        plugin.removeFloatingTrigger(door);
        plugin.forgetDoor(door);
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
        Target subject = target(sender, args);
        if (subject == null) {
            return;
        }
        Door door = subject.door();
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

    /**
     * Set (or show) the vertical column a swing door pivots around.
     *
     * <p>Only X and Z matter — the door turns around the Y axis, so the hinge is the whole column
     * at that (x, z), not one particular block in it.</p>
     */
    private void hinge(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        Target subject = target(sender, args);
        if (subject == null) {
            return;
        }
        Door door = subject.door();
        String[] rest = subject.rest();

        Integer x = null;
        Integer z = null;
        if (rest.length >= 2) {
            try {
                x = Integer.parseInt(rest[0]);
                z = Integer.parseInt(rest[1]);
            } catch (NumberFormatException ex) {
                msg(sender, NamedTextColor.RED, "Usage: /door hinge " + door.getName() + " [here|show|<x> <z>]");
                return;
            }
        } else if (rest.length == 1) {
            String what = rest[0].toLowerCase(Locale.ROOT);
            switch (what) {
                case "show", "where" -> {
                    showHinge(sender, player, door);
                    return;
                }
                case "here", "me" -> {
                    x = player.getLocation().getBlockX();
                    z = player.getLocation().getBlockZ();
                }
                default -> {
                    msg(sender, NamedTextColor.RED, "Usage: /door hinge " + door.getName() + " [here|show|<x> <z>]");
                    return;
                }
            }
        } else {
            Block target = player.getTargetBlockExact(HINGE_REACH);
            if (target == null) {
                // Guessing the player's own position here silently put hinges in absurd places.
                msg(sender, NamedTextColor.RED, "Look at the block the door should pivot around (within "
                        + HINGE_REACH + " blocks), or use /door hinge " + door.getName()
                        + " here, or give coordinates: /door hinge " + door.getName() + " <x> <z>.");
                return;
            }
            if (!target.getWorld().getName().equals(door.getWorld())) {
                msg(sender, NamedTextColor.RED, "'" + door.getName() + "' lives in " + door.getWorld() + ".");
                return;
            }
            x = target.getX();
            z = target.getZ();
        }

        door.setHinge(x, z);
        plugin.saveDoors();
        msg(sender, NamedTextColor.GREEN, "Hinge for '" + door.getName() + "' set to the column at " + x + ", " + z + ".");
        if (door.getType() != DoorType.SWING) {
            msg(sender, NamedTextColor.GRAY, "'" + door.getName() + "' is a portcullis, so the hinge is unused "
                    + "until you switch it back with /door type " + door.getName() + " swing.");
        }
        showHinge(sender, player, door);
    }

    /** Mark the hinge column and say whether it actually runs through the door. */
    private void showHinge(CommandSender sender, Player player, Door door) {
        World world = plugin.getServer().getWorld(door.getWorld());
        if (world == null) {
            msg(sender, NamedTextColor.RED, "World '" + door.getWorld() + "' isn't loaded.");
            return;
        }
        int hingeX = door.getHingeX();
        int hingeZ = door.getHingeZ();

        // Mark the column over the door's own height so it is visible from where the builder stands.
        List<BlockVector3> column = new ArrayList<>();
        boolean insideDoor = false;
        for (int y = door.getMin().y(); y <= door.getMax().y(); y++) {
            BlockVector3 cell = new BlockVector3(hingeX, y, hingeZ);
            column.add(cell);
            if (door.containsClosed(cell)) {
                insideDoor = true;
            }
        }
        plugin.getPreviewManager().showMarkers(player, world, column,
                Material.GOLD_BLOCK.createBlockData(), PreviewManager.HINGE_COLOR, plugin.getPreviewTicks());
        msg(sender, NamedTextColor.GOLD, "Hinge column for '" + door.getName() + "': " + hingeX + ", " + hingeZ
                + " (marked in gold, " + column.size() + " block(s) tall).");
        if (!insideDoor) {
            msg(sender, NamedTextColor.GRAY, "That column isn't part of the door, so it swings around a point "
                    + "outside itself. That's allowed — it just sweeps a wider arc. Pick a block of the door "
                    + "itself for a normal hinge.");
        }
        msg(sender, NamedTextColor.GRAY, "See it swing with /door preview " + door.getName() + ".");
    }

    private void direction(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Target subject = target(sender, args);
        if (subject == null) {
            return;
        }
        Door door = subject.door();
        String[] rest = subject.rest();
        if (rest.length < 1) {
            msg(sender, NamedTextColor.RED, "Usage: /door direction [name] <cw|ccw>");
            return;
        }
        String dir = rest[0].toLowerCase(Locale.ROOT);
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
        Target subject = target(sender, args);
        if (subject == null) {
            return;
        }
        Door door = subject.door();
        String[] rest = subject.rest();
        if (rest.length < 1) {
            msg(sender, NamedTextColor.RED, "Usage: /door type [name] <swing|portcullis>");
            return;
        }
        DoorType type = DoorType.fromString(rest[0]);
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
        Target subject = target(sender, args);
        if (subject == null) {
            return;
        }
        Door door = subject.door();
        String[] rest = subject.rest();
        if (rest.length < 1) {
            msg(sender, NamedTextColor.RED, "Usage: /door slide [name] <blocks>  (positive = up, negative = down)");
            return;
        }
        int blocks;
        try {
            blocks = Integer.parseInt(rest[0]);
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
        Target subject = target(sender, args);
        if (subject == null) {
            return;
        }
        Door door = subject.door();
        String[] rest = subject.rest();
        String action = rest.length >= 1 ? rest[0].toLowerCase(Locale.ROOT) : "set";
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
            case "wand" -> {
                plugin.getSelectionManager().get(player.getUniqueId()).setPendingPowerBlock(door.getId());
                msg(sender, NamedTextColor.GREEN, "Right-click a block with the wand to make it the power block "
                        + "for '" + door.getName() + "'. Left-click to cancel.");
            }
            case "set" -> {
                Block target = player.getTargetBlockExact(HINGE_REACH);
                if (target == null) {
                    msg(sender, NamedTextColor.RED, "Look at the block you want to use as the power block, "
                            + "or run /door powerblock " + door.getName() + " wand to click it with the wand.");
                    return;
                }
                plugin.bindPowerBlock(player, door, target);
            }
            default -> msg(sender, NamedTextColor.RED, "Usage: /door powerblock <name> [wand|clear|show]");
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
        Target subject = target(sender, args);
        if (subject == null) {
            return;
        }
        Door door = subject.door();
        String[] rest = subject.rest();
        if (rest.length < 1) {
            msg(sender, NamedTextColor.RED, "Usage: /door trigger [name] <redstone|float|clear>");
            return;
        }
        String type = rest[0].toLowerCase(Locale.ROOT);
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
        if (!sender.hasPermission("dynamicdoors.toggle")) {
            msg(sender, NamedTextColor.RED, "You don't have permission to toggle doors.");
            return;
        }
        Target subject = target(sender, args);
        if (subject == null) {
            return;
        }
        Door door = subject.door();
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

    /** A resolved door plus the arguments that follow it. */
    private record Target(Door door, String[] rest) {
    }

    /**
     * Work out which door a command is about.
     *
     * <p>A name may be given as the first argument, and doing so also makes that door the player's
     * selected one. Leave it out and the command applies to whatever {@code /door select} chose, so
     * a run of edits doesn't repeat the name. Anything after the name (or all of it, when the name
     * is omitted) is handed back as {@code rest} for the subcommand's own arguments.</p>
     *
     * @return the target, or null if none could be resolved (a message has been sent)
     */
    private Target target(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (args.length >= 2) {
            Optional<Door> named = plugin.getDoorManager().byName(args[1]);
            if (named.isPresent()) {
                if (player != null) {
                    plugin.setActiveDoor(player, named.get());
                }
                return new Target(named.get(), java.util.Arrays.copyOfRange(args, 2, args.length));
            }
        }
        Door active = player == null ? null : plugin.getActiveDoor(player);
        if (active != null) {
            // No name matched, so everything after the subcommand belongs to the subcommand.
            return new Target(active, java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length >= 2) {
            msg(sender, NamedTextColor.RED, "No door named '" + args[1] + "'"
                    + (player == null ? "." : ", and no door selected. Pick one with /door select <name>."));
        } else {
            msg(sender, NamedTextColor.RED, "Which door? Give a name, or select one with /door select <name>.");
        }
        return null;
    }

    /** Choose the door that later commands apply to by default. */
    private void select(CommandSender sender, String[] args) {
        if (!requireUse(sender) || !(sender instanceof Player player)) {
            return;
        }
        if (args.length < 2) {
            Door active = plugin.getActiveDoor(player);
            if (active == null) {
                msg(sender, NamedTextColor.GRAY, "No door selected. /door select <name> picks one; "
                        + "after that you can leave the name off other commands.");
            } else {
                msg(sender, NamedTextColor.GOLD, "Selected door: " + active.getName()
                        + " (" + active.blockCount() + " blocks, " + (active.isOpen() ? "open" : "closed") + ")");
            }
            return;
        }
        String name = args[1];
        if (name.equalsIgnoreCase("none") || name.equalsIgnoreCase("clear")) {
            plugin.clearActiveDoor(player);
            msg(sender, NamedTextColor.GREEN, "Door selection cleared.");
            return;
        }
        Optional<Door> door = plugin.getDoorManager().byName(name);
        if (door.isEmpty()) {
            msg(sender, NamedTextColor.RED, "No door named '" + name + "'.");
            return;
        }
        plugin.setActiveDoor(player, door.get());
        msg(sender, NamedTextColor.GREEN, "Working on '" + door.get().getName()
                + "'. Other commands can now leave the name off — /door toggle, /door hinge, /door preview, …");
        World world = plugin.getServer().getWorld(door.get().getWorld());
        if (world != null) {
            plugin.getPreviewManager().showSelection(player, world, door.get().closedPositions(),
                    PreviewManager.SELECTION_COLOR, plugin.getPreviewTicks());
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("dynamicdoors.admin")) {
            msg(sender, NamedTextColor.RED, "You don't have permission to do that.");
            return false;
        }
        return true;
    }

    private boolean requireUse(CommandSender sender) {
        if (!sender.hasPermission("dynamicdoors.use")) {
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
                "trigger", "toggle", "preview", "edit", "update", "powerblock", "select").contains(sub)) {
            List<String> options = new ArrayList<>();
            plugin.getDoorManager().all().forEach(d -> options.add(d.getName()));
            if (sub.equals("select")) {
                options.add("none");
            } else {
                // The name is optional when a door is selected, so offer this subcommand's values too.
                options.addAll(valuesFor(sub));
            }
            return filter(options, args[1]);
        }
        if (args.length == 2 && sub.equals("mode")) {
            return filter(List.of("block", "region"), args[1]);
        }
        if (args.length == 3) {
            return filter(valuesFor(sub), args[2]);
        }
        return List.of();
    }

    /** The non-name arguments a subcommand accepts, offered when the door name is left off. */
    private static List<String> valuesFor(String sub) {
        return switch (sub) {
            case "type" -> List.of("swing", "portcullis");
            case "direction" -> List.of("cw", "ccw");
            case "trigger" -> List.of("redstone", "float", "clear");
            case "hinge" -> List.of("here", "show");
            case "powerblock" -> List.of("wand", "clear", "show");
            case "preview" -> List.of("open", "close");
            default -> List.of();
        };
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
