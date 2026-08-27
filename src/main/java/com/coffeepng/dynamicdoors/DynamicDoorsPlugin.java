package com.coffeepng.dynamicdoors;

import com.coffeepng.dynamicdoors.command.DoorCommand;
import com.coffeepng.dynamicdoors.door.DoorAnimator;
import com.coffeepng.dynamicdoors.door.DoorManager;
import com.coffeepng.dynamicdoors.door.DoorStorage;
import com.coffeepng.dynamicdoors.door.PhysicsGuard;
import com.coffeepng.dynamicdoors.door.ToggleResult;
import com.coffeepng.dynamicdoors.listener.PhysicsGuardListener;
import com.coffeepng.dynamicdoors.listener.PowerBlockListener;
import com.coffeepng.dynamicdoors.listener.RedstoneListener;
import com.coffeepng.dynamicdoors.listener.PlayerListener;
import com.coffeepng.dynamicdoors.model.BlockVector3;
import com.coffeepng.dynamicdoors.model.Door;
import com.coffeepng.dynamicdoors.preview.PreviewManager;
import com.coffeepng.dynamicdoors.selection.SelectionManager;
import com.coffeepng.dynamicdoors.selection.SelectionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DynamicDoorsPlugin extends JavaPlugin {

    private DoorManager doorManager;
    private DoorStorage doorStorage;
    private DoorAnimator doorAnimator;
    private SelectionManager selectionManager;
    private PreviewManager previewManager;
    private PhysicsGuard physicsGuard;

    private NamespacedKey doorKey;

    /** The door each player is currently working on, so commands don't need a name every time. */
    private final Map<UUID, UUID> activeDoors = new HashMap<>();

    private int durationTicks;
    private int stepTicks;
    private int cooldownTicks;
    private boolean clickToToggle;
    private boolean blockFilledContainers;
    private boolean blockOnObstruction;
    private boolean suppressBlockUpdates;
    private int updateGuardGraceTicks;
    private String wandMaterial;
    private int attachLimit;
    private Material powerBlockMaterial;
    private boolean requirePowerBlockMaterial;
    private boolean protectPowerBlocks;
    private boolean clickPowerBlock;
    private boolean givePowerBlockWithWand;
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
        this.doorStorage.setCompact(getConfig().getBoolean("storage.compact", true));
        this.doorAnimator = new DoorAnimator(this);
        this.selectionManager = new SelectionManager();
        this.selectionManager.setDefaultMode(readDefaultSelectionMode());
        this.previewManager = new PreviewManager(this);
        this.physicsGuard = new PhysicsGuard();

        doorStorage.loadInto(doorManager);

        DoorCommand command = new DoorCommand(this);
        getCommand("door").setExecutor(command);
        getCommand("door").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new RedstoneListener(this), this);
        getServer().getPluginManager().registerEvents(new PowerBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new PhysicsGuardListener(this), this);

        // Rebuild floating triggers once worlds are guaranteed to be loaded.
        getServer().getScheduler().runTaskLater(this, this::rebuildFloatingTriggers, 20L);

        getLogger().info("DynamicDoors enabled.");
    }

    @Override
    public void onDisable() {
        if (previewManager != null) {
            previewManager.cancelAll();
        }
        if (physicsGuard != null) {
            physicsGuard.clear();
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
        this.blockOnObstruction = readObstructionPolicy();
        this.suppressBlockUpdates = getConfig().getBoolean("restrictions.suppress-block-updates", true);
        this.updateGuardGraceTicks = getConfig().getInt("restrictions.update-guard-grace-ticks", 2);
        this.powerBlockMaterial = readPowerBlockMaterial();
        this.requirePowerBlockMaterial = getConfig().getBoolean("power-block.require-material", true);
        this.protectPowerBlocks = getConfig().getBoolean("power-block.protect", true);
        this.clickPowerBlock = getConfig().getBoolean("power-block.click-to-toggle", true);
        this.givePowerBlockWithWand = getConfig().getBoolean("power-block.give-with-wand", true);
        this.wandMaterial = getConfig().getString("selection.wand-material", "BLAZE_ROD");
        this.attachLimit = getConfig().getInt("selection.attach-limit", 256);
        this.previewEnabled = getConfig().getBoolean("preview.enabled", true);
        this.previewTicks = getConfig().getInt("preview.duration-ticks", 200);
        this.previewLiveTicks = getConfig().getInt("preview.live-duration-ticks", 100);
        this.previewHoldTicks = getConfig().getInt("preview.hold-ticks", 40);
        this.previewMaxBlocks = getConfig().getInt("preview.max-blocks", 2000);
    }

    /** true = refuse to move when something is in the way; false = overwrite it. */
    private boolean readObstructionPolicy() {
        String raw = getConfig().getString("restrictions.obstruction", "block");
        return switch (raw == null ? "block" : raw.toLowerCase(java.util.Locale.ROOT)) {
            case "overwrite", "replace", "ignore" -> false;
            case "block", "refuse", "cancel" -> true;
            default -> {
                getLogger().warning("restrictions.obstruction must be 'block' or 'overwrite'; using block.");
                yield true;
            }
        };
    }

    private Material readPowerBlockMaterial() {
        String raw = getConfig().getString("power-block.material", "GOLD_BLOCK");
        Material material = Material.matchMaterial(raw == null ? "GOLD_BLOCK" : raw);
        if (material == null || !material.isBlock()) {
            getLogger().warning("power-block.material '" + raw + "' is not a block; using GOLD_BLOCK.");
            return Material.GOLD_BLOCK;
        }
        return material;
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
        doorStorage.setCompact(getConfig().getBoolean("storage.compact", true));
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
        return attemptToggle(door, null);
    }

    /**
     * Toggle a door on behalf of a player, showing them why if it refuses.
     *
     * @param actor the player who triggered it, or null for redstone/console
     */
    public ToggleResult attemptToggle(Door door, Player actor) {
        ToggleResult result = attemptToggleInternal(door);
        if (result == ToggleResult.OBSTRUCTED && actor != null) {
            int blocked = previewManager.showObstructions(actor, door, previewTicks);
            actor.sendMessage(net.kyori.adventure.text.Component.text(
                    blocked + " block(s) are standing where '" + door.getName()
                            + "' would land (glowing red). Clear them, or set restrictions.obstruction "
                            + "to 'overwrite' to let the door break them.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
        }
        return result;
    }

    private ToggleResult attemptToggleInternal(Door door) {
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

    public PhysicsGuard getPhysicsGuard() {
        return physicsGuard;
    }

    /** True when block updates are frozen on a door's cells while it moves. */
    public boolean isSuppressBlockUpdates() {
        return suppressBlockUpdates;
    }

    public int getUpdateGuardGraceTicks() {
        return updateGuardGraceTicks;
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

    /** True when a door refuses to move rather than overwriting blocks in its way. */
    public boolean isBlockOnObstruction() {
        return blockOnObstruction;
    }

    public Material getPowerBlockMaterial() {
        return powerBlockMaterial;
    }

    public boolean requiresPowerBlockMaterial() {
        return requirePowerBlockMaterial;
    }

    public boolean isProtectPowerBlocks() {
        return protectPowerBlocks;
    }

    public boolean isClickPowerBlock() {
        return clickPowerBlock;
    }

    public boolean isGivePowerBlockWithWand() {
        return givePowerBlockWithWand;
    }

    /**
     * Bind a block as a door's power block, validating it and telling the player what happened.
     * Shared by /door powerblock and the wand, so both behave identically.
     *
     * @return true if the power block was bound
     */
    public boolean bindPowerBlock(Player player, Door door, org.bukkit.block.Block target) {
        if (!target.getWorld().getName().equals(door.getWorld())) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "'" + door.getName() + "' lives in " + door.getWorld() + ".",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return false;
        }
        if (requirePowerBlockMaterial && target.getType() != powerBlockMaterial) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "A power block must be " + powerBlockMaterial.name().toLowerCase(java.util.Locale.ROOT)
                            + " (place one there, or set power-block.require-material to false).",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return false;
        }
        BlockVector3 pos = new BlockVector3(target.getX(), target.getY(), target.getZ());
        Door existing = powerBlockOwner(door.getWorld(), pos);
        if (existing != null && !existing.getId().equals(door.getId())) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "That block is already the power block for '" + existing.getName() + "'.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return false;
        }
        door.setPowerBlock(pos);
        door.setPowered(target.isBlockPowered() || target.isBlockIndirectlyPowered());
        saveDoors();
        previewManager.showSelection(player, target.getWorld(), java.util.List.of(pos),
                PreviewManager.SELECTION_COLOR, previewTicks);
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "Power block for '" + door.getName() + "' set to " + pos + ".",
                net.kyori.adventure.text.format.NamedTextColor.GREEN));
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "Power it with a lever, button, or redstone to toggle the door"
                        + (clickPowerBlock ? " — or just right-click it." : "."),
                net.kyori.adventure.text.format.NamedTextColor.GRAY));
        if (protectPowerBlocks) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "It's protected from breaking; sneak-break it to unbind it.",
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
        return true;
    }

    // ---- Active door -------------------------------------------------------

    /** Remember the door this player is working on. */
    public void setActiveDoor(Player player, Door door) {
        activeDoors.put(player.getUniqueId(), door.getId());
    }

    /** The door this player selected with /door select, or null if none (or it was removed). */
    public Door getActiveDoor(Player player) {
        UUID doorId = activeDoors.get(player.getUniqueId());
        if (doorId == null) {
            return null;
        }
        for (Door door : doorManager.all()) {
            if (door.getId().equals(doorId)) {
                return door;
            }
        }
        activeDoors.remove(player.getUniqueId());
        return null;
    }

    public void clearActiveDoor(Player player) {
        activeDoors.remove(player.getUniqueId());
    }

    /** Forget a door everyone had selected — called when it is deleted. */
    public void forgetDoor(Door door) {
        activeDoors.values().removeIf(id -> id.equals(door.getId()));
    }

    /** The door whose power block sits at this position, if any. */
    public Door powerBlockOwner(String world, BlockVector3 pos) {
        for (Door door : doorManager.all()) {
            if (door.getWorld().equals(world) && pos.equals(door.getPowerBlock())) {
                return door;
            }
        }
        return null;
    }

    public String getWandMaterial() {
        return wandMaterial;
    }

    /** Most blocks a single /door attach scan will pull in. */
    public int getAttachLimit() {
        return attachLimit;
    }
}
