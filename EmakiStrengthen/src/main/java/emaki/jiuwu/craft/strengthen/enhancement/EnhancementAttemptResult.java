package emaki.jiuwu.craft.strengthen.enhancement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.strengthen.api.model.EnhancementPityResult;

/**
 * 一次强化框架执行的结果。
 *
 * <p>区分三种情形：{@code committed=false} 表示前置校验未通过、未扣费、未改物品；
 * {@code committed=true && success=true} 表示成功并已写回；{@code committed=true && success=false}
 * 表示已扣费但判定失败，这也是一次「正常完成」的执行，而非错误。
 *
 * @param committed        是否真正执行了本次强化（含扣费与写回）
 * @param success          判定是否成功；仅在 {@code committed} 为真时有意义
 * @param errorKey         未提交时的原因键，供上层取语言文本；提交时为空串
 * @param placeholders     错误文本占位符
 * @param previousLevel    执行前的目标等级
 * @param resultingLevel   执行后的目标等级
 * @param successRate      本次实际生效的成功率（已含保底加成）
 * @param pityCounter      本次执行后的保底计数
 * @param pityTriggered    本次是否触发了保底
 */
public record EnhancementAttemptResult(boolean committed,
        boolean success,
        @NotNull String errorKey,
        @NotNull Map<String, String> placeholders,
        int previousLevel,
        int resultingLevel,
        double successRate,
        int pityCounter,
        boolean pityTriggered,
        @NotNull EnhancementPityResult pityResult) {

    public EnhancementAttemptResult {
        errorKey = errorKey == null ? "" : errorKey;
        placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
        pityResult = pityResult == null ? EnhancementPityResult.empty() : pityResult;
    }

    /**
     * Compatibility constructor retaining the original scalar pity payload.
     */
    public EnhancementAttemptResult(boolean committed,
            boolean success,
            @NotNull String errorKey,
            @NotNull Map<String, String> placeholders,
            int previousLevel,
            int resultingLevel,
            double successRate,
            int pityCounter,
            boolean pityTriggered) {
        this(committed, success, errorKey, placeholders, previousLevel, resultingLevel,
                successRate, pityCounter, pityTriggered, EnhancementPityResult.empty());
    }

    /**
     * Constructs a result from the multi-track pity payload and derives the legacy scalar fields.
     */
    public EnhancementAttemptResult(boolean committed,
            boolean success,
            @NotNull String errorKey,
            @NotNull Map<String, String> placeholders,
            int previousLevel,
            int resultingLevel,
            double successRate,
            @NotNull EnhancementPityResult pityResult) {
        this(committed, success, errorKey, placeholders, previousLevel, resultingLevel, successRate,
                pityResult == null ? 0 : pityResult.primaryCounter(),
                pityResult != null && pityResult.triggered(), pityResult);
    }

    /** 构造一个未提交的失败结果。 */
    public static @NotNull EnhancementAttemptResult rejected(@NotNull String errorKey) {
        return rejected(errorKey, Map.of());
    }

    /** 构造一个未提交的失败结果，附带占位符。 */
    public static @NotNull EnhancementAttemptResult rejected(@NotNull String errorKey,
            @Nullable Map<String, String> placeholders) {
        return new EnhancementAttemptResult(false, false, errorKey,
                placeholders == null ? Map.of() : placeholders, 0, 0, 0D, 0, false);
    }

    /** {@return 供动作/消息使用的占位符集合} */
    public @NotNull Map<String, String> toPlaceholders() {
        Map<String, String> values = new LinkedHashMap<>(placeholders);
        values.put("previous_level", String.valueOf(previousLevel));
        values.put("resulting_level", String.valueOf(resultingLevel));
        values.put("success", String.valueOf(success));
        values.put("success_rate", String.valueOf(successRate));
        values.put("pity_counter", String.valueOf(pityCounter));
        values.put("pity_triggered", String.valueOf(pityTriggered));
        return Map.copyOf(values);
    }

    /** {@return 本次执行涉及的动作阶段键} */
    public @NotNull List<String> actionPhaseKeys() {
        return success ? List.of("on_success") : List.of("on_failure");
    }
}
