package emaki.jiuwu.craft.corelib.action.builtin.gate;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;

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
        return CoreGateResult.halted("action.gate.stop.stopped");
    }
}
