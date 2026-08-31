package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

public abstract class BaseStage implements CoreActionStage {

    private final String id;
    private final String category;
    private final String description;
    private final CoreTargetRequirement targetRequirement;
    private final CoreActionExecutionDomain domain;
    private final List<CoreStageParameter> parameters;

    protected BaseStage(String id,
            String category,
            String description,
            CoreTargetRequirement targetRequirement,
            CoreActionExecutionDomain domain,
            CoreStageParameter... parameters) {
        this.id = id;
        this.category = category;
        this.description = description;
        this.targetRequirement = targetRequirement;
        this.domain = domain;
        this.parameters = parameters == null ? List.of() : List.of(parameters);
    }

    @Override
    public final @NotNull String id() {
        return id;
    }

    @Override
    public final @NotNull String category() {
        return category;
    }

    @Override
    public final @NotNull String description() {
        return description;
    }

    @Override
    public final @NotNull List<CoreStageParameter> parameters() {
        return parameters;
    }

    @Override
    public final @NotNull CoreTargetRequirement targetRequirement() {
        return targetRequirement;
    }

    @Override
    public @NotNull Set<CoreActionKey<?>> requiredContext() {
        return Set.of();
    }

    @Override
    public final @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return BuiltinDomains.target(domain);
    }
}
