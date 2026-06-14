package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.model.EquippedSetState;
import emaki.jiuwu.craft.item.model.ItemSetDefinition;
import emaki.jiuwu.craft.item.model.ItemSetLoreConfig;
import emaki.jiuwu.craft.item.model.ItemSetPieceDefinition;
import emaki.jiuwu.craft.item.model.ItemSetThreshold;

public final class ItemSetLoreRenderer {

    public List<String> render(EquippedSetState state) {
        if (state == null || state.definition() == null) {
            return List.of();
        }
        ItemSetDefinition definition = state.definition();
        ItemSetLoreConfig config = definition.loreConfig();
        Map<String, String> base = Map.of(
                "set_id", definition.id(),
                "set_name", definition.displayLabel(),
                "active", Integer.toString(state.activeCount()),
                "total", Integer.toString(definition.totalPieces())
        );
        List<String> lines = new ArrayList<>();
        addIfNotNull(lines, replace(config.header(), base));
        for (ItemSetPieceDefinition piece : definition.pieces().values()) {
            boolean equipped = state.equippedPieces().contains(piece.pieceId());
            Map<String, String> placeholders = new java.util.LinkedHashMap<>(base);
            placeholders.put("piece", piece.displayLabel());
            placeholders.put("piece_id", piece.pieceId());
            placeholders.put("slot", piece.slot());
            addIfNotNull(lines, replace(equipped ? config.equippedFormat() : config.missingFormat(), placeholders));
        }
        if (Texts.isNotBlank(config.separator())) {
            lines.add(config.separator());
        }
        for (ItemSetThreshold threshold : definition.thresholds()) {
            boolean active = threshold.active(state.activeCount());
            for (String loreLine : threshold.lore()) {
                Map<String, String> placeholders = new java.util.LinkedHashMap<>(base);
                placeholders.put("threshold", Integer.toString(threshold.requiredPieces()));
                placeholders.put("line", loreLine);
                addIfNotNull(lines, replace(active ? config.activeThresholdFormat() : config.inactiveThresholdFormat(), placeholders));
            }
        }
        return lines;
    }

    private void addIfNotNull(List<String> lines, String line) {
        if (line != null) {
            lines.add(line);
        }
    }

    private String replace(String template, Map<String, String> placeholders) {
        if (template == null) {
            return null;
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("%" + entry.getKey() + "%", value);
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }
}
