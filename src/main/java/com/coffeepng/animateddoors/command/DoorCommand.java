package com.coffeepng.animateddoors.command;

import com.coffeepng.animateddoors.AnimatedDoorsPlugin;
import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.door.ToggleResult;
import com.coffeepng.animateddoors.model.Door;
import com.coffeepng.animateddoors.model.DoorType;
import com.coffeepng.animateddoors.selection.SelectionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
        line(sender, "/door type <name> <swing|portcullis|sliding>", "choose the door's motion");
        line(sender, "/door hinge <name>", "(swing) set the hinge to the block you're looking at");
        line(sender, "/door direction <name> <cw|ccw>", "(swing) set the opening direction");
        line(sender, "/door direction <name> <north|south|east|west>", "(sliding) set the direction it retracts");
        line(sender, "/door slide <name> <blocks>", "set the travel distance (portcullis: +up / -down)");
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
        ItemStack wand = plugin.createWand();
        for (ItemStack leftover : player.getInventory().addItem(wand).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
        player.sendMessage(Component.text("Received ", NamedTextColor.GREEN)
                .append(wand.displayName())
                .append(Component.text(".", NamedTextColor.GREEN)));
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
        msg(sender, NamedTextColor.GREEN, "Created door '" + name + "' as a swing door. Its hinge defaults to "
                + "the min corner; set it with /door hinge " + name + " and pick a direction with /door direction "
                + name + " cw|ccw. For a different motion, use /door type " + name + " portcullis|sliding.");

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
        switch (door.getType()) {
            case SWING -> {
                msg(sender, NamedTextColor.YELLOW, "  hinge: " + door.getHingeX() + ", " + door.getHingeZ());
                msg(sender, NamedTextColor.YELLOW, "  direction: "
                        + (door.getQuarterTurns() >= 0 ? "clockwise" : "counter-clockwise"));
            }
            case PORTCULLIS -> msg(sender, NamedTextColor.YELLOW, "  slide: " + Math.abs(door.getSlide())
                    + " block(s) " + (door.getSlide() >= 0 ? "up" : "down"));
            case SLIDING -> msg(sender, NamedTextColor.YELLOW, "  slide: " + door.getSlideDistance()
                    + " block(s) " + door.getSlideFace().name().toLowerCase(Locale.ROOT));
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
            msg(sender, NamedTextColor.RED, "Usage: /door direction <name> " + directionOptions(null));
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        String dir = args[2].toLowerCase(Locale.ROOT);
        switch (door.getType()) {
            case SWING -> {
                switch (dir) {
                    case "cw", "clockwise" -> door.setQuarterTurns(1);
                    case "ccw", "counter", "counterclockwise" -> door.setQuarterTurns(-1);
                    default -> {
                        msg(sender, NamedTextColor.RED, "A swing door's direction must be 'cw' or 'ccw'.");
                        return;
                    }
                }
                plugin.saveDoors();
                msg(sender, NamedTextColor.GREEN, "Direction for '" + door.getName() + "' set to "
                        + (door.getQuarterTurns() >= 0 ? "clockwise" : "counter-clockwise") + ".");
            }
            case PORTCULLIS -> {
                int distance = Math.abs(door.getSlide());
                if (distance == 0) {
                    distance = door.height();
                }
                switch (dir) {
                    case "up" -> door.setSlide(distance);
                    case "down" -> door.setSlide(-distance);
                    default -> {
                        msg(sender, NamedTextColor.RED, "A portcullis' direction must be 'up' or 'down'.");
                        return;
                    }
                }
                plugin.saveDoors();
                msg(sender, NamedTextColor.GREEN, "'" + door.getName() + "' will now retract " + distance
                        + " block(s) " + (door.getSlide() >= 0 ? "up" : "down") + ".");
            }
            case SLIDING -> {
                BlockFace face = horizontalFace(dir);
                if (face == null) {
                    msg(sender, NamedTextColor.RED,
                            "A sliding door's direction must be 'north', 'south', 'east' or 'west'.");
                    return;
                }
                door.setSlideFace(face);
                if (door.getSlideDistance() == 0) {
                    applyDefaultSlide(door);
                    door.setSlideFace(face);
                }
                plugin.saveDoors();
                msg(sender, NamedTextColor.GREEN, "'" + door.getName() + "' will now retract "
                        + door.getSlideDistance() + " block(s) " + face.name().toLowerCase(Locale.ROOT) + ".");
            }
        }
    }

    /** Parse a cardinal compass direction, or null if the token isn't one. */
    private static BlockFace horizontalFace(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "north", "n" -> BlockFace.NORTH;
            case "south", "s" -> BlockFace.SOUTH;
            case "east", "e" -> BlockFace.EAST;
            case "west", "w" -> BlockFace.WEST;
            default -> null;
        };
    }

    /** The direction tokens that make sense for a door of the given type (all of them if null). */
    private static List<String> directionValues(DoorType type) {
        if (type == null) {
            return List.of("cw", "ccw", "up", "down", "north", "south", "east", "west");
        }
        return switch (type) {
            case SWING -> List.of("cw", "ccw");
            case PORTCULLIS -> List.of("up", "down");
            case SLIDING -> List.of("north", "south", "east", "west");
        };
    }

    private static String directionOptions(DoorType type) {
        return "<" + String.join("|", directionValues(type)) + ">";
    }

    private void type(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, NamedTextColor.RED, "Usage: /door type <name> <swing|portcullis|sliding>");
            return;
        }
        Door door = resolve(sender, args);
        if (door == null) {
            return;
        }
        DoorType type = DoorType.fromString(args[2]);
        if (type == null) {
            msg(sender, NamedTextColor.RED, "Type must be 'swing', 'portcullis' or 'sliding'.");
            return;
        }
        door.setType(type);
        switch (type) {
            case PORTCULLIS -> {
                if (door.getSlide() == 0) {
                    // Default: retract straight up by the door's own height.
                    door.setSlide(door.height());
                }
                plugin.saveDoors();
                msg(sender, NamedTextColor.GREEN, "'" + door.getName() + "' is now a portcullis, sliding "
                        + Math.abs(door.getSlide()) + " block(s) " + (door.getSlide() >= 0 ? "up" : "down")
                        + ". Adjust with /door slide " + door.getName() + " <blocks>.");
            }
            case SLIDING -> {
                if (door.getSlideDistance() == 0) {
                    // Default: retract sideways along the wall the door sits in, by its own width.
                    applyDefaultSlide(door);
                }
                plugin.saveDoors();
                msg(sender, NamedTextColor.GREEN, "'" + door.getName() + "' is now a sliding door, retracting "
                        + door.getSlideDistance() + " block(s) "
                        + door.getSlideFace().name().toLowerCase(Locale.ROOT)
                        + ". Adjust with /door slide " + door.getName() + " <blocks> and /door direction "
                        + door.getName() + " <north|south|east|west>.");
            }
            case SWING -> {
                plugin.saveDoors();
                msg(sender, NamedTextColor.GREEN, "'" + door.getName() + "' is now a swing door. "
                        + "Set the hinge and direction with /door hinge and /door direction.");
            }
        }
    }

    /**
     * Pick a sensible default slide for a door that has just become {@link DoorType#SLIDING}:
     * it retracts along its own longest horizontal axis, by its width on that axis, so a wall-shaped
     * door disappears into the wall beside it.
     */
    private void applyDefaultSlide(Door door) {
        if (door.widthX() >= door.widthZ()) {
            door.setSlideFace(BlockFace.EAST);
            door.setSlideDistance(door.widthX());
        } else {
            door.setSlideFace(BlockFace.SOUTH);
            door.setSlideDistance(door.widthZ());
        }
    }

    private void slide(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, NamedTextColor.RED,
                    "Usage: /door slide <name> <blocks>  (portcullis: positive = up, negative = down)");
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
        if (door.getType() == DoorType.SLIDING) {
            // A sliding door's direction lives in its slide face, so a negative distance flips that face.
            if (blocks < 0) {
                door.setSlideFace(door.getSlideFace().getOppositeFace());
            }
            door.setSlideDistance(Math.abs(blocks));
            plugin.saveDoors();
            msg(sender, NamedTextColor.GREEN, "'" + door.getName() + "' will slide " + door.getSlideDistance()
                    + " block(s) " + door.getSlideFace().name().toLowerCase(Locale.ROOT) + ".");
            return;
        }
        // Swing doors fall through to a portcullis: a vertical slide is the only motion a distance means here.
        door.setSlide(blocks);
        door.setType(DoorType.PORTCULLIS);
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
            return filter(List.of("swing", "portcullis", "sliding"), args[2]);
        }
        if (args.length == 3 && sub.equals("direction")) {
            // Offer only the directions that apply to the named door, when it resolves.
            DoorType type = plugin.getDoorManager().byName(args[1]).map(Door::getType).orElse(null);
            return filter(directionValues(type), args[2]);
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
