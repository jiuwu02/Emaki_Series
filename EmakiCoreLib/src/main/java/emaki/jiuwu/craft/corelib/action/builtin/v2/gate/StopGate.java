package emaki.jiuwu.craft.corelib.action.builtin.v2.gate;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;

/**
 * Ends the pipeline here.
 *
 * <p>Reports {@code Halted}, so the pipeline outcome is {@code Skipped} rather than a failure: stopping on
 * purpose is not an error. Mostly useful inside a branch, as in
 * {@code if %var.dead% [ stop ] else [ damage amount=5 ]}.</p>
 *
 * <p>Thread need {@code PURE}: returns a decision and touches nothing.</p>
 */
public final class StopGate extends BaseGate {

    public StopGate() {
        super("stop", "Ends the pipeline here.", CoreGateThread.PURE);
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        return CoreGateResult.halted("action.v2.gate.stop.stopped");
    }
}
