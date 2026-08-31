package emaki.jiuwu.craft.corelib.action.builtin.gate;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

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
            return CoreGateResult.invalid("action.gate.limit.invalid_count");
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
