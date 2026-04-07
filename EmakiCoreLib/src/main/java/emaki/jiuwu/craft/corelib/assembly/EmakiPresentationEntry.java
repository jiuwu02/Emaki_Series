package emaki.jiuwu.craft.corelib.assembly;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Emaki 展示条目类（标准化版本）
 * <p>
 * 该类表示物品展示系统中的一个操作条目，包含操作类型、搜索模式、
 * 内容模板、执行顺序和来源命名空间等信息。
 * 字段命名遵循标准化规范，使用小驼峰命名法。
 * </p>
 *
 * @since 2.0.0
 */
public record EmakiPresentationEntry(
        /** 条目类型标识符（如 name_append, lore_insert_below 等） */
        String entryType,

        /** 搜索/锚定模式（用于定位目标行或文本） */
        String searchPattern,

        /** 内容模板（要插入或替换的文本模板） */
        String contentTemplate,

        /** 执行顺序号（用于控制多个条目的应用顺序） */
        int sequenceOrder,

        /** 来源命名空间（标识配置来源，如插件名称或文件路径） */
        String sourceNamespace) {

    /**
     * 规范化构造函数 - 验证并初始化所有条目字段
     */
    public EmakiPresentationEntry {
        // 规范化字符串字段
        entryType = Texts.isBlank(entryType) ? "unknown" : Texts.lower(entryType);
        searchPattern = Texts.toStringSafe(searchPattern);
        contentTemplate = Texts.toStringSafe(contentTemplate);
        sourceNamespace = Texts.toStringSafe(sourceNamespace);

        // 确保序列号为非负数
        sequenceOrder = Math.max(0, sequenceOrder);
    }

    // ==================== 序列化方法 ====================

    /**
     * 将条目转换为 Map 表示（用于 JSON/YAML 序列化）
     *
     * @return 包含所有字段的 Map（键使用蛇形命名法）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("entry_type", entryType);
        map.put("search_pattern", searchPattern);
        map.put("content_template", contentTemplate);
        map.put("sequence_order", sequenceOrder);
        map.put("source_namespace", sourceNamespace);
        return map;
    }

    /**
     * 从 Map 创建条目实例（用于反序列化）
     *
     * @param map 包含条目数据的 Map（使用标准化的蛇形字段名）
     * @return 条目实例，如果输入无效则返回 null
     */
    public static EmakiPresentationEntry fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        // 仅支持标准化的新字段名
        String typeValue = Texts.toStringSafe(map.get("entry_type"));
        if (Texts.isBlank(typeValue)) {
            return null;
        }

        return new EmakiPresentationEntry(
                typeValue,
                Texts.toStringSafe(map.get("search_pattern")),
                Texts.toStringSafe(map.get("content_template")),
                Numbers.tryParseInt(map.get("sequence_order"), 0),
                Texts.toStringSafe(map.get("source_namespace"))
        );
    }
}
