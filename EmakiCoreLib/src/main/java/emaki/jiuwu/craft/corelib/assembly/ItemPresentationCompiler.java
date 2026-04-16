package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.lore.ActionNameParser;
import emaki.jiuwu.craft.corelib.assembly.lore.IndexedLineInsertActionParser;
import emaki.jiuwu.craft.corelib.assembly.lore.IndexedLineInsertActionParser.LineDirection;
import emaki.jiuwu.craft.corelib.assembly.lore.IndexedLineInsertActionParser.ParsedIndexedLineInsertAction;
import emaki.jiuwu.craft.corelib.assembly.lore.SearchInsertConfig;
import emaki.jiuwu.craft.corelib.assembly.lore.SearchInsertValidationException;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * 物品展示编译器
 * <p>
 * 负责将配置中的名称和 Lore 操作编译为标准化的 {@link EmakiPresentationEntry} 列表。
 * 该类使用标准化字段命名，所有局部变量和参数均遵循小驼峰命名法规范。
 * </p>
 *
 * @since 2.0.0
 */
public final class ItemPresentationCompiler {

    /**
     * 编译完整的物品展示操作（名称 + Lore）
     *
     * @param nameOperations 名称操作配置对象
     * @param loreOperations Lore 操作配置对象
     * @param startSequence 起始序列号
     * @param sourceId 来源标识符
     * @return 编译结果，包含条目列表和问题列表
     */
    public PresentationCompileResult compile(Object nameOperations,
            Object loreOperations,
            int startSequence,
            String sourceId) {
        PresentationCompileResult nameResult = compileNameOperations(nameOperations, startSequence, sourceId);
        PresentationCompileResult loreResult = compileLoreOperations(loreOperations, nameResult.nextSequence(), sourceId);
        List<EmakiPresentationEntry> entries = new ArrayList<>(nameResult.entries());
        entries.addAll(loreResult.entries());
        List<PresentationCompileIssue> issues = new ArrayList<>(nameResult.issues());
        issues.addAll(loreResult.issues());
        return new PresentationCompileResult(entries, loreResult.nextSequence(), issues);
    }

    /**
     * 编译名称相关操作
     *
     * @param operations 操作配置列表
     * @param startSequence 起始序列号
     * @param sourceId 来源标识符
     * @return 编译结果
     */
    public PresentationCompileResult compileNameOperations(Object operations, int startSequence, String sourceId) {
        List<EmakiPresentationEntry> entries = new ArrayList<>();
        int sequence = Math.max(0, startSequence);

        for (Object raw : ConfigNodes.asObjectList(operations)) {
            Map<String, Object> operation = normalizeOperation(raw);
            if (operation.isEmpty()) {
                continue;
            }
            String action = resolveAction(operation);

            switch (action) {
                case "append_suffix" ->
                    entries.add(new EmakiPresentationEntry(
                            "name_append",
                            "",
                            resolveString(operation, "value"), // 配置键保持不变，仅变量命名标准化
                            sequence++,
                            sourceId
                    ));
                case "prepend_prefix" ->
                    entries.add(new EmakiPresentationEntry(
                            "name_prepend",
                            "",
                            resolveString(operation, "value"), // 配置键保持不变
                            sequence++,
                            sourceId
                    ));
                case "replace" ->
                    entries.add(new EmakiPresentationEntry(
                            "name_replace",
                            "",
                            resolveString(operation, "value"), // 配置键保持不变
                            sequence++,
                            sourceId
                    ));
                case "regex_replace" ->
                    entries.add(new EmakiPresentationEntry(
                            "name_regex_replace",
                            resolveString(operation, "regex_pattern"), // 配置键保持不变
                            resolveString(operation, "replacement"), // 配置键保持不变
                            sequence++,
                            sourceId
                    ));
                default -> {
                    // 忽略未知操作类型
                }
            }
        }
        return new PresentationCompileResult(entries, sequence, List.of());
    }

    /**
     * 编译 Lore 相关操作
     *
     * @param operations 操作配置列表
     * @param startSequence 起始序列号
     * @param sourceId 来源标识符
     * @return 编译结果，包含条目列表和可能的编译问题
     */
    public PresentationCompileResult compileLoreOperations(Object operations, int startSequence, String sourceId) {
        List<EmakiPresentationEntry> entries = new ArrayList<>();
        List<PresentationCompileIssue> issues = new ArrayList<>();
        int sequence = Math.max(0, startSequence);

        for (Object raw : ConfigNodes.asObjectList(operations)) {
            sequence = compileLoreOperation(raw, entries, issues, sequence, sourceId);
        }
        return new PresentationCompileResult(entries, sequence, issues);
    }

