package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.assembly.ItemOperationEntry;

public final class ItemOperationCodec {

    private ItemOperationCodec() {
    }

    public static List<Map<String, Object>> encode(List<ItemOperationEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (ItemOperationEntry entry : entries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            result.add(entry.toMap());
        }
        return result;
    }

    public static List<ItemOperationEntry> decode(Object raw) {
        return decodeStrict(raw).entries();
    }

    public static DecodeResult decodeStrict(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return new DecodeResult(List.of(), false);
        }
        List<ItemOperationEntry> result = new ArrayList<>();
        Set<String> operationIds = new HashSet<>();
        boolean complete = true;
        for (Object item : list) {
            EntryDecode entryDecode = decodeEntry(item);
            complete &= entryDecode.complete();
            ItemOperationEntry entry = entryDecode.entry();
            if (entry == null || entry.isEmpty()) {
                complete = false;
                continue;
            }
            if (!operationIds.add(entry.operationId())) {
                complete = false;
            }
            result.add(entry);
        }
        return new DecodeResult(result, complete);
    }

    private static EntryDecode decodeEntry(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return EntryDecode.INCOMPLETE;
        }
        Object rawOperationId = map.get("id");
        if (!(rawOperationId instanceof String operationId) || Texts.isBlank(operationId)) {
            return EntryDecode.INCOMPLETE;
        }
        boolean complete = map.containsKey("ns") && map.get("ns") instanceof String;
        String sourceNamespace = Texts.toStringSafe(map.get("ns"));
        LongDecode timestamp = parseLong(map.get("ts"));
        RecordDecode<ItemOperationEntry.NameOperationRecord> nameRecords = decodeNameRecords(
                map.containsKey("name"),
                map.get("name")
        );
        RecordDecode<ItemOperationEntry.LoreOperationRecord> loreRecords = decodeLoreRecords(
                map.containsKey("lore"),
                map.get("lore")
        );
        complete &= timestamp.complete() && nameRecords.complete() && loreRecords.complete();
        ItemOperationEntry entry = new ItemOperationEntry(
                operationId,
                sourceNamespace,
                timestamp.value(),
                nameRecords.records(),
                loreRecords.records()
        );
        return new EntryDecode(entry, complete && !entry.isEmpty());
    }

    private static RecordDecode<ItemOperationEntry.NameOperationRecord> decodeNameRecords(
            boolean present,
            Object raw) {
        if (raw == null) {
            return present ? RecordDecode.emptyIncomplete() : RecordDecode.emptyComplete();
        }
        if (!(raw instanceof List<?> list)) {
            return RecordDecode.emptyIncomplete();
        }
        List<ItemOperationEntry.NameOperationRecord> result = new ArrayList<>();
        boolean complete = true;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)
                    || !validRequiredString(map, "action")
                    || !validOptionalStrings(map, "value", "original", "regex_pattern")) {
                complete = false;
                continue;
            }
            ItemOperationEntry.NameOperationRecord record = ItemOperationEntry.NameOperationRecord.fromMap(map);
            if (record == null) {
                complete = false;
                continue;
            }
            result.add(record);
        }
        return new RecordDecode<>(result, complete);
    }

    private static RecordDecode<ItemOperationEntry.LoreOperationRecord> decodeLoreRecords(
            boolean present,
            Object raw) {
        if (raw == null) {
            return present ? RecordDecode.emptyIncomplete() : RecordDecode.emptyComplete();
        }
        if (!(raw instanceof List<?> list)) {
            return RecordDecode.emptyIncomplete();
        }
        List<ItemOperationEntry.LoreOperationRecord> result = new ArrayList<>();
        boolean complete = true;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)
                    || !validRequiredString(map, "action")
                    || !validOptionalStrings(map, "anchor", "regex_pattern", "replacement")
                    || !validOptionalStringList(map, "lines")
                    || !validOptionalStringList(map, "original")
                    || !validOptionalStringList(map, "before")
                    || !validOptionalNonNegativeInt(map, "index")) {
                complete = false;
                continue;
            }
            ItemOperationEntry.LoreOperationRecord record = ItemOperationEntry.LoreOperationRecord.fromMap(map);
            if (record == null) {
                complete = false;
                continue;
            }
            result.add(record);
        }
        return new RecordDecode<>(result, complete);
    }

    private static boolean validRequiredString(Map<?, ?> map, String key) {
        return map.get(key) instanceof String text && Texts.isNotBlank(text);
    }

    private static boolean validOptionalStrings(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && !(map.get(key) instanceof String)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validOptionalStringList(Map<?, ?> map, String key) {
        if (!map.containsKey(key)) {
            return true;
        }
        if (!(map.get(key) instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (!(item instanceof String)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validOptionalNonNegativeInt(Map<?, ?> map, String key) {
        if (!map.containsKey(key)) {
            return true;
        }
        Object raw = map.get(key);
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer) {
            return ((Number) raw).intValue() >= 0;
        }
        if (raw instanceof Long number) {
            return number >= 0L && number <= Integer.MAX_VALUE;
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text) >= 0;
            } catch (NumberFormatException _) {
                return false;
            }
        }
        return false;
    }

    private static LongDecode parseLong(Object raw) {
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
            long value = ((Number) raw).longValue();
            return new LongDecode(Math.max(0L, value), value >= 0L);
        }
        if (raw instanceof String text) {
            try {
                long value = Long.parseLong(text);
                return new LongDecode(Math.max(0L, value), value >= 0L);
            } catch (NumberFormatException _) {
                return LongDecode.INCOMPLETE;
            }
        }
        return LongDecode.INCOMPLETE;
    }

    public record DecodeResult(List<ItemOperationEntry> entries, boolean complete) {

        public DecodeResult {
            entries = entries == null || entries.isEmpty() ? List.of() : List.copyOf(entries);
        }
    }

    private record EntryDecode(ItemOperationEntry entry, boolean complete) {

        private static final EntryDecode INCOMPLETE = new EntryDecode(null, false);
    }

    private record RecordDecode<T>(List<T> records, boolean complete) {

        private RecordDecode {
            records = records == null || records.isEmpty() ? List.of() : List.copyOf(records);
        }

        private static <T> RecordDecode<T> emptyComplete() {
            return new RecordDecode<>(List.of(), true);
        }

        private static <T> RecordDecode<T> emptyIncomplete() {
            return new RecordDecode<>(List.of(), false);
        }
    }

    private record LongDecode(long value, boolean complete) {

        private static final LongDecode INCOMPLETE = new LongDecode(0L, false);
    }
}
