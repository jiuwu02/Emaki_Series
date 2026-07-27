package emaki.jiuwu.craft.corelib.gui;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public enum GuiClickType {
    CLICK,
    LEFTCLICK,
    RIGHTCLICK,
    SHIFT_LEFTCLICK,
    SHIFT_RIGHTCLICK,
    MIDDLECLICK,
    DOUBLECLICK,
    NUMBER_KEY,
    SWAP_OFFHAND,
    DROP,
    CONTROL_DROP;

    private static final int OFFHAND_SWAP_BUTTON = 40;

    public static GuiClickType from(InventoryClickEvent event) {
        if (event == null) {
            return CLICK;
        }
        return from(event.getClick());
    }

    public static GuiClickType from(ClickType clickType) {
        if (clickType == null) {
            return CLICK;
        }
        return switch (clickType) {
            case LEFT, CREATIVE, WINDOW_BORDER_LEFT -> LEFTCLICK;
            case RIGHT, WINDOW_BORDER_RIGHT -> RIGHTCLICK;
            case SHIFT_LEFT -> SHIFT_LEFTCLICK;
            case SHIFT_RIGHT -> SHIFT_RIGHTCLICK;
            case MIDDLE -> MIDDLECLICK;
            case DOUBLE_CLICK -> DOUBLECLICK;
            case NUMBER_KEY -> NUMBER_KEY;
            case SWAP_OFFHAND -> SWAP_OFFHAND;
            case DROP -> DROP;
            case CONTROL_DROP -> CONTROL_DROP;
            default -> CLICK;
        };
    }

    public static GuiClickType from(int mode, int button) {
        return switch (mode) {
            case 0 -> button == 1 ? RIGHTCLICK : LEFTCLICK;
            case 1 -> button == 1 ? SHIFT_RIGHTCLICK : SHIFT_LEFTCLICK;
            case 2 -> button == OFFHAND_SWAP_BUTTON ? SWAP_OFFHAND : NUMBER_KEY;
            case 3 -> MIDDLECLICK;
            case 4 -> button == 1 ? CONTROL_DROP : DROP;
            case 6 -> DOUBLECLICK;
            default -> CLICK;
        };
    }

    /**
     * {@return the pre-expansion click type this value used to collapse into, or {@code null}
     * when this value already existed before the enum was expanded}
     *
     * <p>Templates written before the expanded click set only configure {@code click},
     * {@code left_click} and {@code right_click} sounds. Sound lookup walks this fallback so
     * those templates keep their original behaviour for shift, double and creative clicks.
     */
    public GuiClickType legacyFallback() {
        return switch (this) {
            case SHIFT_LEFTCLICK, DOUBLECLICK -> LEFTCLICK;
            case SHIFT_RIGHTCLICK -> RIGHTCLICK;
            default -> null;
        };
    }

    public boolean isLeftVariant() {
        return this == LEFTCLICK || this == SHIFT_LEFTCLICK;
    }

    public boolean isRightVariant() {
        return this == RIGHTCLICK || this == SHIFT_RIGHTCLICK;
    }

    public boolean isShiftVariant() {
        return this == SHIFT_LEFTCLICK || this == SHIFT_RIGHTCLICK;
    }
}
