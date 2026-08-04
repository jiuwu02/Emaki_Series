package emaki.jiuwu.craft.item.model;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record EmakiItemAlias(String oldId,
        String targetId,
        boolean migratePdc,
        boolean rewriteDisplay,
        String expiresAfter) {

    public EmakiItemAlias {
        oldId = Texts.normalizeId(oldId);
        targetId = Texts.normalizeId(targetId);
        expiresAfter = Texts.toStringSafe(expiresAfter);
    }

    public boolean valid() {
        return Texts.isNotBlank(oldId) && Texts.isNotBlank(targetId) && !oldId.equals(targetId);
    }
}
