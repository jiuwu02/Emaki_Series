package emaki.jiuwu.craft.corelib.action.builtin.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class WhereGate extends BaseGate {

    public WhereGate() {
        super("where", "Keeps only the targets whose condition holds.",
                CoreGateThread.NEEDS_ENTITY_READ,

                CoreStageParameter.positional("condition", CoreStageParameterType.STRING,
                        "Boolean condition"));
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        String condition = arguments.getString("condition");
        if (Texts.isBlank(condition)) {
            return CoreGateResult.invalid("action.gate.where.condition_required");
        }

        Boolean evaluated = ExpressionEngine.evaluateBoolean(condition);
        if (evaluated == null) {
            return CoreGateResult.invalid("action.gate.where.invalid_condition",
                    Map.of("condition", condition));
        }
        if (!evaluated) {
            return CoreGateResult.passed(List.of());
        }
        return CoreGateResult.passed(new ArrayList<>(inbound));
    }
}
