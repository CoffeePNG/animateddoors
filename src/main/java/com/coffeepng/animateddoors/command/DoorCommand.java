package com.coffeepng.animateddoors.command;

import com.coffeepng.animateddoors.AnimatedDoorsPlugin;
import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.door.ToggleResult;
import com.coffeepng.animateddoors.model.Door;
import com.coffeepng.animateddoors.model.DoorType;
import com.coffeepng.animateddoors.selection.SelectionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
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
            "help", "wand", "create", "remove", "list", "info",
            "hinge", "type", "direction", "slide", "trigger", "toggle", "reload");

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
            case "create" -> create(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "hinge" -> hinge(sender, args);
            case "type" -> type(sender, args);
            case "direction" -> direction(sender, args);
            case "slide" -> slide(sender, args);
            case "trigger" -> trigger(sender, args);
            case "toggle" -> toggle(sender, args);
            case "reload" -> reload(sender);
            default -> msg(sender, NamedTextColor.RED, "Unknown subcommand. Try /door help");
        }
        return true;
    }

    private void help(CommandSender sender) {
        msg(sender, NamedTextColor.GOLD, "AnimatedDoors commands:");
        line(sender, "/door wand", "get the selection wand");
        line(sender, "/door create <name>", "create a door from your selection");
        line(sender, "/door type <name> <swing|portcullis>", "choose swing or vertical-slide motion");
        line(sender, "/door hinge <name>", "(swing) set the hinge to the block you're looking at");
        line(sender, "/door direction <name> <cw|ccw>", "(swing) set the opening direction");
        line(sender, "/door slide <name> <blocks>", "(portcullis) set vertical distance (+up / -down)");
        line(sender, "/door trigger <name> redstone", "bind the block you're looking at as a redstone trigger");
        line(sender, "/door trigger <name> float", "place a floating click-trigger at the block you're looking at");
        line(sender, "/door trigger <name> clear", "remove this door's triggers");
        line(sender, "/door toggle <name>", "open/close a door");
        line(sender, "/door info <name>", "show a door's details");
        line(sender, "/door list", "list all doors");
        line(sender, "/door remove <name>", "delete a door");
        line(sender, "/door reload", "reload the config");
    }

    private void wand(CommandSender sender) {
        if (!requireAdmin(sender) || !(sender instanceof Player player)) {
            return;
        }
        Material wand = Material.matchMaterial(plugin.getWandMaterial());
        if (wand == null) {
            wand = Material.BLAZE_ROD;
        }
        player.getInventory().addItem(new ItemStack(wand));
        msg(sender, NamedTextColor.GREEN, "Left-click a block for corner 1, right-click for corner 2.");
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
        if (!sel.complete()) {
            msg(sender, NamedTextColor.RED, "Select both corners with the wand first (/door wand).");
            return;
        }
        Door door = new Door(UUID.randomUUID(), name, player.getWorld().getName(), sel.min(), sel.max());
        plugin.getDoorManager().add(door);
        plugin.saveDoors();
        plugin.getSelectionManager().clear(player.getUniqueId());
        msg(sender, NamedTextColor.GREEN, "Created door '" + name + "'. Hinge defaults to its min corner; "
                + "set it with /door hinge " + name + " and pick a direction with /door direction " + name + " cw|ccw.");

        int filled = countFilledContainers(player.getWorld(), sel.min(), sel.max());
        if (filled > 0) {
            msg(sender, NamedTextColor.GOLD, "Heads up: this selection contains " + filled
                    + " container(s) with items. Door contents aren't preserved when it moves, so the door "
                    + "will refuse to move until they're emptied (see restrictions.block-filled-containers).");
        }
    }

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
                    + (door.isOpen() ? "open" : "closed") + ")");
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
        msg(sender, NamedTextColor.GOLD, "Door '" + door.getName() + "':");
        msg(sender, NamedTextColor.YELLOW, "  world: " + door.getWorld());
        msg(sender, NamedTextColor.YELLOW, "  min: " + vec(door.getMin()) + "  max: " + vec(door.getMax()));
        msg(sender, NamedTextColor.YELLOW, "  type: " + door.getType().name().toLowerCase());
        if (door.getType() == DoorType.SWING) {
            msg(sender, NamedTextColor.YELLOW, "  hinge: " + door.getHingeX() + ", " + door.getHingeZ());
            msg(sender, NamedTextColor.YELLOW, "  direction: "
                    + (door.getQuarterTurns() >= 0 ? "clockwise" : "counter-clockwise"));
        } else {
            msg(sender, NamedTextColor.YELLOW, "  slide: " + Math.abs(door.getSlide()) + " block(s) "
                    + (door.getSlide() >= 0 ? "up" : "down"));
        }
        msg(sender, NamedTextColor.YELLOW, "  state: " + (door.isOpen() ? "open" : "closed"));
        msg(sender, NamedTextColor.YELLOW, "  redstone trigger: "
                + (door.getRedstoneTrigger() == null ? "none" : vec(door.getRedstoneTrigger())));
        msg(sender, NamedTextColor.YELLOW, "  floating trigger: "
                + (door.getFloatingTrigger() == null ? "none" : vec(door.getFloatingTrigger())));
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
    }

    private int countFilledContainers(org.bukkit.World world, BlockVector3 min, BlockVector3 max) {
        int count = 0;
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    if (com.coffeepng.animateddoors.door.DoorAnimator
                            .isFilledContainer(world.getBlockAt(x, y, z))) {
                        count++;
                    }
                }
            }
        }
        return count;
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
        ToggleResult result = plugin.attemptToggle(door);
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

    private static String vec(BlockVector3 v) {
        return v.x() + ", " + v.y() + ", " + v.z();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("remove", "info", "hinge", "type", "direction", "slide", "trigger", "toggle").contains(sub)) {
            List<String> names = new ArrayList<>();
            plugin.getDoorManager().all().forEach(d -> names.add(d.getName()));
            return filter(names, args[1]);
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
