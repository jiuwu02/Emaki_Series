package emaki.jiuwu.craft.corelib.api.item;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class PlainItemData {

    private static final Pattern COMPONENT_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private PlainItemData() {
    }

    static String componentId(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("组件 ID 不能为 null。");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        if (!COMPONENT_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("无效的带命名空间组件 ID: " + raw);
        }
        return normalized;
    }

    static Object copy(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double
                || value instanceof BigInteger || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Character character) {
            return character.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    throw new IllegalArgumentException("纯物品数据映射不能包含 null 键。");
                }
                copied.put(String.valueOf(entry.getKey()), copy(entry.getValue()));
            }
            return Collections.unmodifiableMap(copied);
        }
        if (value instanceof List<?> list) {
            List<Object> copied = new ArrayList<>(list.size());
            for (Object entry : list) {
                copied.add(copy(entry));
            }
            return Collections.unmodifiableList(copied);
        }
        throw new IllegalArgumentException("不支持的物品数据值类型: " + value.getClass().getName());
    }

    static Map<String, ItemComponentPatch> componentMap(Map<String, ItemComponentPatch> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, ItemComponentPatch> copied = new LinkedHashMap<>();
        for (Map.Entry<String, ItemComponentPatch> entry : source.entrySet()) {
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("组件补丁不能为 null: " + entry.getKey());
            }
            copied.put(componentId(entry.getKey()), entry.getValue().copy());
        }
        return Collections.unmodifiableMap(copied);
    }
}
