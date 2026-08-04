package emaki.jiuwu.craft.item.model;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record ItemSetPieceDefinition(String pieceId, String itemId, String slot, String displayName) {

    public ItemSetPieceDefinition {
        pieceId = Texts.normalizeId(pieceId);
        itemId = Texts.normalizeId(itemId);
        slot = Texts.normalizeId(slot);
        displayName = Texts.toStringSafe(displayName);
    }

    public String displayLabel() {
        return Texts.isNotBlank(displayName) ? displayName : pieceId;
    }
}
