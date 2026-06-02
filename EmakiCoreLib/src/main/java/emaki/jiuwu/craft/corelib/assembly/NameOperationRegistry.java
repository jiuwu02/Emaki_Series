package emaki.jiuwu.craft.corelib.assembly;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class NameOperationRegistry {

    private final OperationTemplateRenderer templateRenderer;
    private final Map<String, NameOperationProcessor> processors = new LinkedHashMap<>();

    public NameOperationRegistry(OperationTemplateRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
        register("replace", new ReplaceProcessor());
        register("prepend_prefix", new PrependPrefixProcessor());
        register("append_suffix", new AppendSuffixProcessor());
        register("regex_replace", new RegexReplaceProcessor());
    }

    public NameOperationProcessor getProcessor(String action) {
        return processors.get(Texts.lower(action));
    }

    public void register(String action, NameOperationProcessor processor) {
        processors.put(Texts.lower(action), processor);
    }

    public void apply(LocalNameState state, Object operations, Map<String, Object> variables) {
        apply(state, operations, variables, null, null);
    }

    public void apply(LocalNameState state,
            Object operations,
            Map<String, Object> variables,
            ActionContext context,
            DebugLogger debugLogger) {
        if (state == null) {
            return;
        }
        for (Map<String, Object> operation : templateRenderer.normalizeOperations(operations)) {
            String action = Texts.lower(operation.get("action"));
            String value = templateRenderer.renderTemplate(
                    templateRenderer.resolveOperationValue(operation),
                    variables,
                    context,
                    debugLogger,
                    "item_operation.name." + action
            );
            NameOperationProcessor processor = getProcessor(action);
            if (processor != null) {
                processor.process(state, new NameOperationProcessor.Context(operation, value, variables));
            }
        }
    }

    public OperationTemplateRenderer templateRenderer() {
        return templateRenderer;
    }

    public Set<String> registeredActions() {
        return Set.copyOf(processors.keySet());
    }


    private static final class ReplaceProcessor implements NameOperationProcessor {

        @Override
        public void process(LocalNameState state, Context context) {
            state.replaceBase(context.value());
        }
    }

    private static final class PrependPrefixProcessor implements NameOperationProcessor {

        @Override
        public void process(LocalNameState state, Context context) {
            if (Texts.isNotBlank(context.value())) {
                state.addPrefix(context.value());
            }
        }
    }

    private static final class AppendSuffixProcessor implements NameOperationProcessor {

        @Override
        public void process(LocalNameState state, Context context) {
            if (Texts.isNotBlank(context.value())) {
                state.addPostfix(context.value());
            }
        }
    }

    private static final class RegexReplaceProcessor implements NameOperationProcessor {

        @Override
        public void process(LocalNameState state, Context context) {
            state.applyRegexReplace(
                    Texts.toStringSafe(context.operation().get("regex_pattern")),
                    Texts.toStringSafe(context.operation().get("replacement")),
                    context.variables()
            );
        }
    }
}
