package emaki.jiuwu.craft.corelib.action.builtin.gate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class SetGate extends BaseGate {

    public SetGate() {
        super("set", "Writes pipeline variables readable as %var.name%.", CoreGateThread.PURE);
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        Map<String, String> raw = arguments.raw();
        if (raw.isEmpty()) {
            return CoreGateResult.invalid("action.gate.set.no_assignment");
        }
        Map<String, String> variables = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (Texts.isBlank(entry.getKey())) {
                continue;
            }
            variables.put(Texts.lower(entry.getKey()), evaluate(entry.getValue()));
        }
        if (variables.isEmpty()) {
            return CoreGateResult.invalid("action.gate.set.no_assignment");
        }
        return CoreGateResult.passed(new ArrayList<>(inbound), variables, Map.of());
    }

    private static String evaluate(String value) {
        String text = Texts.toStringSafe(value);
        if (text.isBlank()) {
            return text;
        }
        ExpressionEngine.NumericEvaluationResult evaluated = ExpressionEngine.evaluateNumericDetailed(text);
        if (!evaluated.success()) {
            return text;
        }
        double numeric = evaluated.value();
        return numeric == Math.rint(numeric) && !Double.isInfinite(numeric)
                ? String.valueOf((long) numeric)
                : String.valueOf(numeric);
    }
}
