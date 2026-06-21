package emaki.jiuwu.craft.item.service;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.loader.EmakiItemAliasLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.model.EmakiItemAlias;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.script.js.JavaScriptItemDefinitionRegistry;

public final class EmakiItemIdResolver {

    private final EmakiItemLoader itemLoader;
    private final EmakiItemAliasLoader aliasLoader;
    private final JavaScriptItemDefinitionRegistry javaScriptDefinitions;

    public EmakiItemIdResolver(EmakiItemLoader itemLoader, EmakiItemAliasLoader aliasLoader) {
        this(itemLoader, aliasLoader, null);
    }

    public EmakiItemIdResolver(EmakiItemLoader itemLoader, EmakiItemAliasLoader aliasLoader, JavaScriptItemDefinitionRegistry javaScriptDefinitions) {
        this.itemLoader = itemLoader;
        this.aliasLoader = aliasLoader;
        this.javaScriptDefinitions = javaScriptDefinitions;
    }

    public String resolveId(String id) {
        String normalized = Texts.normalizeId(id);
        if (Texts.isBlank(normalized)) {
            return "";
        }
        if (javaScriptDefinitions != null && javaScriptDefinitions.getOverride(normalized) != null) {
            return normalized;
        }
        if (itemLoader.get(normalized) != null) {
            return normalized;
        }
        if (javaScriptDefinitions != null && javaScriptDefinitions.get(normalized) != null) {
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
        if (Texts.isBlank(resolved)) {
            return null;
        }
        if (javaScriptDefinitions != null) {
            EmakiItemDefinition override = javaScriptDefinitions.getOverride(resolved);
            if (override != null) {
                return override;
            }
        }
        EmakiItemDefinition yaml = itemLoader.get(resolved);
        if (yaml != null) {
            return yaml;
        }
        return javaScriptDefinitions == null ? null : javaScriptDefinitions.get(resolved);
    }

    public EmakiItemAlias aliasFor(String id) {
        String normalized = Texts.normalizeId(id);
        return itemLoader.get(normalized) == null ? aliasLoader.get(normalized) : null;
    }

    public boolean hasAlias(String id) {
        return aliasFor(id) != null;
    }
}
