package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;

public abstract class BaseGate implements CoreActionGate {

    private final String id;
    private final String description;
    private final List<CoreStageParameter> parameters;
    private final CoreGateThread threadNeed;

    protected BaseGate(String id,
            String description,
            CoreGateThread threadNeed,
            CoreStageParameter... parameters) {
        this.id = id;
        this.description = description;
        this.threadNeed = threadNeed == null ? CoreGateThread.PURE : threadNeed;
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
    public final @NotNull CoreGateThread threadNeed() {
        return threadNeed;
    }
}
