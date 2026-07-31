package emaki.jiuwu.craft.corelib.action.builtin.v2.gate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Writes pipeline variables, readable later as {@code %var.<name>%}.
 *
 * <p>Declares no parameters because the key names are chosen by whoever writes the pipeline:
 * {@code set damage=%skill.level%*4+18} names a variable that CoreLib cannot know in advance. The static
 * validator therefore skips its unknown-argument check for this stage id; see {@code StaticValidator}.</p>
 *
 * <p>Values that evaluate as arithmetic are stored as numbers so that {@code %var.damage%} reads back as
 * {@code 22} rather than {@code %skill.level%*4+18}. Anything else is stored verbatim.</p>
 *
 * <p>Thread need {@code PURE}: string and arithmetic work only.</p>
 */
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
            return CoreGateResult.invalid("action.v2.gate.set.no_assignment");
        }
        Map<String, String> variables = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (Texts.isBlank(entry.getKey())) {
                continue;
            }
            variables.put(Texts.lower(entry.getKey()), evaluate(entry.getValue()));
        }
        if (variables.isEmpty()) {
            return CoreGateResult.invalid("action.v2.gate.set.no_assignment");
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
