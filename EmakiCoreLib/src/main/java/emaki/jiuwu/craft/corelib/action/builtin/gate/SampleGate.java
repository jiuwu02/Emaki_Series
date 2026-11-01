package emaki.jiuwu.craft.corelib.action.builtin.gate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

public final class SampleGate extends BaseGate {

    public SampleGate() {
        super("sample", "随机抽取 count 个目标。",
                CoreGateThread.PURE,
                CoreStageParameter.positional("count", CoreStageParameterType.INTEGER,
                        "随机抽取的目标数量"));
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        int count = arguments.getInt("count", -1);
        if (count < 0) {
            return CoreGateResult.invalid("action.gate.sample.invalid_count");
        }
        if (count >= inbound.size()) {
            return CoreGateResult.passed(new ArrayList<>(inbound));
        }
        List<CoreActionSubject> shuffled = new ArrayList<>(inbound);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        return CoreGateResult.passed(new ArrayList<>(shuffled.subList(0, count)));
    }
}
