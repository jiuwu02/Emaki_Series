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

public final class EveryGate extends BaseGate {

    public EveryGate() {
        super("every", "按间隔重复其后所有段。", CoreGateThread.PURE,
                CoreStageParameter.optional("interval", CoreStageParameterType.DURATION, "1t",
                        "间隔，例如 20t 或 1s"),
                CoreStageParameter.optional("times", CoreStageParameterType.INTEGER, "0",
                        "首次之后的额外运行次数"));
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        return CoreGateResult.passed(new ArrayList<>(inbound));
    }
}
