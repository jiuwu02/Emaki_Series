package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

final class NameModificationRegistry {

    private final TextTemplateRenderer templateRenderer;
    private final Map<String, NameModificationProcessor> processors = new LinkedHashMap<>();

    NameModificationRegistry(TextTemplateRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
        register("replace", new ReplaceNameProcessor());
        register("prepend_prefix", new PrependPrefixProcessor());
        register("append_suffix", new AppendSuffixProcessor());
        register("regex_replace", new RegexReplaceNameProcessor());
    }

    NameModificationProcessor getProcessor(String action) {
        return processors.get(Texts.lower(action));
    }

    void apply(LocalNameState state, Object operations, Map<String, Object> variables) {
        if (state == null) {
            return;
        }
        for (Map<String, Object> operation : templateRenderer.normalizeOperations(operations)) {
            String action = Texts.lower(operation.get("action"));
            String value = templateRenderer.renderTemplate(templateRenderer.resolveOperationValue(operation), variables);
            NameModificationProcessor processor = getProcessor(action);
            if (processor != null) {
                processor.process(state, new NameModificationProcessor.Context(operation, value, variables));
            }
        }
    }

    private void register(String action, NameModificationProcessor processor) {
        processors.put(action, processor);
    }

    private static final class ReplaceNameProcessor implements NameModificationProcessor {

        @Override
        public void process(LocalNameState state, NameModificationProcessor.Context context) {
            state.replaceBase(context.value());
        }
    }

    private static final class PrependPrefixProcessor implements NameModificationProcessor {

        @Override
        public void process(LocalNameState state, NameModificationProcessor.Context context) {
            if (Texts.isNotBlank(context.value())) {
                state.addPrefix(context.value());
            }
        }
    }

    private static final class AppendSuffixProcessor implements NameModificationProcessor {

        @Override
        public void process(LocalNameState state, NameModificationProcessor.Context context) {
            if (Texts.isNotBlank(context.value())) {
                state.addPostfix(context.value());
            }
        }
    }

    private static final class RegexReplaceNameProcessor implements NameModificationProcessor {

        @Override
        public void process(LocalNameState state, NameModificationProcessor.Context context) {
            state.applyRegexReplace(
                    Texts.toStringSafe(context.operation().get("regex_pattern")),
                    Texts.toStringSafe(context.operation().get("replacement")),
                    context.variables()
            );
        }
    }
}
