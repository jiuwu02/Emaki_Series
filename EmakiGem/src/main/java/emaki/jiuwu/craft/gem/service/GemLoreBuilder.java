package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.EmakiLoreSectionContribution;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;

final class GemLoreBuilder {

    private static final String NAMESPACE_ID = "gem";
    private static final String DEFAULT_SLOT_SEPARATOR = "<dark_gray>─────────────</dark_gray>";
    private static final String LORE_PREFIX = "lore.";

    private final EmakiGemPlugin plugin;

    GemLoreBuilder(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    Map<String, Object> buildItemPlaceholders(GemItemDefinition itemDefinition, GemState state) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("item_definition_id", itemDefinition.id());
        placeholders.put("opened_slots", countOpenedSlots(itemDefinition, state));
        placeholders.put("total_slots", itemDefinition.slots().size());
        placeholders.put("slot_icons", renderSlotIcons(itemDefinition, state));
        return placeholders;
    }

    List<String> buildOverviewLines(GemItemDefinition itemDefinition, GemState state, List<String> extraLines) {
        if (itemDefinition == null || state == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        Map<String, Object> placeholders = Map.of(
                "icons", renderSlotIcons(itemDefinition, state),
                "opened", countOpenedSlots(itemDefinition, state),
                "total", itemDefinition.slots().size()
        );
        lines.add(loreText("overview_title", placeholders,
                "<yellow>宝石插槽 [" + placeholders.get("icons") + "] "
                        + placeholders.get("opened") + "/" + placeholders.get("total") + "</yellow>"));
        List<String> configuredLines = extraLines == null ? List.of() : List.copyOf(extraLines);
        if (configuredLines.isEmpty()) {
            lines.add(DEFAULT_SLOT_SEPARATOR);
        } else {
            lines.addAll(configuredLines);
        }
        return lines;
    }

    List<String> buildSlotStatusLines(GemItemDefinition itemDefinition, GemState state) {
        if (itemDefinition == null || state == null || itemDefinition.slots().isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add(loreText("status_header", Map.of(), "<dark_gray>◆ 宝石槽</dark_gray>"));
        for (GemItemDefinition.SocketSlot slot : itemDefinition.slots()) {
            if (slot == null) {
                continue;
            }
            GemItemInstance instance = state.assignment(slot.index());
            if (!state.isOpened(slot.index())) {
                lines.add(loreText("slot_not_opened", Map.of("slot", slot.index(), "name", slot.displayName()),
                        "<gray>[" + slot.index() + "] <dark_gray>未开孔 - " + slot.displayName() + "</dark_gray>"));
                continue;
            }
            if (instance == null) {
                lines.add(loreText("slot_empty", Map.of("slot", slot.index(), "name", slot.displayName()),
                        "<gray>[" + slot.index() + "] <yellow>空槽</yellow> <dark_gray>(" + slot.displayName() + ")</dark_gray>"));
                continue;
            }
            GemDefinition definition = plugin.gemLoader().get(instance.gemId());
            String displayName = definition == null
                    ? instance.gemId()
                    : plugin.itemFactory().resolveGemDisplayName(definition, instance.level());
            lines.add(loreText("slot_inlaid", Map.of("slot", slot.index(), "gem", displayName, "level", instance.level()),
                    "<gray>[" + slot.index() + "]</gray> " + displayName + " <dark_gray>Lv." + instance.level() + "</dark_gray>"));
        }
        return lines;
    }

    List<String> extractSafeLoreLines(Object operations, Map<String, ?> placeholders, boolean filterOverviewLine) {
        String overviewMarker = filterOverviewLine ? loreText("overview_title", Map.of("icons", "", "opened", 0, "total", 0), "宝石插槽") : null;
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
                if (overviewMarker != null && isOverviewLine(line, overviewMarker)) {
                    continue;
                }
                lines.add(line);
            }
        }
        return lines;
    }

    void addSection(List<EmakiLoreSectionContribution> sections,
            String sectionId,
            int order,
            List<String> lines) {
        if (sections == null || Texts.isBlank(sectionId) || lines == null || lines.isEmpty()) {
            return;
        }
        sections.add(new EmakiLoreSectionContribution(sectionId, order, lines, NAMESPACE_ID));
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

    private String renderSlotIcons(GemItemDefinition itemDefinition, GemState state) {
        StringBuilder builder = new StringBuilder();
        if (itemDefinition == null || state == null) {
            return builder.toString();
        }
        for (GemItemDefinition.SocketSlot slot : itemDefinition.slots()) {
            if (slot == null) {
                continue;
            }
            builder.append(state.isOpened(slot.index()) ? '◆' : '◇');
        }
        return builder.toString();
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

    private String loreText(String key, Map<String, ?> placeholders, String fallback) {
        String value = plugin.messageService().message(LORE_PREFIX + key, placeholders);
        return Texts.isBlank(value) || (LORE_PREFIX + key).equals(value) ? fallback : value;
    }

    private boolean isOverviewLine(String line, String marker) {
        if (Texts.isBlank(line) || Texts.isBlank(marker)) {
            return false;
        }
        // Strip MiniMessage tags for comparison
        String stripped = line.replaceAll("<[^>]+>", "");
        String markerStripped = marker.replaceAll("<[^>]+>", "");
        if (Texts.isBlank(markerStripped)) {
            return false;
        }
        // Use first meaningful segment of the marker as identifier
        String[] segments = markerStripped.trim().split("\\s+");
        return segments.length > 0 && stripped.contains(segments[0]);
    }
}
