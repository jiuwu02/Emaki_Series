package emaki.jiuwu.craft.corelib.assembly.lore;

import java.util.ArrayList;
import java.util.List;

/**
 * Lore 搜索插入操作结果类（标准化版本）
 * <p>
 * 该类封装了搜索插入操作的执行结果，包括修改后的 Lore 行列表、
 * 执行状态、状态消息以及是否发生变更的标志。
 * 字段命名遵循标准化规范，使用小驼峰命名法。
 * </p>
 *
 * @since 2.0.0
 */
public record LoreSearchInsertResult(
        /** 修改后的 Lore 行列表（可能包含新增或替换的行） */
        List<String> modifiedLore,

        /** 操作执行状态（成功/失败/跳过等） */
        LoreSearchInsertStatus executionStatus,

        /** 状态详细描述信息（错误原因、警告等） */
        String statusMessage,

        /** 标志位：是否对原始 Lore 进行了实际修改 */
        boolean mutationApplied) {

    /**
     * 规范化构造函数 - 验证并初始化所有结果字段
     */
    public LoreSearchInsertResult {
        // 规范化 Lore 列表
        List<String> normalized = new ArrayList<>();
        if (modifiedLore != null) {
            normalized.addAll(modifiedLore);
        }
        modifiedLore = normalized.isEmpty() ? List.of() : List.copyOf(normalized);

        // 设置默认值
        executionStatus = executionStatus == null ? LoreSearchInsertStatus.INVALID_CONFIG : executionStatus;
        statusMessage = statusMessage == null ? "" : statusMessage;

        // 自动计算 mutationApplied 标志（如果未显式提供）
        // 注意：如果调用者显式提供了 mutationApplied，则使用该值
        if (!mutationApplied) {
            // 默认逻辑：根据执行状态判断是否发生了变更
            mutationApplied = (executionStatus == LoreSearchInsertStatus.APPLIED
                    || executionStatus == LoreSearchInsertStatus.APPENDED_TO_END);
        }
    }

    /**
     * 便捷构造函数 - 仅传入核心字段，自动计算 mutationApplied
     *
     * @param modifiedLore 修改后的 Lore 列表
     * @param executionStatus 执行状态
     * @param statusMessage 状态消息
     */
    public LoreSearchInsertResult(List<String> modifiedLore,
            LoreSearchInsertStatus executionStatus,
            String statusMessage) {
        this(modifiedLore, executionStatus, statusMessage, false);
        // 构造函数会自动计算 mutationApplied
    }
}
