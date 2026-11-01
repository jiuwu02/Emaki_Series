package emaki.jiuwu.craft.corelib.api.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * One ledger entry describing a single item operation: its identity plus the recorded name and
 * lore changes needed to replay or revert the operation later.
 *
 * <p>The operation ledger persists entries in the item's {@code PersistentDataContainer} through
 * {@link #toMap()} so a rebuilt item can reapply every change it went through. The compact
 * constructor stringifies {@code operationId} and {@code sourceNamespace}, clamps
 * {@code timestamp} to {@code >= 0}, and replaces {@code null} record lists with immutable empty
 * lists; both lists are copied unmodifiable and are never {@code null} after construction.
 *
 * @param operationId identifier of this operation within the ledger
 * @param sourceNamespace namespace that produced the operation
 * @param timestamp epoch millisecond the operation ran, clamped to {@code >= 0}
 * @param nameRecords recorded name changes in execution order
 * @param loreRecords recorded lore changes in execution order
 */
public record ItemOperationEntry(
        String operationId,
        String sourceNamespace,
        long timestamp,
        List<NameOperationRecord> nameRecords,
        List<LoreOperationRecord> loreRecords) {

    /** Normalizes the raw components as described in the type documentation. */
    public ItemOperationEntry {
        operationId = Texts.toStringSafe(operationId);
        sourceNamespace = Texts.toStringSafe(sourceNamespace);
        timestamp = Math.max(0L, timestamp);
        nameRecords = nameRecords == null ? List.of() : List.copyOf(nameRecords);
        loreRecords = loreRecords == null ? List.of() : List.copyOf(loreRecords);
    }

    /**
     * {@return {@code true} when the entry records neither name nor lore changes}
     */
    public boolean isEmpty() {
        return nameRecords.isEmpty() && loreRecords.isEmpty();
    }

    /**
     * {@return the entry as a plain map with the {@code id}, {@code ns} and {@code ts} keys plus
     * {@code name} and {@code lore} record lists}
     *
     * <p>{@code name} and {@code lore} are omitted when their lists are empty; {@code null} list
     * elements are skipped while serializing.
     */
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

    /**
     * One recorded change to the item's display name made by an operation.
     *
     * <p>The compact constructor lowercases {@code action} with {@link Texts#lower(Object)} and
     * stringifies the remaining components ({@code null} becomes an empty string), so no
     * component is {@code null} after construction.
     *
     * @param action registered name-operation key, lowercased, such as {@code regex_replace}
     * @param renderedValue template-rendered value the action applied
     * @param originalValue display name captured before the operation batch ran; every record of
     *                      one batch shares the same captured value
     * @param regexPattern regular expression of a {@code regex_replace} step, otherwise blank
     */
    public record NameOperationRecord(
            String action,
            String renderedValue,
            String originalValue,
            String regexPattern) {

        /**
         * Creates a record without a regex pattern, equivalent to passing an empty
         * {@code regexPattern}.
         */
        public NameOperationRecord(String action, String renderedValue, String originalValue) {
            this(action, renderedValue, originalValue, "");
        }

        /** Normalizes the raw components as described in the type documentation. */
        public NameOperationRecord {
            action = Texts.lower(action);
            renderedValue = Texts.toStringSafe(renderedValue);
            originalValue = Texts.toStringSafe(originalValue);
            regexPattern = Texts.toStringSafe(regexPattern);
        }

        /**
         * {@return the record as a plain map with the {@code action} and {@code value} keys plus
         * {@code original} and {@code regex_pattern} when non-blank}
         */
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

        /**
         * Reads a record back from its {@link #toMap()} form.
         *
         * @param map plain map; {@code null} and empty are accepted
         * @return the parsed record, or {@code null} when the map is {@code null}, empty, or
         *         carries a blank {@code action}; every other missing field defaults to an
         *         empty string
         */
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

    /**
     * One recorded change to the item's lore made by an operation.
     *
     * <p>The compact constructor lowercases {@code action}, copies the line lists unmodifiable
     * ({@code null} becomes an empty list), stringifies {@code anchor}, {@code regexPattern} and
     * {@code regexReplacement}, and clamps {@code requestedIndex} to {@code >= 0}, so no component
     * is {@code null} after construction. The four convenience constructors select common
     * combinations of the trailing state; only the first record of a batch carries the before
     * snapshot of the lore.
     *
     * @param action registered lore-operation key, lowercased, such as {@code replace_line},
     *               {@code delete_line} or {@code regex_replace}
     * @param renderedLines content lines rendered for this step
     * @param anchor search pattern locating the lines this step targets
     * @param originalLines lines the step matched and replaced or deleted, captured for revert
     * @param beforeLines full lore captured before the first recorded step; empty otherwise
     * @param beforeRecorded whether {@code beforeLines} was captured
     * @param requestedIndex index argument of an insert-style step; {@code 0} when unused
     * @param regexPattern regular expression of a {@code regex_replace} step, otherwise blank
     * @param regexReplacement rendered replacement text of a {@code regex_replace} step
     */
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

        /**
         * Creates a record without a before snapshot, index or regex state.
         */
        public LoreOperationRecord(String action,
                List<String> renderedLines,
                String anchor,
                List<String> originalLines) {
            this(action, renderedLines, anchor, originalLines, List.of(), false, 0, "", "");
        }

        /**
         * Creates a record carrying the before snapshot of the lore, without index or regex
         * state; {@code beforeRecorded} becomes {@code true}.
         */
        public LoreOperationRecord(String action,
                List<String> renderedLines,
                String anchor,
                List<String> originalLines,
                List<String> beforeLines) {
            this(action, renderedLines, anchor, originalLines, beforeLines, true, 0, "", "");
        }

        /**
         * Creates a record with an index and regex state but no before snapshot.
         */
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

        /**
         * Creates a fully specified record carrying both the before snapshot and the index and
         * regex state; {@code beforeRecorded} becomes {@code true}.
         */
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

        /** Normalizes the raw components as described in the type documentation. */
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

        /**
         * {@return the record as a plain map with the {@code action} key plus {@code lines},
         * {@code anchor}, {@code original}, {@code before}, {@code index} and the
         * {@code regex_pattern}/{@code replacement} pair only when each value is present}
         *
         * <p>{@code before} is written only when it was captured, and {@code replacement} is
         * written only alongside a non-blank {@code regex_pattern}.
         */
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

        /**
         * Reads a record back from its {@link #toMap()} form.
         *
         * <p>{@code beforeRecorded} is derived from the presence of the {@code before} key, so a
         * recorded empty snapshot round-trips as recorded.
         *
         * @param map plain map; {@code null} and empty are accepted
         * @return the parsed record, or {@code null} when the map is {@code null}, empty, or
         *         carries a blank {@code action}; missing line lists default to empty, a missing
         *         or negative {@code index} to {@code 0}, and missing strings to empty
         */
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
