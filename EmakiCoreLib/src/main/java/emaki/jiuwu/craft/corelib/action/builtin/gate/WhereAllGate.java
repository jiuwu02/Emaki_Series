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

public final class WhereAllGate extends BaseGate {

    private final TargetFactsReader factsReader;
    private final TargetConditionEvaluator evaluator;

    public WhereAllGate(TargetFactsReader factsReader, TargetConditionEvaluator evaluator) {
        super("where_all", "只保留同时满足全部谓词的目标；谓词以 || 分隔。",
                CoreGateThread.NEEDS_ENTITY_READ,
                CoreStageParameter.positional("conditions", CoreStageParameterType.STRING,
                        "多个谓词，|| 分隔，如 'player || health_percent<=50'"));
        this.factsReader = factsReader;
        this.evaluator = evaluator;
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        String raw = arguments.getString("conditions");
        if (Texts.isBlank(raw)) {
            return CoreGateResult.invalid("action.gate.where_all.conditions_required");
        }
        List<ConditionGroup> groups = new ArrayList<>();
        for (String part : raw.split("\\|\\|")) {
            String trimmed = Texts.trim(part);
            if (trimmed.isEmpty()) {
                continue;
            }
            TargetPredicateParser.Result parsed = TargetPredicateParser.parse(trimmed);
            if (parsed instanceof TargetPredicateParser.Result.Invalid invalid) {
                return CoreGateResult.invalid(invalid.reasonKey(), invalid.args());
            }
            groups.add(((TargetPredicateParser.Result.Parsed) parsed).group());
        }
        if (groups.isEmpty()) {
            return CoreGateResult.invalid("action.gate.where_all.conditions_required");
        }
        Location origin = origin(context);
        List<CoreActionSubject> matched = new ArrayList<>(inbound.size());
        for (CoreActionSubject subject : inbound) {
            TargetFacts facts = factsReader.read(subject, origin);
            boolean all = true;
            for (ConditionGroup group : groups) {
                if (!evaluator.matches(group, true, TargetConditionContext.of(facts))) {
                    all = false;
                    break;
                }
            }
            if (all) {
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
