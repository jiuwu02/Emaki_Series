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
            String originalValue) {

        public NameOperationRecord {
            action = Texts.lower(action);
            renderedValue = Texts.toStringSafe(renderedValue);
            originalValue = Texts.toStringSafe(originalValue);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("action", action);
            map.put("value", renderedValue);
            if (Texts.isNotBlank(originalValue)) {
                map.put("original", originalValue);
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
            if (Texts.isBlank(action)) {
                return null;
            }
            return new NameOperationRecord(action, value, original);
        }
    }

    public record LoreOperationRecord(
            String action,
            List<String> renderedLines,
            String anchor,
            List<String> originalLines,
            List<String> beforeLines,
            boolean beforeRecorded) {

        public LoreOperationRecord(String action,
                List<String> renderedLines,
                String anchor,
                List<String> originalLines) {
            this(action, renderedLines, anchor, originalLines, List.of(), false);
        }

        public LoreOperationRecord(String action,
                List<String> renderedLines,
                String anchor,
                List<String> originalLines,
                List<String> beforeLines) {
            this(action, renderedLines, anchor, originalLines, beforeLines, true);
        }

        public LoreOperationRecord {
            action = Texts.lower(action);
            renderedLines = renderedLines == null ? List.of() : List.copyOf(renderedLines);
            anchor = Texts.toStringSafe(anchor);
            originalLines = originalLines == null ? List.of() : List.copyOf(originalLines);
            beforeLines = beforeLines == null ? List.of() : List.copyOf(beforeLines);
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
            if (Texts.isBlank(action)) {
                return null;
            }
            return new LoreOperationRecord(action, lines, anchor, original, before, beforeRecorded);
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
