package emaki.jiuwu.craft.corelib.action.builtin.v2;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;

/** Metadata carrier for the builtin gate stages. */
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
