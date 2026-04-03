package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.lore.ActionNameParser;
import emaki.jiuwu.craft.corelib.assembly.lore.SearchInsertConfig;
import emaki.jiuwu.craft.corelib.assembly.lore.SearchInsertValidationException;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ItemPresentationCompiler {

    public PresentationCompileResult compile(Object nameOperations,
            Object loreOperations,
            int startSequence,
            String sourceId) {
        PresentationCompileResult nameResult = compileNameOperations(nameOperations, startSequence, sourceId);
        PresentationCompileResult loreResult = compileLoreOperations(loreOperations, nameResult.nextSequence(), sourceId);
        List<EmakiPresentationEntry> entries = new ArrayList<>(nameResult.entries());
        entries.addAll(loreResult.entries());
        List<PresentationCompileIssue> issues = new ArrayList<>(nameResult.issues());
        issues.addAll(loreResult.issues());
        return new PresentationCompileResult(entries, loreResult.nextSequence(), issues);
    }

    public PresentationCompileResult compileNameOperations(Object operations, int startSequence, String sourceId) {
        List<EmakiPresentationEntry> entries = new ArrayList<>();
        int sequence = Math.max(0, startSequence);
        for (Object raw : ConfigNodes.asObjectList(operations)) {
            Map<String, Object> operation = normalizeOperation(raw);
            if (operation.isEmpty()) {
                continue;
            }
            String action = resolveAction(operation);
            switch (action) {
                case "append_suffix", "name_append", "name_append_suffix" ->
                    entries.add(new EmakiPresentationEntry(
                            "name_append",
                            "",
                            resolveString(operation, "value", "template", "replacement"),
                            sequence++,
                            sourceId
                    ));
                case "prepend_prefix", "name_prepend", "name_prepend_prefix" ->
                    entries.add(new EmakiPresentationEntry(
                            "name_prepend",
                            "",
                            resolveString(operation, "value", "template", "replacement"),
                            sequence++,
                            sourceId
                    ));
                case "replace", "name_replace" ->
                    entries.add(new EmakiPresentationEntry(
                            "name_replace",
                            "",
                            resolveString(operation, "value", "template", "replacement"),
                            sequence++,
                            sourceId
                    ));
                case "regex_replace", "name_regex_replace" ->
                    entries.add(new EmakiPresentationEntry(
                            "name_regex_replace",
                            resolveString(operation, "regex_pattern", "pattern", "target_pattern", "anchor"),
                            resolveString(operation, "replacement", "value", "template"),
                            sequence++,
                            sourceId
                    ));
                default -> {
                }
            }
        }
        return new PresentationCompileResult(entries, sequence, List.of());
    }

    public PresentationCompileResult compileLoreOperations(Object operations, int startSequence, String sourceId) {
        List<EmakiPresentationEntry> entries = new ArrayList<>();
        List<PresentationCompileIssue> issues = new ArrayList<>();
        int sequence = Math.max(0, startSequence);
        for (Object raw : ConfigNodes.asObjectList(operations)) {
            Map<String, Object> operation = normalizeOperation(raw);
            if (operation.isEmpty()) {
                continue;
            }
            String action = resolveAction(operation);
            List<String> content = resolveContent(operation);
            String targetPattern = resolveString(operation, "target_pattern", "pattern", "anchor");
            String regexPattern = resolveString(operation, "regex_pattern", "pattern", "target_pattern", "anchor");
            String replacement = resolveString(operation, "replacement", "value", "template");

            if (ActionNameParser.isSearchInsertAction(action)) {
                try {
                    SearchInsertConfig config = SearchInsertConfig.fromAction(
                            action,
                            targetPattern,
                            resolveBoolean(operation, "ignore_case", false),
                            content,
                            resolveBoolean(operation, "inherit_style", true),
                            resolveString(operation, "on_not_found")
                    );
                    entries.add(new EmakiPresentationEntry("lore_search_insert", config.toJson(), "", sequence++, sourceId));
                } catch (SearchInsertValidationException exception) {
                    issues.add(new PresentationCompileIssue(
                            sourceId,
                            action,
                            targetPattern,
                            exception.reason(),
                            exception.getMessage()
                    ));
                }
                continue;
            }

            switch (action) {
                case "insert_below", "lore_insert_below" ->
                    sequence = addLoreContentEntries(entries, "lore_insert_below", targetPattern, content, sequence, sourceId);
                case "insert_above", "lore_insert_above" ->
                    sequence = addLoreContentEntries(entries, "lore_insert_above", targetPattern, content, sequence, sourceId);
                case "append", "append_line", "append_lines", "lore_append" ->
                    sequence = addLoreContentEntries(entries, "lore_append", "", content, sequence, sourceId);
                case "prepend", "prepend_line", "prepend_lines", "append_first_line", "append_first_lines", "insert_first",
                        "lore_prepend" ->
                    sequence = addLoreContentEntries(entries, "lore_prepend", "", content, sequence, sourceId);
                case "replace_line", "lore_replace_line" ->
                    sequence = addLoreReplaceEntry(entries, targetPattern, content, sequence, sourceId);
                case "delete_line", "lore_delete_line" ->
                    entries.add(new EmakiPresentationEntry("lore_delete_line", targetPattern, "", sequence++, sourceId));
                case "regex_replace", "lore_regex_replace" ->
                    entries.add(new EmakiPresentationEntry("lore_regex_replace", regexPattern, replacement, sequence++, sourceId));
                default -> {
                }
            }
        }
        return new PresentationCompileResult(entries, sequence, issues);
    }

    private int addLoreContentEntries(List<EmakiPresentationEntry> entries,
            String entryType,
            String anchor,
            List<String> content,
            int sequence,
            String sourceId) {
        for (String line : content) {
            String statId = firstPlaceholder(line);
            if (Texts.isNotBlank(statId)) {
                entries.add(new EmakiPresentationEntry("stat_line", anchor, line, sequence++, statId));
                continue;
            }
            entries.add(new EmakiPresentationEntry(entryType, anchor, line, sequence++, sourceId));
        }
        return sequence;
    }

    private int addLoreReplaceEntry(List<EmakiPresentationEntry> entries,
            String targetPattern,
            List<String> content,
            int sequence,
            String sourceId) {
        String line = content.isEmpty() ? "" : content.get(0);
        String statId = firstPlaceholder(line);
        if (Texts.isNotBlank(statId)) {
            entries.add(new EmakiPresentationEntry("stat_line", targetPattern, line, sequence++, statId));
            return sequence;
        }
        entries.add(new EmakiPresentationEntry("lore_replace_line", targetPattern, line, sequence++, sourceId));
        return sequence;
    }

    private Map<String, Object> normalizeOperation(Object raw) {
        Object plain = ConfigNodes.toPlainData(raw);
        if (!(plain instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(String.valueOf(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
        }
        return normalized;
    }

    private String resolveAction(Map<String, Object> operation) {
        String action = Texts.lower(resolveString(operation, "action", "type", "operation"));
        if (Texts.isBlank(action) && operation.containsKey("name")) {
            action = Texts.lower(operation.get("name"));
        }
        return action;
    }

    private List<String> resolveContent(Map<String, Object> operation) {
        Object raw = firstPresent(operation, "content", "lines");
        return List.copyOf(Texts.asStringList(raw));
    }

    private boolean resolveBoolean(Map<String, Object> operation, String key, boolean defaultValue) {
        if (!operation.containsKey(key)) {
            return defaultValue;
        }
        Object value = operation.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return Boolean.parseBoolean(Texts.toStringSafe(value));
    }

    private String resolveString(Map<String, Object> operation, String... keys) {
        Object value = firstPresent(operation, keys);
        return Texts.toStringSafe(value);
    }

    private Object firstPresent(Map<String, Object> operation, String... keys) {
        for (String key : keys) {
            if (operation.containsKey(key)) {
                return operation.get(key);
            }
        }
        return null;
    }

    private String firstPlaceholder(String template) {
        if (Texts.isBlank(template)) {
            return "";
        }
        int open = template.indexOf('{');
        int close = template.indexOf('}', open + 1);
        if (open < 0 || close <= open + 1) {
            return "";
        }
        return Texts.lower(template.substring(open + 1, close));
    }
}
