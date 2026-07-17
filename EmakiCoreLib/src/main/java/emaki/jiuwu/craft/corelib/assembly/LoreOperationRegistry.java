package emaki.jiuwu.craft.corelib.assembly;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class LoreOperationRegistry {

    private final OperationTemplateRenderer templateRenderer;
    private final Map<String, LoreOperationProcessor> processors = new LinkedHashMap<>();

    public LoreOperationRegistry(OperationTemplateRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
        register("append", new AppendProcessor());
        register("prepend", new PrependProcessor());
        register("insert_below", new InsertProcessor(true));
        register("search_insert_below", new InsertProcessor(true));
        register("search_insert", new InsertProcessor(true));
        register("insert_above", new InsertProcessor(false));
        register("search_insert_above", new InsertProcessor(false));
        register("replace_line", new ReplaceLineProcessor());
        register("replace_text", new ReplaceTextProcessor(false));
        register("replace_text_all", new ReplaceTextProcessor(true));
        register("delete_line", new DeleteLineProcessor());
        register("regex_replace", new RegexReplaceProcessor());
    }

    public LoreOperationProcessor getProcessor(String action) {
        return processors.get(Texts.lower(action));
    }

    public void register(String action, LoreOperationProcessor processor) {
        processors.put(Texts.lower(action), processor);
    }

    public void apply(List<String> lines, Object operations, Map<String, Object> variables) {
        apply(lines, operations, variables, null, null);
    }

    public void apply(List<String> lines,
            Object operations,
            Map<String, Object> variables,
            ActionContext context,
            DebugLogger debugLogger) {
        if (lines == null) {
            return;
        }
        for (Map<String, Object> operation : templateRenderer.normalizeOperations(operations)) {
            String action = Texts.lower(operation.get("action"));
            List<String> content = templateRenderer.renderContent(operation, variables, context, debugLogger, "item_operation.lore." + action);
            String anchor = templateRenderer.renderTemplate(
                    templateRenderer.resolveSearchPattern(operation),
                    variables,
                    context,
                    debugLogger,
                    "item_operation.lore.anchor." + action
            );
            LoreOperationProcessor processor = getProcessor(action);
            if (processor != null) {
                processor.process(lines, new LoreOperationProcessor.Context(operation, content, anchor, variables));
            }
        }
    }

    public OperationTemplateRenderer templateRenderer() {
        return templateRenderer;
    }

    public Set<String> registeredActions() {
        return Set.copyOf(processors.keySet());
    }


    private static int findInsertIndex(List<String> lines, String anchor, boolean below) {
        if (Texts.isBlank(anchor)) {
            return below ? (lines == null ? 0 : lines.size()) : 0;
        }
        for (int index = 0; index < lines.size(); index++) {
            if (Texts.toStringSafe(lines.get(index)).contains(anchor)) {
                return below ? index + 1 : index;
            }
        }
        return lines.size();
    }

    private static final class AppendProcessor implements LoreOperationProcessor {

        @Override
        public void process(List<String> lines, Context context) {
            lines.addAll(context.content());
        }
    }

    private static final class PrependProcessor implements LoreOperationProcessor {

        @Override
        public void process(List<String> lines, Context context) {
            if (context.content().isEmpty()) {
                return;
            }
            lines.addAll(0, context.content());
        }
    }

    private static final class InsertProcessor implements LoreOperationProcessor {

        private final boolean below;

        private InsertProcessor(boolean below) {
            this.below = below;
        }

        @Override
        public void process(List<String> lines, Context context) {
            if (context.content().isEmpty()) {
                return;
            }
            int insertIndex = findInsertIndex(lines, context.anchor(), below);
            lines.addAll(insertIndex, context.content());
        }
    }

    private static final class ReplaceLineProcessor implements LoreOperationProcessor {

        @Override
        public void process(List<String> lines, Context context) {
            if (lines == null || Texts.isBlank(context.anchor())) {
                return;
            }
            String replacement = context.content().isEmpty() ? "" : context.content().get(0);
            for (int index = 0; index < lines.size(); index++) {
                if (Texts.toStringSafe(lines.get(index)).contains(context.anchor())) {
                    lines.set(index, Texts.toStringSafe(replacement));
                    return;
                }
            }
        }
    }

    private static final class ReplaceTextProcessor implements LoreOperationProcessor {

        private final boolean replaceAll;

        private ReplaceTextProcessor(boolean replaceAll) {
            this.replaceAll = replaceAll;
        }

        @Override
        public void process(List<String> lines, Context context) {
            if (lines == null || Texts.isBlank(context.anchor())) {
                return;
            }
            String replacement = context.content().isEmpty() ? "" : context.content().get(0);
            int requestedIndex = parseRequestedIndex(context.operation().get("index"));
            if (replaceAll) {
                replaceAll(lines, context.anchor(), replacement);
                return;
            }
            replaceNthOrLast(lines, context.anchor(), replacement, requestedIndex);
        }

        private void replaceAll(List<String> lines, String anchor, String replacement) {
            for (int index = 0; index < lines.size(); index++) {
                String current = Texts.toStringSafe(lines.get(index));
                if (current.contains(anchor)) {
                    lines.set(index, current.replace(anchor, replacement));
                }
            }
        }

        private void replaceNthOrLast(List<String> lines, String anchor, String replacement, int requestedIndex) {
            List<Integer> matches = new java.util.ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                if (Texts.toStringSafe(lines.get(index)).contains(anchor)) {
                    matches.add(index);
                }
            }
            if (matches.isEmpty()) {
                return;
            }
            int targetIndex;
            if (requestedIndex <= 0) {
                targetIndex = matches.get(0);
            } else if (requestedIndex <= matches.size()) {
                targetIndex = matches.get(requestedIndex - 1);
            } else {
                targetIndex = matches.get(matches.size() - 1);
            }
            String current = Texts.toStringSafe(lines.get(targetIndex));
            lines.set(targetIndex, current.replace(anchor, replacement));
        }

        private int parseRequestedIndex(Object rawIndex) {
            Integer parsed = emaki.jiuwu.craft.corelib.math.Numbers.tryParseInt(rawIndex, null);
            return parsed == null ? 0 : Math.max(0, parsed);
        }
    }

    private static final class DeleteLineProcessor implements LoreOperationProcessor {

        @Override
        public void process(List<String> lines, Context context) {
            if (lines == null || Texts.isBlank(context.anchor())) {
                return;
            }
            for (int index = lines.size() - 1; index >= 0; index--) {
                if (Texts.toStringSafe(lines.get(index)).contains(context.anchor())) {
                    lines.remove(index);
                }
            }
        }
    }

    private static final class RegexReplaceProcessor implements LoreOperationProcessor {

        @Override
        public void process(List<String> lines, Context context) {
            if (lines == null) {
                return;
            }
            String regex = Texts.toStringSafe(context.operation().get("regex_pattern"));
            String replacement = Texts.toStringSafe(context.operation().get("replacement"));
            if (Texts.isBlank(regex)) {
                return;
            }
            for (int index = 0; index < lines.size(); index++) {
                lines.set(index, OperationTemplateRenderer.replaceRegex(lines.get(index), regex, replacement, context.variables()));
            }
        }
    }
}
