package emaki.jiuwu.craft.item.model;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record ItemSetDefinition(String id,
        String displayName,
        Map<String, ItemSetPieceDefinition> pieces,
        List<ItemSetThreshold> thresholds,
        ItemSetLoreConfig loreConfig) {

    public ItemSetDefinition {
        id = Texts.normalizeId(id);
        displayName = Texts.toStringSafe(displayName);
        pieces = pieces == null ? Map.of() : copyPieces(pieces);
        thresholds = thresholds == null ? List.of() : thresholds.stream()
                .filter(threshold -> threshold != null)
                .sorted(Comparator.comparingInt(ItemSetThreshold::requiredPieces))
                .toList();
        loreConfig = loreConfig == null ? ItemSetLoreConfig.defaults() : loreConfig;
    }

    public int totalPieces() {
        return pieces.size();
    }

    public String displayLabel() {
        return Texts.isNotBlank(displayName) ? displayName : id;
    }

    public List<ItemSetThreshold> activeThresholds(int equippedPieces) {
        return thresholds.stream().filter(threshold -> threshold.active(equippedPieces)).toList();
    }

    private static Map<String, ItemSetPieceDefinition> copyPieces(Map<String, ItemSetPieceDefinition> source) {
        Map<String, ItemSetPieceDefinition> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (value != null && Texts.isNotBlank(value.pieceId())) {
                copy.put(value.pieceId(), value);
            }
        });
        return Map.copyOf(copy);
    }
}
