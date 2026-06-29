package emaki.jiuwu.craft.cooking.model;

import java.util.List;

/**
 * 单营养类型阈值规则：当某个营养类型的值满足 {@code value}/{@code compare} 条件时，
 * 执行 {@link #onMeetActions()}；从满足跌回不满足时执行 {@link #onRecoverActions()}。
 *
 * <p>满足/恢复采用边沿触发，由服务层跟踪上一次状态，避免每次数值变化都重复执行动作。</p>
 *
 * @param id              规则 id（用于状态跟踪与循环 key 拼装）
 * @param types           适用的营养类型 id 列表；为空表示适用于全部营养类型
 * @param value           阈值
 * @param compare         比较运算符
 * @param onMeetActions   满足条件时执行的动作行
 * @param onRecoverActions 从满足恢复到不满足时执行的动作行
 */
public record NutritionSingleThreshold(String id,
        List<String> types,
        double value,
        NutritionCompare compare,
        List<String> onMeetActions,
        List<String> onRecoverActions) {

    public NutritionSingleThreshold {
        types = types == null ? List.of() : List.copyOf(types);
        onMeetActions = onMeetActions == null ? List.of() : List.copyOf(onMeetActions);
        onRecoverActions = onRecoverActions == null ? List.of() : List.copyOf(onRecoverActions);
        compare = compare == null ? NutritionCompare.GREATER_OR_EQUAL : compare;
    }

    /**
     * 该规则是否适用于给定营养类型。{@code types} 为空表示适用于全部类型。
     */
    public boolean appliesTo(String typeId) {
        return types.isEmpty() || types.contains(typeId);
    }
}
