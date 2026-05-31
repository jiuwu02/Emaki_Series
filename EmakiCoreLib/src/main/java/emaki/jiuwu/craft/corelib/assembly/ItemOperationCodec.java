package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

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
        if (raw == null) {
            return List.of();
        }
        List<Object> list = ConfigNodes.asObjectList(raw);
        if (list.isEmpty()) {
            return List.of();
        }
        List<ItemOperationEntry> result = new ArrayList<>();
        for (Object item : list) {
            ItemOperationEntry entry = decodeEntry(item);
            if (entry != null && !entry.isEmpty()) {
                result.add(entry);
            }
        }
        return result;
    }

    private static ItemOperationEntry decodeEntry(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        String operationId = Texts.toStringSafe(map.get("id"));
        String sourceNamespace = Texts.toStringSafe(map.get("ns"));
        long timestamp = parseLong(map.get("ts"));
        List<ItemOperationEntry.NameOperationRecord> nameRecords = decodeNameRecords(map.get("name"));
        List<ItemOperationEntry.LoreOperationRecord> loreRecords = decodeLoreRecords(map.get("lore"));
        if (Texts.isBlank(operationId)) {
            return null;
        }
        return new ItemOperationEntry(operationId, sourceNamespace, timestamp, nameRecords, loreRecords);
    }

    private static List<ItemOperationEntry.NameOperationRecord> decodeNameRecords(Object raw) {
        if (raw == null) {
            return List.of();
        }
        List<Object> list = ConfigNodes.asObjectList(raw);
        List<ItemOperationEntry.NameOperationRecord> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                ItemOperationEntry.NameOperationRecord record = ItemOperationEntry.NameOperationRecord.fromMap(map);
                if (record != null) {
                    result.add(record);
                }
            }
        }
        return result;
    }

    private static List<ItemOperationEntry.LoreOperationRecord> decodeLoreRecords(Object raw) {
        if (raw == null) {
            return List.of();
        }
        List<Object> list = ConfigNodes.asObjectList(raw);
        List<ItemOperationEntry.LoreOperationRecord> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                ItemOperationEntry.LoreOperationRecord record = ItemOperationEntry.LoreOperationRecord.fromMap(map);
                if (record != null) {
                    result.add(record);
                }
            }
        }
        return result;
    }

    private static long parseLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException _) {
                return 0L;
            }
        }
        return 0L;
    }
}
