package emaki.jiuwu.craft.corelib.api.item;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class EquipmentSlotMatcher {

    public static final String ACTIVE_SLOT_META_KEY = "active_slot";
    public static final String SLOT_ALL = "all";
    public static final String SLOT_HAND = "hand";
    public static final String SLOT_MAIN_HAND = "main_hand";
    public static final String SLOT_OFF_HAND = "off_hand";
    public static final String SLOT_HELMET = "helmet";
    public static final String SLOT_CHESTPLATE = "chestplate";
    public static final String SLOT_LEGGINGS = "leggings";
    public static final String SLOT_BOOTS = "boots";

    private EquipmentSlotMatcher() {
    }

    public static String normalizeRequired(String slot) {
        String normalized = normalize(slot);
        return Texts.isBlank(normalized) ? SLOT_ALL : normalized;
    }

    public static String normalizeActual(String slot) {
        return normalize(slot);
    }

    public static boolean matches(String actualSlot, String requiredSlot) {
        String normalizedRequired = normalizeRequired(requiredSlot);
        if (SLOT_ALL.equals(normalizedRequired)) {
            return true;
        }
        String normalizedActual = normalizeActual(actualSlot);
        if (Texts.isBlank(normalizedActual)) {
            return false;
        }
        if (normalizedRequired.equals(normalizedActual)) {
            return true;
        }
        return SLOT_HAND.equals(normalizedRequired)
                && (SLOT_MAIN_HAND.equals(normalizedActual) || SLOT_OFF_HAND.equals(normalizedActual));
    }

    private static String normalize(String slot) {
        String normalized = Texts.normalizeId(slot);
        if (Texts.isBlank(normalized)) {
            return "";
        }
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "all", "any" -> SLOT_ALL;
            case "hand" -> SLOT_HAND;
            case "mainhand", "main_hand", "main" -> SLOT_MAIN_HAND;
            case "offhand", "off_hand", "off" -> SLOT_OFF_HAND;
            case "helmet", "head" -> SLOT_HELMET;
            case "chestplate", "chest", "body" -> SLOT_CHESTPLATE;
            case "leggings", "legs" -> SLOT_LEGGINGS;
            case "boots", "feet", "foot" -> SLOT_BOOTS;
            default -> normalized;
        };
    }
}
