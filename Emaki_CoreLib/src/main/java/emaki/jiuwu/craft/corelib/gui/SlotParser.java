package emaki.jiuwu.craft.corelib.gui;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SlotParser {

    private SlotParser() {
    }

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

    public static boolean isValidSlot(Integer slot, int rows) {
        if (slot == null) {
            return false;
        }
        return slot >= 0 && slot <= rows * 9 - 1;
    }

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
