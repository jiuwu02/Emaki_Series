package emaki.jiuwu.craft.corelib.action.builtin.v2.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Stops the pipeline unless a probability roll succeeds.
 *
 * <p>Accepts {@code 50%}, {@code 0.5} and {@code 1/3}. Losing the roll is {@code Halted}, not
 * {@code Invalid}: a failed roll is the point of the stage, while {@code chance abc} is a typo the server
 * owner needs to see.</p>
 *
 * <p>Thread need {@code PURE}: draws a random number and compares it. No Bukkit state is touched, so this
 * gate folds into whichever domain its neighbours use.</p>
 */
public final class ChanceGate extends BaseGate {

    public ChanceGate() {
        super("chance", "Stops the pipeline unless a probability roll succeeds.",
                CoreGateThread.PURE,
                CoreStageParameter.positional("chance", CoreStageParameterType.PERCENTAGE,
                        "Probability such as 50%, 0.5 or 1/3"));
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        if (Texts.isBlank(arguments.getString("chance"))) {
            return CoreGateResult.invalid("action.v2.gate.chance.chance_required");
        }
        double chance = arguments.getChance("chance", -1D);
        if (chance < 0D || chance > 1D) {
            return CoreGateResult.invalid("action.v2.gate.chance.invalid_chance");
        }
        if (chance <= 0D) {
            return CoreGateResult.halted("action.v2.gate.chance.not_rolled");
        }
        if (chance < 1D && ThreadLocalRandom.current().nextDouble() >= chance) {
            return CoreGateResult.halted("action.v2.gate.chance.not_rolled");
        }
        return CoreGateResult.passed(new ArrayList<>(inbound));
    }
}
