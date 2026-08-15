package com.coffeepng.animateddoors.door;

/**
 * Outcome of attempting to toggle a door.
 */
public enum ToggleResult {
    /** The swing started. */
    STARTED,
    /** The door is mid-swing. */
    BUSY,
    /** The door was toggled too recently. */
    COOLDOWN,
    /** The door's world isn't loaded. */
    NO_WORLD,
    /** No solid blocks were found to move. */
    EMPTY,
    /** A container in the door still holds items. */
    CONTAINER_WITH_ITEMS;

    /** Human-readable feedback for this outcome. */
    public String message() {
        return switch (this) {
            case STARTED -> "Door toggled.";
            case BUSY -> "That door is already moving.";
            case COOLDOWN -> "That door was just toggled — wait a moment.";
            case NO_WORLD -> "That door's world isn't loaded.";
            case EMPTY -> "That door has no solid blocks to move.";
            case CONTAINER_WITH_ITEMS -> "That door contains a container with items — empty it first.";
        };
    }
}
