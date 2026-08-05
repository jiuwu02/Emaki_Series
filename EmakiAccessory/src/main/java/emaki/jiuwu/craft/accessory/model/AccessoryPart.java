package emaki.jiuwu.craft.accessory.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * One configured accessory part: a free-form id plus how many slots it provides.
 *
 * <p>A part is the configuration concept; a slot instance is the concrete grid cell it expands into.
 * Expansion is always {@code <partId>_<index>} with index starting at 1, and single-slot parts expand
 * the same way rather than keeping a bare id. That uniformity matters: if {@code necklace} sometimes
 * meant the part and sometimes the slot, every downstream consumer would need to branch on it.
 *
 * @param partId      normalized part id
 * @param count       number of slot instances; at least 1
 * @param displayName MiniMessage display name shown on the empty-slot icon; may be empty
 * @param icon        item source id used as the empty-slot placeholder; may be empty
 */
public record AccessoryPart(String partId, int count, String displayName, String icon) {

    /** Part ids are restricted to this shape so slot instance ids stay unambiguous. */
    public static final Pattern PART_ID_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    /** A part id ending in {@code _<digits>} would collide with another part's slot instance ids. */
    private static final Pattern INDEX_SUFFIX_PATTERN = Pattern.compile(".*_\\d+$");

    /** Canonical constructor; normalizes the id and clamps the count to at least 1. */
    public AccessoryPart {
        partId = Texts.normalizeId(partId);
        displayName = Texts.toStringSafe(displayName);
        icon = Texts.toStringSafe(icon);
        count = Math.max(1, count);
    }

    /**
     * Tests whether a part id is legal.
     *
     * <p>Rejects ids ending in {@code _<digits>} on top of the character rule: {@code ring_2} as a part
     * name would collide with the second slot instance of a {@code ring} part. That rule is not implied
     * by the character class, it is forced by the instance naming scheme.
     *
     * @param candidate the raw id from configuration
     * @return whether the id may be used as a part id
     */
    public static boolean isLegalPartId(String candidate) {
        String normalized = Texts.normalizeId(candidate);
        return Texts.isNotBlank(normalized)
                && PART_ID_PATTERN.matcher(normalized).matches()
                && !INDEX_SUFFIX_PATTERN.matcher(normalized).matches();
    }

    /**
     * Builds the slot instance id for one index.
     *
     * @param partId the owning part id
     * @param index  one-based slot index
     * @return the slot instance id
     */
    public static String slotInstanceId(String partId, int index) {
        return Texts.normalizeId(partId) + "_" + index;
    }

    /**
     * Extracts the part id from a slot instance id by stripping the trailing {@code _<index>}.
     *
     * <p>Returns the input unchanged when it carries no index suffix, so an orphaned key from older
     * data still yields something usable for diagnostics.
     *
     * @param slotInstanceId the slot instance id
     * @return the owning part id
     */
    public static String partIdOf(String slotInstanceId) {
        String normalized = Texts.normalizeId(slotInstanceId);
        int separator = normalized.lastIndexOf('_');
        if (separator <= 0 || separator == normalized.length() - 1) {
            return normalized;
        }
        String suffix = normalized.substring(separator + 1);
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return normalized;
            }
        }
        return normalized.substring(0, separator);
    }

    /** {@return this part's slot instance ids in index order} */
    public List<String> slotInstanceIds() {
        List<String> ids = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            ids.add(slotInstanceId(partId, index));
        }
        return List.copyOf(ids);
    }
}
