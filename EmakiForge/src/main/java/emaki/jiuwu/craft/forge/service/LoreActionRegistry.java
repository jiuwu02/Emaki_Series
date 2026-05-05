package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

final class LoreActionRegistry {

    private final TextTemplateRenderer templateRenderer;
    private final Map<String, LoreActionProcessor> processors = new LinkedHashMap<>();

    LoreActionRegistry(TextTemplateRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
        register("append", new AppendLoreProcessor());
        register("prepend", new PrependLoreProcessor());
        register("insert_below", new InsertLoreProcessor(true));
        register("search_insert_below", new InsertLoreProcessor(true));
        register("search_insert", new InsertLoreProcessor(true));
        register("insert_above", new InsertLoreProcessor(false));
        register("search_insert_above", new InsertLoreProcessor(false));
        register("replace_line", new ReplaceLineLoreProcessor());
        register("delete_line", new DeleteLineLoreProcessor());
        register("regex_replace", new RegexReplaceLoreProcessor());
    }

    LoreActionProcessor getProcessor(String action) {
        return processors.get(Texts.lower(action));
    }

    void apply(List<String> lines, Object operations, Map<String, Object> variables) {
        if (lines == null) {
            return;
        }
        for (Map<String, Object> operation : templateRenderer.normalizeOperations(operations)) {
            String action = Texts.lower(operation.get("action"));
            List<String> content = templateRenderer.renderContent(operation, variables);
            String anchor = templateRenderer.renderTemplate(templateRenderer.resolveSearchPattern(operation), variables);
            LoreActionProcessor processor = getProcessor(action);
            if (processor != null) {
                processor.process(lines, new LoreActionProcessor.Context(operation, content, anchor, variables));
            }
        }
    }

    private void register(String action, LoreActionProcessor processor) {
        processors.put(action, processor);
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

    private static void replaceLine(List<String> lines, String anchor, String replacement) {
        if (lines == null) {
            return;
        }
        for (int index = 0; index < lines.size(); index++) {
            if (!Texts.toStringSafe(lines.get(index)).contains(anchor)) {
                continue;
            }
            lines.set(index, Texts.toStringSafe(replacement));
            return;
        }
    }

    private static void deleteLine(List<String> lines, String anchor) {
        if (lines == null) {
            return;
        }
        for (int index = lines.size() - 1; index >= 0; index--) {
            if (Texts.toStringSafe(lines.get(index)).contains(anchor)) {
                lines.remove(index);
            }
        }
    }

    private static void replaceRegexInLore(List<String> lines,
            String regex,
            String replacement,
            Map<String, Object> variables) {
        if (lines == null || Texts.isBlank(regex)) {
            return;
        }
        for (int index = 0; index < lines.size(); index++) {
            lines.set(index, TextTemplateRenderer.replaceRegex(lines.get(index), regex, replacement, variables));
        }
    }

    private static final class AppendLoreProcessor implements LoreActionProcessor {

        @Override
        public void process(List<String> lines, LoreActionProcessor.Context context) {
            lines.addAll(context.content());
        }
    }

    private static final class PrependLoreProcessor implements LoreActionProcessor {

        @Override
        public void process(List<String> lines, LoreActionProcessor.Context context) {
            for (String line : context.content()) {
                lines.add(0, line);
            }
        }
    }

    private static final class InsertLoreProcessor implements LoreActionProcessor {

        private final boolean below;

        private InsertLoreProcessor(boolean below) {
            this.below = below;
        }

        @Override
        public void process(List<String> lines, LoreActionProcessor.Context context) {
            for (String line : context.content()) {
                lines.add(findInsertIndex(lines, context.anchor(), below), line);
            }
        }
    }

    private static final class ReplaceLineLoreProcessor implements LoreActionProcessor {

        @Override
        public void process(List<String> lines, LoreActionProcessor.Context context) {
            replaceLine(lines, context.anchor(), context.content().isEmpty() ? "" : context.content().get(0));
        }
    }

    private static final class DeleteLineLoreProcessor implements LoreActionProcessor {

        @Override
        public void process(List<String> lines, LoreActionProcessor.Context context) {
            deleteLine(lines, context.anchor());
        }
    }

    private static final class RegexReplaceLoreProcessor implements LoreActionProcessor {

        @Override
        public void process(List<String> lines, LoreActionProcessor.Context context) {
            replaceRegexInLore(
                    lines,
                    Texts.toStringSafe(context.operation().get("regex_pattern")),
                    Texts.toStringSafe(context.operation().get("replacement")),
                    context.variables()
            );
        }
    }
}