    // ==================== 私有辅助方法 ====================
    /**
     * 添加 Lore 内容插入条目（支持多行内容）
     *
     * @param entries 条目列表（将被修改）
     * @param entryType 条目类型
     * @param anchor 搜索锚点
     * @param content 内容行列表
     * @param sequence 当前序列号
     * @param sourceId 来源标识符
     * @return 更新后的序列号
     */
    private int addLoreContentEntries(List<EmakiPresentationEntry> entries,
            String entryType,
            String anchor,
            List<String> content,
            int sequence,
            String sourceId) {
        for (String line : content) {
            String statId = firstPlaceholder(line);
            if (Texts.isNotBlank(statId)) {
                entries.add(new EmakiPresentationEntry("stat_line", anchor, line, sequence++, statId));
                continue;
            }
            entries.add(new EmakiPresentationEntry(entryType, anchor, line, sequence++, sourceId));
        }
        return sequence;
    }

    private int addIndexedLoreEntries(List<EmakiPresentationEntry> entries,
            ParsedIndexedLineInsertAction indexedAction,
            List<String> content,
            int sequence,
            String sourceId) {
        int lineIndex = Math.max(1, indexedAction.lineIndex());
        int offset = 0;
        for (String line : content) {
            String entryType;
            int effectiveLineIndex;
            if (indexedAction.direction() == LineDirection.TOP) {
                effectiveLineIndex = lineIndex + offset;
                entryType = "lore_top_line_insert";
                offset++;
            } else {
                effectiveLineIndex = lineIndex;
                entryType = "lore_bottom_line_insert";
            }
            String statId = firstPlaceholder(line);
            if (Texts.isNotBlank(statId)) {
                String statEntryType = indexedAction.direction() == LineDirection.TOP ? "stat_line_top" : "stat_line_bottom";
                entries.add(new EmakiPresentationEntry(statEntryType, String.valueOf(effectiveLineIndex), line, sequence++, statId));
                continue;
            }
            entries.add(new EmakiPresentationEntry(entryType, String.valueOf(effectiveLineIndex), line, sequence++, sourceId));
        }
        return sequence;
    }

    private int compileLoreOperation(Object raw,
            List<EmakiPresentationEntry> entries,
            List<PresentationCompileIssue> issues,
            int sequence,
            String sourceId) {
        Map<String, Object> operation = normalizeOperation(raw);
        if (operation.isEmpty()) {
            return sequence;
        }
        String action = resolveAction(operation);
        List<String> content = resolveContent(operation);
        String searchPattern = resolveString(operation, "target_pattern");
        String regexSearchPattern = resolveString(operation, "regex_pattern");
        String contentTemplate = resolveString(operation, "replacement");

        if (ActionNameParser.isSearchInsertAction(action)) {
            return addSearchInsertEntry(operation, entries, issues, sequence, sourceId, action, content, searchPattern);
        }
        if (IndexedLineInsertActionParser.isIndexedLineInsertAction(action)) {
            ParsedIndexedLineInsertAction indexedAction = IndexedLineInsertActionParser.parse(action);
            return addIndexedLoreEntries(entries, indexedAction, content, sequence, sourceId);
        }
        return switch (action) {
            case "insert_below" ->
                addLoreContentEntries(entries, "lore_insert_below", searchPattern, content, sequence, sourceId);
            case "insert_above" ->
                addLoreContentEntries(entries, "lore_insert_above", searchPattern, content, sequence, sourceId);
            case "append" ->
                addLoreContentEntries(entries, "lore_append", "", content, sequence, sourceId);
            case "prepend" ->
                addLoreContentEntries(entries, "lore_prepend", "", content, sequence, sourceId);
            case "replace_line" ->
                addLoreReplaceEntry(entries, searchPattern, content, sequence, sourceId);
            case "delete_line" -> {
                entries.add(new EmakiPresentationEntry("lore_delete_line", searchPattern, "", sequence++, sourceId));
                yield sequence;
            }
            case "regex_replace" -> {
                entries.add(new EmakiPresentationEntry("lore_regex_replace", regexSearchPattern, contentTemplate, sequence++, sourceId));
                yield sequence;
            }
            default -> sequence;
        };
    }

