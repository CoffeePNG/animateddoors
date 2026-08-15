package com.coffeepng.animateddoors.door;

import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.model.Door;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * YAML-backed persistence for doors. One file, one section per door keyed by UUID.
 */
public class DoorStorage {

    private final File file;
    private final Logger logger;

    public DoorStorage(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "doors.yml");
        this.logger = logger;
    }

    public void loadInto(DoorManager manager) {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("doors");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            try {
                Door door = read(UUID.fromString(key), sec);
                manager.add(door);
            } catch (RuntimeException ex) {
                logger.log(Level.WARNING, "Skipping malformed door entry '" + key + "': " + ex.getMessage());
            }
        }
        logger.info("Loaded " + manager.all().size() + " door(s).");
    }

    private Door read(UUID id, ConfigurationSection sec) {
        String name = sec.getString("name", id.toString());
        String world = sec.getString("world");
        BlockVector3 min = readVec(sec.getIntegerList("min"));
        BlockVector3 max = readVec(sec.getIntegerList("max"));
        Door door = new Door(id, name, world, min, max);
        List<Integer> hinge = sec.getIntegerList("hinge");
        if (hinge.size() >= 2) {
            door.setHinge(hinge.get(0), hinge.get(1));
        }
        door.setQuarterTurns(sec.getInt("quarterTurns", 1));
        door.setOpen(sec.getBoolean("open", false));
        if (sec.isList("redstone")) {
            door.setRedstoneTrigger(readVec(sec.getIntegerList("redstone")));
        }
        if (sec.isList("floating")) {
            door.setFloatingTrigger(readVec(sec.getIntegerList("floating")));
        }
        String entity = sec.getString("floatingEntity");
        if (entity != null) {
            door.setFloatingEntityId(UUID.fromString(entity));
        }
        return door;
    }

    public void saveAll(DoorManager manager) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Door door : manager.all()) {
            write(yaml, door);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to save doors.yml", ex);
        }
    }

    private void write(YamlConfiguration yaml, Door door) {
        String base = "doors." + door.getId();
        yaml.set(base + ".name", door.getName());
        yaml.set(base + ".world", door.getWorld());
        yaml.set(base + ".min", vecList(door.getMin()));
        yaml.set(base + ".max", vecList(door.getMax()));
        yaml.set(base + ".hinge", List.of(door.getHingeX(), door.getHingeZ()));
        yaml.set(base + ".quarterTurns", door.getQuarterTurns());
        yaml.set(base + ".open", door.isOpen());
        yaml.set(base + ".redstone", door.getRedstoneTrigger() == null ? null : vecList(door.getRedstoneTrigger()));
        yaml.set(base + ".floating", door.getFloatingTrigger() == null ? null : vecList(door.getFloatingTrigger()));
        yaml.set(base + ".floatingEntity",
                door.getFloatingEntityId() == null ? null : door.getFloatingEntityId().toString());
    }

    private static BlockVector3 readVec(List<Integer> list) {
        if (list.size() < 3) {
            throw new IllegalArgumentException("expected 3 coordinates, got " + list.size());
        }
        return new BlockVector3(list.get(0), list.get(1), list.get(2));
    }

    private static List<Integer> vecList(BlockVector3 v) {
        return List.of(v.x(), v.y(), v.z());
    }
}
