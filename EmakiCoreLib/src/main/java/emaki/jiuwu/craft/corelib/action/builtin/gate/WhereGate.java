package emaki.jiuwu.craft.corelib.action.builtin.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseGate;
import emaki.jiuwu.craft.corelib.action.select.TargetFacts;
import emaki.jiuwu.craft.corelib.action.select.TargetFactsReader;
import emaki.jiuwu.craft.corelib.action.select.TargetPlaceholders;
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

    private final TargetFactsReader factsReader;

    public WhereGate(TargetFactsReader factsReader) {
        super("where", "只保留条件成立的目标。",
                CoreGateThread.NEEDS_ENTITY_READ,

                CoreStageParameter.positional("condition", CoreStageParameterType.STRING,
                        "布尔条件"));
        this.factsReader = factsReader;
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        String condition = arguments.getString("condition");
        if (Texts.isBlank(condition)) {
            return CoreGateResult.invalid("action.gate.where.condition_required");
        }
        Location origin = origin(context);
        List<CoreActionSubject> matched = new ArrayList<>(inbound.size());
        for (CoreActionSubject subject : inbound) {
            Boolean evaluated = evaluate(condition, subject, origin);
            if (evaluated == null) {
                return CoreGateResult.invalid("action.gate.where.invalid_condition",
                        Map.of("condition", condition));
            }
            if (evaluated) {
                matched.add(subject);
            }
        }
        return CoreGateResult.passed(matched);
    }

    private Boolean evaluate(String condition, CoreActionSubject subject, Location origin) {
        TargetFacts facts = factsReader.read(subject, origin);
        return ExpressionEngine.evaluateBoolean(TargetPlaceholders.expand(condition, facts));
    }

    private static Location origin(CoreStageContext context) {
        try {
            return context.origin();
        } catch (IllegalStateException exception) {
            return null;
        }
    }
}
