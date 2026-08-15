package emaki.jiuwu.craft.accessory.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record AccessorySetDefinition(String setId,
        String displayName,
        Map<String, Piece> pieces,
        Map<Integer, Threshold> thresholds) {

    public AccessorySetDefinition {
        setId = Texts.normalizeId(setId);
        displayName = Texts.toStringSafe(displayName);
        pieces = pieces == null ? Map.of() : Map.copyOf(pieces);
        thresholds = thresholds == null ? Map.of() : Map.copyOf(thresholds);
    }

    public record Piece(String pieceId, String itemId, String slot, String display) {

        public Piece {
            pieceId = Texts.normalizeId(pieceId);
            itemId = Texts.normalizeId(itemId);
            String normalizedSlot = Texts.normalizeId(slot);
            slot = Texts.isBlank(normalizedSlot) ? "all" : normalizedSlot;
            display = Texts.toStringSafe(display);
        }
    }

    public record Threshold(int requiredPieces, Map<String, Double> attributes, List<String> skills) {

        public Threshold {
            requiredPieces = Math.max(1, requiredPieces);
            Map<String, Double> normalizedAttributes = new LinkedHashMap<>();
            if (attributes != null) {
                attributes.forEach((key, value) -> {
                    String normalized = Texts.normalizeId(key);
                    if (Texts.isNotBlank(normalized) && value != null && Double.isFinite(value)) {
                        normalizedAttributes.merge(normalized, value, Double::sum);
                    }
                });
            }
            attributes = Map.copyOf(normalizedAttributes);
            List<String> normalizedSkills = new ArrayList<>();
            if (skills != null) {
                for (String skill : skills) {
                    String normalized = Texts.normalizeId(skill);
                    if (Texts.isNotBlank(normalized) && !normalizedSkills.contains(normalized)) {
                        normalizedSkills.add(normalized);
                    }
                }
            }
            skills = List.copyOf(normalizedSkills);
        }
    }

    public List<Threshold> activeThresholds(int equippedPieces) {
        List<Threshold> active = new ArrayList<>();
        thresholds.entrySet().stream()
                .filter(entry -> entry.getKey() <= equippedPieces)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> active.add(entry.getValue()));
        return List.copyOf(active);
    }

    public int totalPieces() {
        return pieces.size();
    }
}
