package emaki.jiuwu.craft.corelib.action.builtin.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;

/**
 * Marks the current target flow as the one to carry forward.
 *
 * <p>Replaces the {@code save=target} argument of the old {@code ray} action, which wrote to the shared
 * context as a side effect of selecting a target.</p>
 *
 * <p>Inside a pipeline the flow already passes from stage to stage, so this gate passes it through unchanged.
 * What makes it more than a no-op is that the interpreter records the flow it saw into
 * {@code PipelineOutcome.keptFlow()}, which is how a caller hands one phase's targets to the next one; a
 * Skills script writes {@code looking_at | keep} in {@code cast} and reads it back with {@code inherited} in
 * {@code hit}.</p>
 *
 * <p>It records the flow at the point it runs, not the pipeline's final flow, so where the line sits within
 * its phase does not matter. A later gate that narrows the flow does not change what was recorded, and a
 * second {@code keep} replaces the first.</p>
 *
 * <p>Thread need {@code PURE}: passes a list along.</p>
 */
public final class KeepGate extends BaseGate {

    public KeepGate() {
        super("keep", "Marks the current target flow as the one to carry forward.", CoreGateThread.PURE);
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        return CoreGateResult.passed(new ArrayList<>(inbound),
                Map.of("keep_count", String.valueOf(inbound.size())), Map.of());
    }
}
