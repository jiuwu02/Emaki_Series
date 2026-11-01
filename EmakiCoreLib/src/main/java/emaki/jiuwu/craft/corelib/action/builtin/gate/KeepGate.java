package emaki.jiuwu.craft.corelib.action.builtin.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;

public final class KeepGate extends BaseGate {

    public KeepGate() {
        super("keep", "将当前目标流标记为后续沿用的一条。", CoreGateThread.PURE);
    }

    @Override
    public @NotNull Set<String> providedVariables() {
        return Set.of("keep_count");
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        return CoreGateResult.passed(new ArrayList<>(inbound),
                Map.of("keep_count", String.valueOf(inbound.size())), Map.of());
    }
}