    private int addSearchInsertEntry(Map<String, Object> operation,
            List<EmakiPresentationEntry> entries,
            List<PresentationCompileIssue> issues,
            int sequence,
            String sourceId,
            String action,
            List<String> content,
            String searchPattern) {
        try {
            SearchInsertConfig config = SearchInsertConfig.fromAction(
                    action,
                    searchPattern,
                    resolveBoolean(operation, "ignore_case", false),
                    content,
                    resolveBoolean(operation, "inherit_style", true),
                    resolveString(operation, "on_not_found")
            );
            entries.add(new EmakiPresentationEntry("lore_search_insert", config.toJson(), "", sequence++, sourceId));
        } catch (SearchInsertValidationException exception) {
            issues.add(new PresentationCompileIssue(
                    sourceId,
                    action,
                    searchPattern,
                    exception.reason(),
                    exception.getMessage()
            ));
        }
        return sequence;
    }

    /**
     * 添加 Lore 替换条目（仅替换第一行内容）
     *
     * @param entries 条目列表（将被修改）
     * @param searchPattern 搜索模式
     * @param content 内容行列表
     * @param sequence 当前序列号
     * @param sourceId 来源标识符
     * @return 更新后的序列号
     */
    private int addLoreReplaceEntry(List<EmakiPresentationEntry> entries,
            String searchPattern,
            List<String> content,
            int sequence,
            String sourceId) {
        String line = content.isEmpty() ? "" : content.get(0);
        String statId = firstPlaceholder(line);
        if (Texts.isNotBlank(statId)) {
            entries.add(new EmakiPresentationEntry("stat_line", searchPattern, line, sequence++, statId));
            return sequence;
        }
        entries.add(new EmakiPresentationEntry("lore_replace_line", searchPattern, line, sequence++, sourceId));
        return sequence;
    }

    /**
     * 规范化操作配置为统一的 Map 格式
     *
     * @param raw 原始配置对象
     * @return 规范化后的 Map，如果输入无效则返回空 Map
     */
    private Map<String, Object> normalizeOperation(Object raw) {
        Object plain = ConfigNodes.toPlainData(raw);
        if (!(plain instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(String.valueOf(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
        }
        return normalized;
    }

    /**
     * 从操作配置中解析动作名称
     *
     * @param operation 操作配置 Map
     * @return 小写格式的动作名称
     */
    private String resolveAction(Map<String, Object> operation) {
        return Texts.lower(resolveString(operation, "action"));
    }

    /**
     * 从操作配置中解析内容列表
     *
     * @param operation 操作配置 Map
     * @return 内容字符串列表
     */
    private List<String> resolveContent(Map<String, Object> operation) {
        Object raw = firstPresent(operation, "content");
        return List.copyOf(Texts.asStringList(raw));
    }

    /**
     * 从操作配置中解析布尔值
     *
     * @param operation 操作配置 Map
     * @param key 配置键名
     * @param defaultValue 默认值（当键不存在时返回）
     * @return 解析后的布尔值
     */
    private boolean resolveBoolean(Map<String, Object> operation, String key, boolean defaultValue) {
        if (!operation.containsKey(key)) {
            return defaultValue;
        }
        Object value = operation.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return Boolean.parseBoolean(Texts.toStringSafe(value));
    }

    /**
     * 从操作配置中解析字符串值（支持多个候选键）
     *
     * @param operation 操作配置 Map
     * @param keys 候选键名列表（按优先级排序）
     * @return 解析后的字符串值
     */
    private String resolveString(Map<String, Object> operation, String... keys) {
        Object value = firstPresent(operation, keys);
        return Texts.toStringSafe(value);
    }

    /**
     * 从 Map 中查找第一个存在的键对应的值
     *
     * @param operation 数据源 Map
     * @param keys 候选键列表（按优先级排序）
     * @return 第一个找到的值，或 null
     */
    private Object firstPresent(Map<String, Object> operation, String... keys) {
        for (String key : keys) {
            if (operation.containsKey(key)) {
                return operation.get(key);
            }
        }
        return null;
    }

    /**
     * 从模板字符串中提取第一个占位符标识符
     *
     * @param template 模板字符串（如 "{player_name}"）
     * @return 占位符标识符（不含花括号），或空字符串
     */
    private String firstPlaceholder(String template) {
        if (Texts.isBlank(template)) {
            return "";
        }
        int open = template.indexOf('{');
        int close = template.indexOf('}', open + 1);
        if (open < 0 || close <= open + 1) {
            return "";
        }
        return Texts.lower(template.substring(open + 1, close));
    }
}
