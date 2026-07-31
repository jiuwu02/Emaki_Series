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
 * Repeats every following stage on an interval.
 *
 * <p>Written as {@code every <interval> times <count>}. {@code times} counts the extra runs on top of the
 * first, so {@code times 0} — the default — runs the body once. The count is capped by
 * {@code action.pipeline.max_repeat_times} (default 100) and exceeding it rejects the configuration rather
 * than silently truncating it, because a capped {@code times=100000} would look like it worked.</p>
 *
 * <p>Registered for the same reason as {@code after}, and its {@link #apply} is likewise the defensive
 * fallback rather than the normal path.</p>
 *
 * <p>Thread need {@code PURE}: the repetition is the scheduler's work.</p>
 */
public final class EveryGate extends BaseGate {

    public EveryGate() {
        super("every", "Repeats every following stage on an interval.", CoreGateThread.PURE,
                CoreStageParameter.optional("interval", CoreStageParameterType.DURATION, "1t",
                        "Interval such as 20t or 1s"),
                CoreStageParameter.optional("times", CoreStageParameterType.INTEGER, "0",
                        "Extra runs after the first"));
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        return CoreGateResult.passed(new ArrayList<>(inbound));
    }
}
