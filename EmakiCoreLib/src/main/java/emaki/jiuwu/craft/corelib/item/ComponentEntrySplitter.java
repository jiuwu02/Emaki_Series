package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.api.text.Texts;

final class ComponentEntrySplitter {

    private ComponentEntrySplitter() {
    }

    static List<String> split(String text) {
        List<String> entries = new ArrayList<>();
        int start = 0;
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current == '[' || current == '{' || current == '(') {
                depth++;
                continue;
            }
            if (current == ']' || current == '}' || current == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (current == ',' && depth == 0) {
                add(entries, text.substring(start, index));
                start = index + 1;
            }
        }
        add(entries, text.substring(start));
        return entries;
    }

    static int findTopLevel(String text, char target) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current == '[' || current == '{' || current == '(') {
                depth++;
                continue;
            }
            if (current == ']' || current == '}' || current == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (current == target && depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static void add(List<String> entries, String raw) {
        String entry = Texts.toStringSafe(raw).trim();
        if (!entry.isEmpty()) {
            entries.add(entry);
        }
    }
}
