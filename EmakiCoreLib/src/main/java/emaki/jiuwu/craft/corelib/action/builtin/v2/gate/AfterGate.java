package emaki.jiuwu.craft.corelib.action.builtin.v2.gate;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;

/**
 * Delays every stage that follows it.
 *
 * <p>A timing stage rather than a flow transform: the interpreter recognises the id {@code after}, takes
 * the rest of the pipeline as this stage's body, and schedules that body with the delay. It also
 * revalidates caster, targets and owner before the body runs, so a target that disappeared during the wait
 * yields {@code Skipped}.</p>
 *
 * <p>Registration is still required even though {@link #apply} is not part of the normal path: the static
 * validator rejects any stage id it cannot resolve, so an unregistered {@code after} would make every
 * pipeline using it fail to compile. The body below is the defensive fallback for a hand-built AST that
 * bypasses the interpreter's timing branch.</p>
 *
 * <p>Thread need {@code PURE}: the delay is the scheduler's work, not this stage's.</p>
 */
public final class AfterGate extends BaseGate {

    public AfterGate() {
        super("after", "Delays every following stage.", CoreGateThread.PURE,
                CoreStageParameter.positional("delay", CoreStageParameterType.DURATION,
                        "Delay such as 10t, 500ms or 2s"));
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        return CoreGateResult.passed(new ArrayList<>(inbound));
    }
}
