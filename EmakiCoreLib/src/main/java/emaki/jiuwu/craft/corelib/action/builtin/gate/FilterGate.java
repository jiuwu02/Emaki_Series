package emaki.jiuwu.craft.corelib.action.builtin.gate;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseGate;
import emaki.jiuwu.craft.corelib.action.select.TargetConditionContext;
import emaki.jiuwu.craft.corelib.action.select.TargetConditionEvaluator;
import emaki.jiuwu.craft.corelib.action.select.TargetFacts;
import emaki.jiuwu.craft.corelib.action.select.TargetFactsReader;
import emaki.jiuwu.craft.corelib.action.select.TargetPredicateParser;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;

public final class FilterGate extends BaseGate {

    private final TargetFactsReader factsReader;
    private final TargetConditionEvaluator evaluator;

    public FilterGate(TargetFactsReader factsReader, TargetConditionEvaluator evaluator) {
        super("filter", "Keeps only the targets matching the inline predicates.",
                CoreGateThread.NEEDS_ENTITY_READ,

                CoreStageParameter.positional("condition", CoreStageParameterType.STRING,
                        "Inline predicates, such as 'entity_type=ZOMBIE health_percent<=50'"));
        this.factsReader = factsReader;
        this.evaluator = evaluator;
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        String condition = arguments.getString("condition");
        if (Texts.isBlank(condition)) {
            return CoreGateResult.invalid("action.gate.filter.condition_required");
        }
        TargetPredicateParser.Result parsed = TargetPredicateParser.parse(condition);
        if (parsed instanceof TargetPredicateParser.Result.Invalid invalid) {
            return CoreGateResult.invalid(invalid.reasonKey(), invalid.args());
        }
        ConditionGroup group = ((TargetPredicateParser.Result.Parsed) parsed).group();
        Location origin = origin(context);
        List<CoreActionSubject> matched = new ArrayList<>(inbound.size());
        for (CoreActionSubject subject : inbound) {
            TargetFacts facts = factsReader.read(subject, origin);
            if (evaluator.matches(group, true, TargetConditionContext.of(facts))) {
                matched.add(subject);
            }
        }
        return CoreGateResult.passed(matched);
    }

    private static Location origin(CoreStageContext context) {
        try {
            return context.origin();
        } catch (IllegalStateException exception) {
            return null;
        }
    }
}