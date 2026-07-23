package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

public record ItemOperationEntry(
        String operationId,
        String sourceNamespace,
        long timestamp,
        List<NameOperationRecord> nameRecords,
        List<LoreOperationRecord> loreRecords) {

    public ItemOperationEntry {
        operationId = Texts.toStringSafe(operationId);
        sourceNamespace = Texts.toStringSafe(sourceNamespace);
        timestamp = Math.max(0L, timestamp);
        nameRecords = nameRecords == null ? List.of() : List.copyOf(nameRecords);
        loreRecords = loreRecords == null ? List.of() : List.copyOf(loreRecords);
    }

    public boolean isEmpty() {
        return nameRecords.isEmpty() && loreRecords.isEmpty();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", operationId);
        map.put("ns", sourceNamespace);
        map.put("ts", timestamp);
        if (!nameRecords.isEmpty()) {
            List<Map<String, Object>> names = new ArrayList<>();
            for (NameOperationRecord record : nameRecords) {
                names.add(record.toMap());
            }
            map.put("name", names);
        }
        if (!loreRecords.isEmpty()) {
            List<Map<String, Object>> lores = new ArrayList<>();
            for (LoreOperationRecord record : loreRecords) {
                lores.add(record.toMap());
            }
            map.put("lore", lores);
        }
        return map;
    }

    public record NameOperationRecord(
            String action,
            String renderedValue,
            String originalValue,
            String regexPattern) {

        public NameOperationRecord(String action, String renderedValue, String originalValue) {
            this(action, renderedValue, originalValue, "");
        }

        public NameOperationRecord {
            action = Texts.lower(action);
            renderedValue = Texts.toStringSafe(renderedValue);
            originalValue = Texts.toStringSafe(originalValue);
            regexPattern = Texts.toStringSafe(regexPattern);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("action", action);
            map.put("value", renderedValue);
            if (Texts.isNotBlank(originalValue)) {
                map.put("original", originalValue);
            }
            if (Texts.isNotBlank(regexPattern)) {
                map.put("regex_pattern", regexPattern);
            }
            return map;
        }

        public static NameOperationRecord fromMap(Map<?, ?> map) {
            if (map == null || map.isEmpty()) {
                return null;
            }
            String action = Texts.toStringSafe(map.get("action"));
            String value = Texts.toStringSafe(map.get("value"));
            String original = Texts.toStringSafe(map.get("original"));
            String regexPattern = Texts.toStringSafe(map.get("regex_pattern"));
            if (Texts.isBlank(action)) {
                return null;
            }
            return new NameOperationRecord(action, value, original, regexPattern);
        }
    }

    public record LoreOperationRecord(
            String action,
            List<String> renderedLines,
            String anchor,
            List<String> originalLines,
            List<String> beforeLines,
            boolean beforeRecorded,
            int requestedIndex,
            String regexPattern,
            String regexReplacement) {

        public LoreOperationRecord(String action,
                List<String> renderedLines,
                String anchor,
                List<String> originalLines) {
            this(action, renderedLines, anchor, originalLines, List.of(), false, 0, "", "");
        }

        public LoreOperationRecord(String action,
                List<String> renderedLines,
                String anchor,
                List<String> originalLines,
                List<String> beforeLines) {
            this(action, renderedLines, anchor, originalLines, beforeLines, true, 0, "", "");
        }

        public LoreOperationRecord(String action,
                List<String> renderedLines,
                String anchor,
                List<String> originalLines,
                int requestedIndex,
                String regexPattern,
                String regexReplacement) {
            this(action, renderedLines, anchor, originalLines, List.of(), false,
                    requestedIndex, regexPattern, regexReplacement);
        }

        public LoreOperationRecord(String action,
                List<String> renderedLines,
                String anchor,
                List<String> originalLines,
                List<String> beforeLines,
                int requestedIndex,
                String regexPattern,
                String regexReplacement) {
            this(action, renderedLines, anchor, originalLines, beforeLines, true,
                    requestedIndex, regexPattern, regexReplacement);
        }

        public LoreOperationRecord {
            action = Texts.lower(action);
            renderedLines = renderedLines == null ? List.of() : List.copyOf(renderedLines);
            anchor = Texts.toStringSafe(anchor);
            originalLines = originalLines == null ? List.of() : List.copyOf(originalLines);
            beforeLines = beforeLines == null ? List.of() : List.copyOf(beforeLines);
            requestedIndex = Math.max(0, requestedIndex);
            regexPattern = Texts.toStringSafe(regexPattern);
            regexReplacement = Texts.toStringSafe(regexReplacement);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("action", action);
            if (!renderedLines.isEmpty()) {
                map.put("lines", new ArrayList<>(renderedLines));
            }
            if (Texts.isNotBlank(anchor)) {
                map.put("anchor", anchor);
            }
            if (!originalLines.isEmpty()) {
                map.put("original", new ArrayList<>(originalLines));
            }
            if (beforeRecorded) {
                map.put("before", new ArrayList<>(beforeLines));
            }
            if (requestedIndex > 0) {
                map.put("index", requestedIndex);
            }
            if (Texts.isNotBlank(regexPattern)) {
                map.put("regex_pattern", regexPattern);
                map.put("replacement", regexReplacement);
            }
            return map;
        }

        public static LoreOperationRecord fromMap(Map<?, ?> map) {
            if (map == null || map.isEmpty()) {
                return null;
            }
            String action = Texts.toStringSafe(map.get("action"));
            List<String> lines = toStringList(map.get("lines"));
            String anchor = Texts.toStringSafe(map.get("anchor"));
            List<String> original = toStringList(map.get("original"));
            boolean beforeRecorded = map.containsKey("before");
            List<String> before = toStringList(map.get("before"));
            int requestedIndex = parseNonNegativeInt(map.get("index"));
            String regexPattern = Texts.toStringSafe(map.get("regex_pattern"));
            String regexReplacement = Texts.toStringSafe(map.get("replacement"));
            if (Texts.isBlank(action)) {
                return null;
            }
            return new LoreOperationRecord(
                    action,
                    lines,
                    anchor,
                    original,
                    before,
                    beforeRecorded,
                    requestedIndex,
                    regexPattern,
                    regexReplacement
            );
        }

        private static int parseNonNegativeInt(Object raw) {
            if (raw instanceof Number number) {
                return Math.max(0, number.intValue());
            }
            if (raw instanceof String text) {
                try {
                    return Math.max(0, Integer.parseInt(text));
                } catch (NumberFormatException _) {
                    return 0;
                }
            }
            return 0;
        }

        private static List<String> toStringList(Object raw) {
            if (raw == null) {
                return List.of();
            }
            if (raw instanceof List<?> list) {
                List<String> result = new ArrayList<>();
                for (Object item : list) {
                    result.add(Texts.toStringSafe(item));
                }
                return result;
            }
            if (raw instanceof String text) {
                return List.of(text);
            }
            return List.of();
        }
    }
}
