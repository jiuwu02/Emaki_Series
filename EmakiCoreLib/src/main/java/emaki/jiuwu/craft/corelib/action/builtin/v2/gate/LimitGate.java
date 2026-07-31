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
 * Keeps the first {@code count} targets, preserving the flow's order.
 *
 * <p>Thread need {@code PURE}: takes a sublist. Nothing about the subjects themselves is read.</p>
 */
public final class LimitGate extends BaseGate {

    public LimitGate() {
        super("limit", "Keeps the first count targets.",
                CoreGateThread.PURE,
                CoreStageParameter.positional("count", CoreStageParameterType.INTEGER,
                        "How many targets to keep"));
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        int count = arguments.getInt("count", -1);
        if (count < 0) {
            return CoreGateResult.invalid("action.v2.gate.limit.invalid_count");
        }
        if (count == 0) {
            return CoreGateResult.passed(List.of());
        }
        if (inbound.size() <= count) {
            return CoreGateResult.passed(new ArrayList<>(inbound));
        }
        return CoreGateResult.passed(new ArrayList<>(inbound.subList(0, count)));
    }
}
