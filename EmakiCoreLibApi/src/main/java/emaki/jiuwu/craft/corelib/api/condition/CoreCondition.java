package emaki.jiuwu.craft.corelib.api.condition;

import java.util.Map;

import org.jetbrains.annotations.NotNull;

public interface CoreCondition {

    @NotNull
    String id();

    @NotNull
    default String description() {
        return "";
    }

    @NotNull
    CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context);
}
