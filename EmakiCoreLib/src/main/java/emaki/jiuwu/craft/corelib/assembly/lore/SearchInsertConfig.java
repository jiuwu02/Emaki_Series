package emaki.jiuwu.craft.corelib.assembly.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Lore 搜索插入配置类（标准化版本）
 * <p>
 * 该类定义了在物品 Lore 中搜索并插入内容的所有配置参数。 字段命名遵循标准化规范，使用小驼峰命名法。
 * </p>
 *
 * @since 2.0.0
 */
public record SearchInsertConfig(
        /**
         * 操作类型标识符（如 insert_above, replace_line 等）
         */
        String actionType,
        /**
         * 搜索模式匹配策略（CONTAINS/EXACT/REGEX）
         */
        SearchMode searchMode,
        /**
         * 插入位置策略（ABOVE/BELOW）
         */
        InsertPosition insertPosition,
        /**
         * 匹配索引（从1开始，表示第几个匹配项）
         */
        int matchIndex,
        /**
         * 目标搜索模式（支持文本、正则表达式）
         */
        String searchPattern,
        /**
         * 是否忽略大小写进行匹配
         */
        boolean caseInsensitive,
        /**
         * 要插入的内容行列表
         */
        List<String> contentLines,
        /**
         * 是否继承目标行的样式（颜色、粗体等）
         */
        boolean styleInheritance,
        /**
         * 未找到匹配时的处理策略
         */
        OnNotFoundPolicy onNotFoundPolicy) {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    /**
     * 规范化构造函数 - 验证并初始化所有配置字段
     */
    public SearchInsertConfig         {
        actionType = Texts.trim(Texts.lower(actionType));
        searchMode = searchMode == null ? SearchMode.CONTAINS : searchMode;
        insertPosition = insertPosition == null ? InsertPosition.BELOW : insertPosition;
        searchPattern = Texts.toStringSafe(searchPattern);
        styleInheritance = styleInheritance;
        onNotFoundPolicy = onNotFoundPolicy == null ? OnNotFoundPolicy.SKIP : onNotFoundPolicy;

        // 规范化内容列表
        List<String> normalizedContent = new ArrayList<>();
        if (contentLines != null) {
            for (String line : contentLines) {
                normalizedContent.add(Texts.toStringSafe(line));
            }
        }
        contentLines = normalizedContent.isEmpty() ? List.of() : List.copyOf(normalizedContent);
    }

    // ==================== 工厂方法 ====================
    /**
     * 从操作名称创建配置实例
     *
     * @param action 操作名称（如 "insert_above:2"）
     * @param targetPattern 目标模式
     * @param ignoreCase 是否忽略大小写
     * @param content 内容列表
     * @param inheritStyle 是否继承样式
     * @param onNotFound 未找到策略
     * @return 验证后的配置实例
     */
    public static SearchInsertConfig fromAction(String action,
            String targetPattern,
            boolean ignoreCase,
            List<String> content,
            boolean inheritStyle,
            String onNotFound) {
        ParsedActionName parsed = ActionNameParser.parse(action);
        return new SearchInsertConfig(
                Texts.lower(action),
                parsed.mode(),
                parsed.position(),
                parsed.matchIndex(),
                targetPattern,
                ignoreCase,
                content,
                inheritStyle,
                OnNotFoundPolicy.fromKey(onNotFound)
        ).validate();
    }

    // ==================== 验证逻辑 ====================
    /**
     * 验证配置的有效性
     *
     * @return 当前实例（验证通过）
     * @throws SearchInsertValidationException 配置无效时抛出
     */
    public SearchInsertConfig validate() {
        if (!ActionNameParser.isSearchInsertAction(actionType)) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_ACTION_NAME,
                    "Invalid lore search insert action name: " + actionType
            );
        }
        if (Texts.isBlank(searchPattern)) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_TARGET_PATTERN,
                    "Lore search insert target_pattern cannot be blank."
            );
        }
        if (contentLines == null || contentLines.isEmpty()) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_CONTENT,
                    "Lore search insert content cannot be empty."
            );
        }
        if (matchIndex <= 0) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_MATCH_INDEX,
                    "Lore search insert match index must be >= 1."
            );
        }
        if (onNotFoundPolicy == null) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_ON_NOT_FOUND,
                    "Lore search insert on_not_found policy is required."
            );
        }
        if (searchMode == SearchMode.REGEX) {
            String normalizedPattern = LoreTextUtil.stripColorCodes(searchPattern);
            try {
                Pattern.compile(normalizedPattern);
            } catch (PatternSyntaxException exception) {
                throw new SearchInsertValidationException(
                        SearchInsertValidationException.Reason.INVALID_REGEX,
                        "Invalid lore search regex: " + exception.getMessage()
                );
            }
        }
        return this;
    }

    // ==================== 序列化方法 ====================
    /**
     * 将配置序列化为 JSON 字符串
     *
     * @return JSON 格式的配置字符串
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * 从 JSON 字符串反序列化配置
     *
     * @param json JSON 格式的配置字符串
     * @return 验证后的配置实例
     * @throws SearchInsertValidationException JSON 无效或配置错误时抛出
     */
    public static SearchInsertConfig fromJson(String json) {
        if (Texts.isBlank(json)) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_SERIALIZED_CONFIG,
                    "Serialized lore search insert config is blank."
            );
        }
        try {
            SearchInsertConfig config = GSON.fromJson(json, SearchInsertConfig.class);
            if (config == null) {
                throw new SearchInsertValidationException(
                        SearchInsertValidationException.Reason.INVALID_SERIALIZED_CONFIG,
                        "Serialized lore search insert config is missing."
                );
            }
            return config.validate();
        } catch (JsonParseException exception) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_SERIALIZED_CONFIG,
                    "Failed to parse lore search insert config JSON: " + exception.getMessage()
            );
        }
    }
}
