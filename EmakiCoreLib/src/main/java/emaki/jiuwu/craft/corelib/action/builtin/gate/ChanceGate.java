package emaki.jiuwu.craft.corelib.action.builtin.gate;

import java.util.ArrayList;
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
import emaki.jiuwu.craft.corelib.api.text.Texts;

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
            return CoreGateResult.invalid("action.gate.chance.chance_required");
        }
        double chance = arguments.getChance("chance", -1D);
        if (chance < 0D || chance > 1D) {
            return CoreGateResult.invalid("action.gate.chance.invalid_chance");
        }
        if (chance <= 0D) {
            return CoreGateResult.halted("action.gate.chance.not_rolled");
        }
        if (chance < 1D && ThreadLocalRandom.current().nextDouble() >= chance) {
            return CoreGateResult.halted("action.gate.chance.not_rolled");
        }
        return CoreGateResult.passed(new ArrayList<>(inbound));
    }
}
