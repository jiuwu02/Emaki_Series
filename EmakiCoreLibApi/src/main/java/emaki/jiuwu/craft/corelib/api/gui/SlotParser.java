package emaki.jiuwu.craft.corelib.api.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Parses configured GUI slot selections into concrete slot numbers.
 *
 * <p>Accepted shapes are a single number, a comma-separated string with {@code start-end} ranges
 * (such as {@code "0-8,13,45"}), an iterable of those shapes, and any other object read through
 * its string form. Duplicates collapse while keeping the first occurrence, and blank or
 * unparsable fragments are skipped instead of failing the parse.
 */
public final class SlotParser {

    private SlotParser() {
    }

    /**
     * Parses a configured slot selection into slot numbers.
     *
     * <p>A {@code null} input yields an immutable empty list; every other input yields a fresh
     * mutable list. Range bounds given in reverse order are swapped before expansion.
     *
     * @param raw configured value: a number, a comma/range string, an iterable of those, or any
     *            other object read through its string form
     * @return the deduplicated slot numbers in first-occurrence order; never {@code null}
     */
    public static List<Integer> parse(Object raw) {
        if (raw == null) {
            return List.of();
        }
        Set<Integer> result = new LinkedHashSet<>();
        if (raw instanceof Number number) {
            result.add(number.intValue());
            return new ArrayList<>(result);
        }
        if (raw instanceof String text) {
            parseInto(result, text);
            return new ArrayList<>(result);
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry != null) {
                    parseInto(result, entry);
                }
            }
            return new ArrayList<>(result);
        }
        parseInto(result, raw);
        return new ArrayList<>(result);
    }

    /**
     * Checks a slot against a {@code rows}-by-9 chest inventory.
     *
     * @param slot slot index to check; {@code null} is invalid
     * @param rows number of rows in the inventory; a value below {@code 1} makes every slot
     *             invalid
     * @return {@code true} when the slot lies within {@code 0} .. {@code rows * 9 - 1}
     */
    public static boolean isValidSlot(Integer slot, int rows) {
        if (slot == null) {
            return false;
        }
        return slot >= 0 && slot <= rows * 9 - 1;
    }

    /**
     * {@return the perimeter slots of a {@code rows}-by-9 chest layout as a fresh mutable list}
     *
     * <p>The top row (left to right) comes first, then the bottom row (left to right), then the
     * left and right column of every interior row from top to bottom. Layouts with fewer than
     * three rows yield an empty list.
     *
     * @param rows number of rows in the inventory
     */
    public static List<Integer> borderSlots(int rows) {
        List<Integer> result = new ArrayList<>();
        if (rows < 3) {
            return result;
        }
        for (int col = 0; col < 9; col++) {
            result.add(col);
            result.add((rows - 1) * 9 + col);
        }
        for (int row = 1; row < rows - 1; row++) {
            result.add(row * 9);
            result.add(row * 9 + 8);
        }
        return result;
    }

    private static void parseInto(Set<Integer> result, Object raw) {
        if (raw instanceof Number number) {
            result.add(number.intValue());
            return;
        }
        String text = Texts.trim(raw);
        if (Texts.isBlank(text)) {
            return;
        }
        String[] parts = text.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.contains("-")) {
                String[] range = trimmed.split("-", 2);
                Integer start = Numbers.tryParseInt(range[0], null);
                Integer end = Numbers.tryParseInt(range[1], null);
                if (start == null || end == null) {
                    continue;
                }
                if (start > end) {
                    int swap = start;
                    start = end;
                    end = swap;
                }
                for (int slot = start; slot <= end; slot++) {
                    result.add(slot);
                }
                continue;
            }
            Integer parsed = Numbers.tryParseInt(trimmed, null);
            if (parsed != null) {
                result.add(parsed);
            }
        }
    }
}
