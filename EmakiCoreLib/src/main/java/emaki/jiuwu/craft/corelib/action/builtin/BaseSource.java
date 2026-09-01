package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;

public abstract class BaseSource implements CoreActionSource {

    private final String id;
    private final String description;
    private final List<CoreStageParameter> parameters;
    private final CoreActionExecutionDomain domain;

    protected BaseSource(String id,
            String description,
            CoreActionExecutionDomain domain,
            CoreStageParameter... parameters) {
        this.id = id;
        this.description = description;
        this.domain = domain;
        this.parameters = parameters == null ? List.of() : List.of(parameters);
    }

    @Override
    public final @NotNull String id() {
        return id;
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
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return BuiltinDomains.target(domain);
    }
}
