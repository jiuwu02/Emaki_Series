package emaki.jiuwu.craft.cooking.model;

import java.util.List;

/**
 * 组合营养阈值规则：用于还原"膳食均衡"中多类营养同时满足后触发反胃的玩法。
 *
 * <p>当满足 {@code value}/{@code compare} 条件的营养类型数量达到 {@code requiredCount} 时，
 * 执行 {@link #onMeetActions()}（例如启动循环动作衰减所有营养并播放反胃效果）；
 * 当达标数量跌回不足 {@code requiredCount} 时执行 {@link #onRecoverActions()}（例如取消循环）。</p>
 *
 * <p>{@code requiredCount} 默认 5，可由服主自配，不强制等于营养类型总数。</p>
 *
 * @param id               规则 id（用于状态跟踪与循环 key 拼装）
 * @param types            参与统计的营养类型 id 列表；为空表示统计全部营养类型
 * @param value            阈值
 * @param compare          比较运算符
 * @param requiredCount    需要达标的营养类型数量
 * @param onMeetActions    达标数量满足时执行的动作行
 * @param onRecoverActions 达标数量从满足跌回不足时执行的动作行
 */
public record NutritionComboThreshold(String id,
        List<String> types,
        double value,
        NutritionCompare compare,
        int requiredCount,
        List<String> onMeetActions,
        List<String> onRecoverActions) {

    public NutritionComboThreshold {
        types = types == null ? List.of() : List.copyOf(types);
        onMeetActions = onMeetActions == null ? List.of() : List.copyOf(onMeetActions);
        onRecoverActions = onRecoverActions == null ? List.of() : List.copyOf(onRecoverActions);
        compare = compare == null ? NutritionCompare.GREATER_OR_EQUAL : compare;
        requiredCount = Math.max(1, requiredCount);
    }

    /**
     * 该规则是否统计给定营养类型。{@code types} 为空表示统计全部类型。
     */
    public boolean counts(String typeId) {
        return types.isEmpty() || types.contains(typeId);
    }
}
