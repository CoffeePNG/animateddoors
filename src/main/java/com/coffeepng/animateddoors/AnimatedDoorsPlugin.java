package com.coffeepng.animateddoors;

import com.coffeepng.animateddoors.command.DoorCommand;
import com.coffeepng.animateddoors.door.DoorAnimator;
import com.coffeepng.animateddoors.door.DoorManager;
import com.coffeepng.animateddoors.door.DoorStorage;
import com.coffeepng.animateddoors.door.ToggleResult;
import com.coffeepng.animateddoors.listener.RedstoneListener;
import com.coffeepng.animateddoors.listener.PlayerListener;
import com.coffeepng.animateddoors.model.BlockVector3;
import com.coffeepng.animateddoors.model.Door;
import com.coffeepng.animateddoors.preview.PreviewManager;
import com.coffeepng.animateddoors.selection.SelectionManager;
import com.coffeepng.animateddoors.selection.SelectionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public class AnimatedDoorsPlugin extends JavaPlugin {

    private DoorManager doorManager;
    private DoorStorage doorStorage;
    private DoorAnimator doorAnimator;
    private SelectionManager selectionManager;
    private PreviewManager previewManager;

    private NamespacedKey doorKey;

    private int durationTicks;
    private int stepTicks;
    private int cooldownTicks;
    private boolean clickToToggle;
    private boolean blockFilledContainers;
    private String wandMaterial;
    private boolean previewEnabled;
    private int previewTicks;
    private int previewLiveTicks;
    private int previewHoldTicks;
    private int previewMaxBlocks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readConfigValues();

        this.doorKey = new NamespacedKey(this, "door");
        this.doorManager = new DoorManager();
        this.doorStorage = new DoorStorage(getDataFolder(), getLogger());
        this.doorAnimator = new DoorAnimator(this);
        this.selectionManager = new SelectionManager();
        this.selectionManager.setDefaultMode(readDefaultSelectionMode());
        this.previewManager = new PreviewManager(this);

        doorStorage.loadInto(doorManager);

        DoorCommand command = new DoorCommand(this);
        getCommand("door").setExecutor(command);
        getCommand("door").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new RedstoneListener(this), this);

        // Rebuild floating triggers once worlds are guaranteed to be loaded.
        getServer().getScheduler().runTaskLater(this, this::rebuildFloatingTriggers, 20L);

        getLogger().info("AnimatedDoors enabled.");
    }

    @Override
    public void onDisable() {
        if (previewManager != null) {
            previewManager.cancelAll();
        }
        // Remove our interaction entities so they don't accumulate across restarts.
        for (World world : getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Interaction && isOurEntity(entity)) {
                    entity.remove();
                }
            }
        }
        if (doorStorage != null && doorManager != null) {
            doorStorage.saveAll(doorManager);
        }
    }

    private void readConfigValues() {
        this.durationTicks = getConfig().getInt("animation.duration-ticks", 40);
        this.stepTicks = getConfig().getInt("animation.step-ticks", 2);
        this.cooldownTicks = getConfig().getInt("triggers.cooldown-ticks", 10);
        this.clickToToggle = getConfig().getBoolean("triggers.click-door-to-toggle", true);
        this.blockFilledContainers = getConfig().getBoolean("restrictions.block-filled-containers", true);
        this.wandMaterial = getConfig().getString("selection.wand-material", "BLAZE_ROD");
        this.previewEnabled = getConfig().getBoolean("preview.enabled", true);
        this.previewTicks = getConfig().getInt("preview.duration-ticks", 200);
        this.previewLiveTicks = getConfig().getInt("preview.live-duration-ticks", 100);
        this.previewHoldTicks = getConfig().getInt("preview.hold-ticks", 40);
        this.previewMaxBlocks = getConfig().getInt("preview.max-blocks", 2000);
    }

    private SelectionMode readDefaultSelectionMode() {
        SelectionMode mode = SelectionMode.fromString(getConfig().getString("selection.default-mode", "block"));
        if (mode == null) {
            getLogger().warning("selection.default-mode is not 'block' or 'region'; falling back to block.");
            return SelectionMode.BLOCK;
        }
        return mode;
    }

    public void reload() {
        reloadConfig();
        readConfigValues();
        selectionManager.setDefaultMode(readDefaultSelectionMode());
    }

    // ---- Previews ----------------------------------------------------------

    /**
     * Refresh the glowing outline of a player's in-progress selection, so it is always visible
     * which blocks would become part of the door.
     */
    public void previewSelection(Player player, SelectionManager.Selection selection) {
        if (!previewEnabled) {
            return;
        }
        List<BlockVector3> cells = selection.mode() == SelectionMode.REGION
                ? selection.regionPositions()
                : selection.blocks();
        if (cells.isEmpty()) {
            previewManager.cancel(player);
            return;
        }
        if (cells.size() > previewMaxBlocks) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "Selection is " + cells.size() + " blocks — too big to preview (preview.max-blocks = "
                            + previewMaxBlocks + ").",
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
            return;
        }
        previewManager.showSelection(player, player.getWorld(), cells,
                PreviewManager.SELECTION_COLOR, previewLiveTicks);
    }

    // ---- Floating triggers -------------------------------------------------

    private void rebuildFloatingTriggers() {
        // Drop stale interaction entities, then respawn from storage so ids stay consistent.
        for (World world : getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Interaction && isOurEntity(entity)) {
                    entity.remove();
                }
            }
        }
        for (Door door : doorManager.all()) {
            if (door.getFloatingTrigger() != null) {
                spawnFloatingTrigger(door);
            }
        }
    }

    /** Spawn (or replace) the floating interaction trigger for a door. */
    public void spawnFloatingTrigger(Door door) {
        BlockVector3 loc = door.getFloatingTrigger();
        if (loc == null) {
            return;
        }
        World world = getServer().getWorld(door.getWorld());
        if (world == null) {
            return;
        }
        removeFloatingTrigger(door);
        Location center = loc.toCenterLocation(world);
        Interaction interaction = world.spawn(center, Interaction.class, e -> {
            e.setInteractionWidth(1.0f);
            e.setInteractionHeight(1.0f);
            e.setResponsive(true);
            e.setPersistent(true);
            e.getPersistentDataContainer().set(doorKey, PersistentDataType.STRING, door.getId().toString());
        });
        door.setFloatingEntityId(interaction.getUniqueId());
    }

    public void removeFloatingTrigger(Door door) {
        if (door.getFloatingEntityId() != null) {
            Entity entity = getServer().getEntity(door.getFloatingEntityId());
            if (entity != null) {
                entity.remove();
            }
        }
        door.setFloatingEntityId(null);
    }

    public UUID doorIdOf(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(doorKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isOurEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(doorKey, PersistentDataType.STRING);
    }

    public void saveDoors() {
        doorStorage.saveAll(doorManager);
    }

    /**
     * Toggle a door if it is not busy or on cooldown. Central entry point for commands and triggers.
     *
     * @return the outcome; {@link ToggleResult#STARTED} means the swing began.
     */
    public ToggleResult attemptToggle(Door door) {
        long tick = getServer().getCurrentTick();
        if (door.isAnimating()) {
            return ToggleResult.BUSY;
        }
        if (doorManager.onCooldown(door, tick, cooldownTicks)) {
            return ToggleResult.COOLDOWN;
        }
        ToggleResult result = doorAnimator.toggle(door);
        if (result == ToggleResult.STARTED) {
            doorManager.markToggled(door, tick);
        }
        return result;
    }

    // ---- Accessors ---------------------------------------------------------

    public DoorManager getDoorManager() {
        return doorManager;
    }

    public DoorAnimator getDoorAnimator() {
        return doorAnimator;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public PreviewManager getPreviewManager() {
        return previewManager;
    }

    public boolean isPreviewEnabled() {
        return previewEnabled;
    }

    public int getPreviewTicks() {
        return previewTicks;
    }

    public int getPreviewLiveTicks() {
        return previewLiveTicks;
    }

    public int getPreviewHoldTicks() {
        return previewHoldTicks;
    }

    public int getPreviewMaxBlocks() {
        return previewMaxBlocks;
    }

    public NamespacedKey getDoorKey() {
        return doorKey;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public int getStepTicks() {
        return stepTicks;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public boolean isClickToToggle() {
        return clickToToggle;
    }

    public boolean isBlockFilledContainers() {
        return blockFilledContainers;
    }

    public String getWandMaterial() {
        return wandMaterial;
    }
}
