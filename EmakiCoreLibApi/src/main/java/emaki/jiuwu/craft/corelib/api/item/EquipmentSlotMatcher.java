package emaki.jiuwu.craft.corelib.api.item;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Normalization and matching for equipment-slot declarations carried by items, such as the
 * {@code equip_slot} item definition key or the {@code active_slot} meta payload entry.
 *
 * <p>Slot names are normalized through {@link Texts#normalizeId} onto the canonical ids
 * {@code main_hand}, {@code off_hand}, {@code helmet}, {@code chestplate}, {@code leggings} and
 * {@code boots}; other spellings pass through unchanged instead of failing. Every method is
 * null-tolerant: a blank requirement means any slot, and a blank actual slot matches nothing.
 */
public final class EquipmentSlotMatcher {

    /**
     * Meta payload key under which an item records its declared equipment slot (its normalized
     * {@code equip_slot} value).
     */
    public static final String ACTIVE_SLOT_META_KEY = "active_slot";
    /** Requirement matching every equipment slot; also the fallback for blank requirements. */
    public static final String SLOT_ALL = "all";
    /** Requirement matching either hand slot. */
    public static final String SLOT_HAND = "hand";
    /** Canonical id {@code main_hand} of the main-hand slot. */
    public static final String SLOT_MAIN_HAND = "main_hand";
    /** Canonical id {@code off_hand} of the off-hand slot. */
    public static final String SLOT_OFF_HAND = "off_hand";
    /** Canonical id {@code helmet} of the head slot. */
    public static final String SLOT_HELMET = "helmet";
    /** Canonical id {@code chestplate} of the chest slot. */
    public static final String SLOT_CHESTPLATE = "chestplate";
    /** Canonical id {@code leggings} of the legs slot. */
    public static final String SLOT_LEGGINGS = "leggings";
    /** Canonical id {@code boots} of the feet slot. */
    public static final String SLOT_BOOTS = "boots";

    private EquipmentSlotMatcher() {
    }

    /**
     * Normalizes a configured slot requirement.
     *
     * @param slot configured value; {@code null} and blank are accepted
     * @return the canonical slot id, or {@link #SLOT_ALL} when the value is {@code null} or blank
     */
    public static String normalizeRequired(String slot) {
        String normalized = normalize(slot);
        return Texts.isBlank(normalized) ? SLOT_ALL : normalized;
    }

    /**
     * Normalizes a slot id describing where an item actually sits.
     *
     * @param slot raw slot id; {@code null} and blank are accepted
     * @return the canonical slot id, or an empty string when the value is {@code null} or blank
     */
    public static String normalizeActual(String slot) {
        return normalize(slot);
    }

    /**
     * Checks an actual equipment slot against a required one.
     *
     * <p>An all requirement matches unconditionally, while a blank actual slot never matches.
     * The {@link #SLOT_HAND} requirement covers both {@link #SLOT_MAIN_HAND} and
     * {@link #SLOT_OFF_HAND}; every other requirement demands an exact canonical match.
     *
     * @param actualSlot where the item actually is; {@code null} is treated as blank
     * @param requiredSlot what the item requires; {@code null} and blank mean any slot
     * @return {@code true} when the item counts as equipped in {@code actualSlot}
     */
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
            case "all" -> SLOT_ALL;
            case "hand" -> SLOT_HAND;
            case "main_hand" -> SLOT_MAIN_HAND;
            case "off_hand" -> SLOT_OFF_HAND;
            case "helmet" -> SLOT_HELMET;
            case "chestplate" -> SLOT_CHESTPLATE;
            case "leggings" -> SLOT_LEGGINGS;
            case "boots" -> SLOT_BOOTS;
            default -> normalized;
        };
    }
}
