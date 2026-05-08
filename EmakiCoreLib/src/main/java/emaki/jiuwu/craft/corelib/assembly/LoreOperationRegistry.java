package emaki.jiuwu.craft.corelib.assembly;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Registry and executor for lore operations.
 * <p>
 * Supported operations:
 * <ul>
 *   <li>{@code append} — add lines at the end</li>
 *   <li>{@code prepend} — add lines at the beginning</li>
 *   <li>{@code insert_below} / {@code search_insert_below} / {@code search_insert} — insert below matching line</li>
 *   <li>{@code insert_above} / {@code search_insert_above} — insert above matching line</li>
 *   <li>{@code replace_line} — replace the first matching line</li>
 *   <li>{@code delete_line} — delete all matching lines</li>
 *   <li>{@code regex_replace} — regex replace within all lines</li>
 * </ul>
 */
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
        if (lines == null) {
            return;
        }
        for (Map<String, Object> operation : templateRenderer.normalizeOperations(operations)) {
            String action = Texts.lower(operation.get("action"));
            List<String> content = templateRenderer.renderContent(operation, variables);
            String anchor = templateRenderer.renderTemplate(templateRenderer.resolveSearchPattern(operation), variables);
            LoreOperationProcessor processor = getProcessor(action);
            if (processor != null) {
                processor.process(lines, new LoreOperationProcessor.Context(operation, content, anchor, variables));
            }
        }
    }

    public OperationTemplateRenderer templateRenderer() {
        return templateRenderer;
    }

    // --- Built-in processors ---

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
            for (String line : context.content()) {
                lines.add(0, line);
            }
        }
    }

    private static final class InsertProcessor implements LoreOperationProcessor {

        private final boolean below;

        private InsertProcessor(boolean below) {
            this.below = below;
        }

        @Override
        public void process(List<String> lines, Context context) {
            for (String line : context.content()) {
                lines.add(findInsertIndex(lines, context.anchor(), below), line);
            }
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
