package emaki.jiuwu.craft.item.service;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.loader.EmakiItemAliasLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.model.EmakiItemAlias;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

public final class EmakiItemIdResolver {

    private final EmakiItemLoader itemLoader;
    private final EmakiItemAliasLoader aliasLoader;

    public EmakiItemIdResolver(EmakiItemLoader itemLoader, EmakiItemAliasLoader aliasLoader) {
        this.itemLoader = itemLoader;
        this.aliasLoader = aliasLoader;
    }

    public String resolveId(String id) {
        String normalized = Texts.normalizeId(id);
        if (Texts.isBlank(normalized)) {
            return "";
        }
        if (itemLoader.get(normalized) != null) {
            return normalized;
        }
        EmakiItemAlias alias = aliasLoader.get(normalized);
        if (alias == null || Texts.isBlank(alias.targetId())) {
            return normalized;
        }
        return alias.targetId();
    }

    public EmakiItemDefinition resolveDefinition(String id) {
        String resolved = resolveId(id);
        return Texts.isBlank(resolved) ? null : itemLoader.get(resolved);
    }

    public EmakiItemAlias aliasFor(String id) {
        String normalized = Texts.normalizeId(id);
        return itemLoader.get(normalized) == null ? aliasLoader.get(normalized) : null;
    }

    public boolean hasAlias(String id) {
        return aliasFor(id) != null;
    }
}
