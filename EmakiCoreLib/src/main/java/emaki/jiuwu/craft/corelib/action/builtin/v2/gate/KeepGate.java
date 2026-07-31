package emaki.jiuwu.craft.corelib.action.builtin.v2.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;

/**
 * Marks the current target flow as the one to carry forward.
 *
 * <p>Replaces the {@code save=target} argument of the old {@code ray} action, which wrote to the shared
 * context as a side effect of selecting a target.</p>
 *
 * <p><strong>Scope in this phase is the pipeline, not the phase boundary.</strong> Inside a pipeline the
 * flow already passes from stage to stage, so this gate passes it through unchanged and records
 * {@code keep_count} for diagnostics. Persisting a flow so that a <em>later</em> phase can pick it up with
 * {@code inherited} needs a place to store it, which arrives with the Skills script context; this stage is
 * the syntax that will bind to it, registered now so pipeline text does not have to change later.</p>
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
