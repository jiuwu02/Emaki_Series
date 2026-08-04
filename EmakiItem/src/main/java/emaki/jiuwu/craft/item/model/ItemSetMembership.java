package emaki.jiuwu.craft.item.model;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record ItemSetMembership(String setId, String pieceId) {

    public ItemSetMembership {
        setId = Texts.normalizeId(setId);
        pieceId = Texts.normalizeId(pieceId);
    }

    public static ItemSetMembership empty() {
        return new ItemSetMembership("", "");
    }

    public boolean configured() {
        return Texts.isNotBlank(setId);
    }

    public String effectivePieceId(String fallbackItemId) {
        return Texts.isNotBlank(pieceId) ? pieceId : Texts.normalizeId(fallbackItemId);
    }
}
