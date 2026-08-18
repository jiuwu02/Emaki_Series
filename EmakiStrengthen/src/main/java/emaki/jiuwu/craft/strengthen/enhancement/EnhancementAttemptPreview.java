package emaki.jiuwu.craft.strengthen.enhancement;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.strengthen.api.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.enhancement.cost.ConsumeTimingEnum;

/**
 * 一次通用强化尝试在不产生副作用的情况下解析出的预览。
 *
 * <p>预览与执行共用同一套目标读取、材料匹配、保底和费用解析逻辑，避免 GUI 展示值与确认时
 * 实际扣费/判定值分叉。调用方仍需在目标所属线程上读取物品；玩家上下文由执行服务显式传给 Provider。
 */
public record EnhancementAttemptPreview(
        boolean valid,
        @NotNull String errorKey,
        @NotNull Map<String, String> placeholders,
        int previousLevel,
        int resultingLevel,
        double baseRate,
        double effectiveRate,
        int pityCounter,
        boolean pityTriggered,
        @NotNull List<AttemptCost> costs,
        @NotNull List<MaterialRequirement> materials) {

    public EnhancementAttemptPreview {
        errorKey = errorKey == null ? "" : errorKey;
        placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
        costs = costs == null ? List.of() : List.copyOf(costs);
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    public static EnhancementAttemptPreview rejected(String errorKey) {
        return new EnhancementAttemptPreview(false, errorKey, Map.of(), 0, 0, 0D, 0D, 0, false, List.of(), List.of());
    }

    public static EnhancementAttemptPreview invalid(String errorKey,
            int previousLevel,
            int resultingLevel,
            double baseRate,
            double effectiveRate,
            int pityCounter,
            boolean pityTriggered,
            List<AttemptCost> costs,
            List<MaterialRequirement> materials) {
        return new EnhancementAttemptPreview(false, errorKey, Map.of(), previousLevel, resultingLevel,
                baseRate, effectiveRate, pityCounter, pityTriggered, costs, materials);
    }

    public static EnhancementAttemptPreview valid(String recipeId,
            int previousLevel,
            int resultingLevel,
            double baseRate,
            double effectiveRate,
            int pityCounter,
            boolean pityTriggered,
            List<AttemptCost> costs,
            List<MaterialRequirement> materials) {
        Map<String, String> placeholders = recipeId == null || recipeId.isBlank()
                ? Map.of()
                : Map.of("recipe", recipeId);
        return new EnhancementAttemptPreview(true, "", placeholders, previousLevel, resultingLevel,
                baseRate, effectiveRate, pityCounter, pityTriggered, costs, materials);
    }

    public record MaterialRequirement(
            @NotNull String slotId,
            int required,
            int supplied,
            @NotNull ConsumeTimingEnum consumeTiming) {

        public MaterialRequirement {
            slotId = slotId == null ? "" : slotId;
            consumeTiming = consumeTiming == null ? ConsumeTimingEnum.ALWAYS : consumeTiming;
            required = Math.max(0, required);
            supplied = Math.max(0, supplied);
        }

        public boolean satisfied() {
            return supplied >= required;
        }
    }
}
