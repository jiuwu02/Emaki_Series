package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;

final class GemLoreBuilder {

    GemLoreBuilder() {
    }

    Map<String, Object> buildItemPlaceholders(GemItemDefinition itemDefinition, GemState state) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("item_definition_id", itemDefinition.id());
        placeholders.put("opened_slots", countOpenedSlots(itemDefinition, state));
        placeholders.put("inlaid_slots", countInlaidSlots(state));
        placeholders.put("total_slots", itemDefinition.slots().size());
        return placeholders;
    }

    List<String> extractSafeLoreLines(Object operations, Map<String, ?> placeholders) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> operation : normalizeOperations(replaceTemplates(operations, placeholders))) {
            String action = Texts.lower(operation.get("action"));
            if (!isSafeLoreAction(action)) {
                continue;
            }
            for (String line : resolveContent(operation)) {
                if (Texts.isBlank(line)) {
                    continue;
                }
                lines.add(line);
            }
        }
        return lines;
    }

    private int countOpenedSlots(GemItemDefinition itemDefinition, GemState state) {
        int opened = 0;
        if (itemDefinition == null || state == null) {
            return opened;
        }
        for (GemItemDefinition.SocketSlot slot : itemDefinition.slots()) {
            if (slot != null && state.isOpened(slot.index())) {
                opened++;
            }
        }
        return opened;
    }

    private int countInlaidSlots(GemState state) {
        int inlaid = 0;
        if (state == null) {
            return inlaid;
        }
        for (GemItemInstance instance : state.socketAssignments().values()) {
            if (instance != null) {
                inlaid++;
            }
        }
        return inlaid;
    }

    private Object replaceTemplates(Object raw, Map<String, ?> placeholders) {
        if (raw == null) {
            return List.of();
        }
        Object plain = ConfigNodes.toPlainData(raw);
        if (plain instanceof String text) {
            return Texts.formatTemplate(text, placeholders);
        }
        if (plain instanceof List<?> list) {
            List<Object> replaced = new ArrayList<>();
            for (Object value : list) {
                replaced.add(replaceTemplates(value, placeholders));
            }
            return replaced;
        }
        if (plain instanceof Map<?, ?> map) {
            Map<String, Object> replaced = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                replaced.put(String.valueOf(entry.getKey()), replaceTemplates(entry.getValue(), placeholders));
            }
            return replaced;
        }
        return plain;
    }

    private boolean isSafeLoreAction(String action) {
        return switch (Texts.lower(action)) {
            case "append", "prepend", "insert_below", "insert_above", "search_insert_below", "search_insert_above" -> true;
            default -> false;
        };
    }

    private List<Map<String, Object>> normalizeOperations(Object raw) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object operation : ConfigNodes.asObjectList(raw)) {
            Object plain = ConfigNodes.toPlainData(operation);
            if (!(plain instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> normalizedOperation = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                normalizedOperation.put(String.valueOf(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
            }
            normalized.add(normalizedOperation);
        }
        return normalized;
    }

    private List<String> resolveContent(Map<String, Object> operation) {
        Object raw = operation == null ? null : operation.get("content");
        return ExpressionEngine.evaluateStringLinesConfig(raw);
    }

}
