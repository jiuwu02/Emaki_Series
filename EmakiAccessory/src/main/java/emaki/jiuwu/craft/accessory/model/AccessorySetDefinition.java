package emaki.jiuwu.craft.accessory.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * One accessory set: pieces plus piece-count thresholds that grant attributes and skills.
 *
 * <p>Deliberately independent from EmakiItem's equipment sets. EmakiItem scans raw Bukkit inventory
 * indices, so it can never see an accessory slot, and its {@code equip_slot} parser rejects custom slot
 * ids. Letting accessories participate in EmakiItem sets would also make EmakiItem query this module,
 * creating a two-way dependency. The configuration shape is copied from {@code sets/example_set.yml}
 * on purpose so server owners do not have to learn a second syntax.
 *
 * @param setId       normalized set id
 * @param displayName MiniMessage display name; may be empty
 * @param pieces      piece id to piece definition, in declaration order
 * @param thresholds  required piece count to threshold, sorted ascending
 */
public record AccessorySetDefinition(String setId,
        String displayName,
        Map<String, Piece> pieces,
        Map<Integer, Threshold> thresholds) {

    /** Canonical constructor; normalizes the id and defends both maps. */
    public AccessorySetDefinition {
        setId = Texts.normalizeId(setId);
        displayName = Texts.toStringSafe(displayName);
        pieces = pieces == null ? Map.of() : Map.copyOf(pieces);
        thresholds = thresholds == null ? Map.of() : Map.copyOf(thresholds);
    }

    /**
     * One set piece.
     *
     * @param pieceId normalized piece id
     * @param itemId  the EmakiItem definition id this piece requires
     * @param slot    the accessory part or slot instance id this piece must sit in, or {@code all}
     * @param display display label used in lore; may be empty
     */
    public record Piece(String pieceId, String itemId, String slot, String display) {

        /** Canonical constructor; normalizes ids and defaults a blank slot to {@code all}. */
        public Piece {
            pieceId = Texts.normalizeId(pieceId);
            itemId = Texts.normalizeId(itemId);
            String normalizedSlot = Texts.normalizeId(slot);
            slot = Texts.isBlank(normalizedSlot) ? "all" : normalizedSlot;
            display = Texts.toStringSafe(display);
        }
    }

    /**
     * One piece-count threshold.
     *
     * @param requiredPieces how many pieces must be equipped
     * @param attributes     attribute id to bonus value
     * @param skills         skill ids unlocked at this threshold
     */
    public record Threshold(int requiredPieces, Map<String, Double> attributes, List<String> skills) {

        /** Canonical constructor; normalizes keys and defends both collections. */
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

    /**
     * Returns the thresholds satisfied by an equipped piece count, in ascending order.
     *
     * <p>Thresholds are cumulative: a four-piece set with 2- and 4-piece thresholds grants both once
     * four pieces are worn, matching EmakiItem's behaviour.
     *
     * @param equippedPieces how many pieces of this set are equipped
     * @return the active thresholds
     */
    public List<Threshold> activeThresholds(int equippedPieces) {
        List<Threshold> active = new ArrayList<>();
        thresholds.entrySet().stream()
                .filter(entry -> entry.getKey() <= equippedPieces)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> active.add(entry.getValue()));
        return List.copyOf(active);
    }

    /** {@return the total number of pieces declared by this set} */
    public int totalPieces() {
        return pieces.size();
    }
}
