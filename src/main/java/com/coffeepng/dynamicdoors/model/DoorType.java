package com.coffeepng.dynamicdoors.model;

import java.util.Locale;

/**
 * The motion a door performs when toggled.
 */
public enum DoorType {
    /** Swings 90° around a vertical hinge (classic door / gate). */
    SWING,
    /** Slides straight up or down (portcullis / gate that retracts). */
    PORTCULLIS,
    /** Slides horizontally along a compass direction (pocket door / blast door). */
    SLIDING;

    public static DoorType fromString(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "swing", "door", "gate" -> SWING;
            case "portcullis", "vertical", "lift" -> PORTCULLIS;
            case "sliding", "slide", "horizontal", "pocket" -> SLIDING;
            default -> null;
        };
    }
}
