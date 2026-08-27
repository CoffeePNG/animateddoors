package com.coffeepng.animateddoors.selection;

import java.util.Locale;

/**
 * How the wand builds a selection.
 */
public enum SelectionMode {

    /** Click individual blocks: right-click adds one, left-click removes one. */
    BLOCK,

    /** Classic two-corner box: left-click sets corner 1, right-click sets corner 2. */
    REGION;

    public static SelectionMode fromString(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "block", "blocks", "single", "pick" -> BLOCK;
            case "region", "box", "corners", "cuboid" -> REGION;
            default -> null;
        };
    }

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }
}
