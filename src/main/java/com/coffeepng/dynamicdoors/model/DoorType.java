package com.coffeepng.dynamicdoors.model;

import java.util.Locale;

/**
 * The motion a door performs when toggled.
 */
public enum DoorType {
    /** Swings 90° around a vertical hinge (classic door / gate). */
    SWING,
    /** Slides straight up or down (portcullis / gate that retracts). */
    PORTCULLIS;

    public static DoorType fromString(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "swing", "door", "gate" -> SWING;
            case "portcullis", "slide", "vertical" -> PORTCULLIS;
            default -> null;
        };
    }
}
