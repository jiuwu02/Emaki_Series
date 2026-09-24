package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.action.pipeline.ResolvedArguments;
import emaki.jiuwu.craft.corelib.action.select.ConfiguredSelectorRepository;
import emaki.jiuwu.craft.corelib.action.select.SelectorArguments;
import emaki.jiuwu.craft.corelib.action.select.SelectorDefinition;
import emaki.jiuwu.craft.corelib.action.select.TargetConditionContext;
import emaki.jiuwu.craft.corelib.action.select.TargetConditionEvaluator;
import emaki.jiuwu.craft.corelib.action.select.TargetFacts;
import emaki.jiuwu.craft.corelib.action.select.TargetFactsReader;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class SelectSource extends BaseSource {

    private final ConfiguredSelectorRepository selectors;
    private final TargetFactsReader factsReader;
    private final TargetConditionEvaluator evaluator;
    private final Function<String, CoreActionSource> sources;

    public SelectSource(ConfiguredSelectorRepository selectors,
            TargetFactsReader factsReader,
            TargetConditionEvaluator evaluator,
            Function<String, CoreActionSource> sources) {
        super("select", "Targets chosen by a selector defined under action.selectors.",
                CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.positional("name", CoreStageParameterType.STRING, "Selector id"));
        this.selectors = selectors;
        this.factsReader = factsReader;
        this.evaluator = evaluator;
        this.sources = sources;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        CoreActionSource delegate = delegateOf(context.argument("name"));
        return delegate == null ? super.executionTarget(context) : delegate.executionTarget(context);
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        String name = Texts.trim(arguments.getString("name"));
        if (name.isEmpty()) {
            return CoreSourceResult.invalid("action.source.select.name_required");
        }
        SelectorDefinition definition = selectors.find(name);
        if (definition == null) {
            return CoreSourceResult.invalid("action.source.select.unknown_selector",
                    Map.of("name", name, "selectors", String.join(", ", selectors.ids())));
        }
        if (!definition.defined()) {
            return CoreSourceResult.invalid("action.source.select.missing_source",
                    Map.of("selector", definition.id()));
        }
        if (definition.broken()) {
            return CoreSourceResult.invalid(definition.conditionProblem(), definition.problemArguments());
        }
        CoreActionSource delegate = resolve(definition.sourceId());
        if (delegate == null) {
            return CoreSourceResult.invalid("action.source.select.unknown_source",
                    Map.of("selector", definition.id(), "source", definition.sourceId()));
        }
        SelectorArguments.Result merged = SelectorArguments.merge(
                renderedArguments(definition, context), arguments.raw(), declaredNames(delegate));
        if (merged instanceof SelectorArguments.Result.UnknownArgument unknown) {
            return CoreSourceResult.invalid("action.source.select.unknown_argument",
                    Map.of("argument", unknown.key(), "source", definition.sourceId()));
        }
        Map<String, String> values = ((SelectorArguments.Result.Merged) merged).values();
        CoreSourceResult outcome = delegate.select(context, ResolvedArguments.of(values, delegate.parameters()));
        if (!(outcome instanceof CoreSourceResult.Selected selected)) {
            return outcome;
        }
        return filter(definition, selected.subjects(), context);
    }

    private @Nullable CoreActionSource delegateOf(@Nullable String selectorId) {
        SelectorDefinition definition = selectors.find(selectorId);
        return definition == null ? null : resolve(definition.sourceId());
    }

    private @Nullable CoreActionSource resolve(@Nullable String sourceId) {
        String id = Texts.lower(Texts.toStringSafe(sourceId));
        if (id.isEmpty() || ConfiguredSelectorRepository.SELECT_SOURCE_ID.equals(id)) {
            return null;
        }
        return sources.apply(id);
    }

    private static Map<String, String> renderedArguments(SelectorDefinition definition, CoreStageContext context) {
        Map<String, String> values = new LinkedHashMap<>();
        definition.sourceArguments().forEach((key, value) -> values.put(key, context.render(value)));
        return values;
    }

    private static List<String> declaredNames(CoreActionSource delegate) {
        return delegate.parameters().stream().map(parameter -> Texts.lower(parameter.name())).toList();
    }

    private CoreSourceResult filter(SelectorDefinition definition,
            List<CoreActionSubject> subjects,
            CoreStageContext context) {
        if (!definition.conditioned()) {
            return selected(limit(definition.limit(), subjects));
        }
        Location origin = origin(context);
        List<CoreActionSubject> matched = new ArrayList<>(subjects.size());
        for (CoreActionSubject subject : subjects) {
            TargetFacts facts = factsReader.read(subject, origin);
            TargetConditionContext conditionContext = new TargetConditionContext(facts,
                    context::render,
                    (condition, conditionArguments) -> condition.test(subject, context, conditionArguments));
            if (evaluator.matches(definition.condition(), definition.invalidAsFailure(), conditionContext)) {
                matched.add(subject);
            }
        }
        return selected(limit(definition.limit(), matched));
    }

    private static CoreSourceResult selected(List<CoreActionSubject> matched) {
        return matched.isEmpty()
                ? CoreSourceResult.empty("action.source.select.no_match")
                : CoreSourceResult.selected(matched);
    }

    private static List<CoreActionSubject> limit(int limit, List<CoreActionSubject> matched) {
        if (limit <= 0 || matched.size() <= limit) {
            return List.copyOf(matched);
        }
        return List.copyOf(matched.subList(0, limit));
    }

    private static Location origin(CoreStageContext context) {
        try {
            return context.origin();
        } catch (IllegalStateException exception) {
            return null;
        }
    }
}