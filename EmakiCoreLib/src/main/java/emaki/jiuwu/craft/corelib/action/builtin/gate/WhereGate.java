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

/**
 * Drops the target flow when its condition does not hold.
 *
 * <p>Thread need {@code NEEDS_ENTITY_READ}: a condition may contain {@code %target.health%}, and reading
 * a placeholder off an entity has to happen on the thread that owns it.</p>
 *
 * <p><strong>Current granularity is the whole flow, not the individual target.</strong> A gate is invoked
 * once per flow and its arguments are rendered before the call, so the condition arrives already
 * substituted against the first target. Per-target filtering needs the interpreter to invoke gates once
 * per target with a per-target context; until that exists, {@code where} either keeps the flow intact or
 * clears it. A single-target flow — the common {@code looking_at | where ... | damage} shape — behaves
 * exactly as documented either way.</p>
 */
public final class WhereGate extends BaseGate {

    public WhereGate() {
        super("where", "Keeps only the targets whose condition holds.",
                CoreGateThread.NEEDS_ENTITY_READ,
                // STRING rather than EXPRESSION: the validator checks an EXPRESSION literal by evaluating it as
                // arithmetic, and `3>2` is a boolean condition that has no numeric value. Branch `if` conditions
                // are likewise not literal-checked, so this keeps the two condition forms consistent. An
                // unevaluable condition is still caught, at run time, by apply below.
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
        // The interpreter already rendered the arguments against the context, so the condition arrives
        // with its placeholders substituted; only the boolean evaluation is left.
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
