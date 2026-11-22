package emaki.jiuwu.craft.item.service;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

public final class EmakiItemIdResolver {

    private final EmakiItemLoader itemLoader;

    public EmakiItemIdResolver(EmakiItemLoader itemLoader) {
        this.itemLoader = itemLoader;
    }

    public String resolveId(String id) {
        String normalized = Texts.normalizeId(id);
        if (Texts.isBlank(normalized)) {
            return "";
        }
        return normalized;
    }

    public EmakiItemDefinition resolveDefinition(String id) {
        String resolved = resolveId(id);
        if (Texts.isBlank(resolved)) {
            return null;
        }
        return itemLoader.get(resolved);
    }
}
