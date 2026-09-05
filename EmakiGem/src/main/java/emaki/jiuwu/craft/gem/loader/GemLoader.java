package emaki.jiuwu.craft.gem.loader;

import java.io.File;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;

public final class GemLoader extends YamlDirectoryLoader<GemDefinition> {

    public GemLoader(EmakiGemPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "gems";
    }

    @Override
    protected String typeName() {
        return localized("loader.type.gem");
    }

    @Override
    protected GemDefinition parse(File file, YamlSection configuration) {
        if (configuration == null) {
            issue("loader.invalid_config", Map.of("type", typeName(), "file", file == null ? "-" : file.getName()));
            return null;
        }
        String id = Texts.lower(configuration.getString("id"));
        if (Texts.isBlank(id)) {
            onBlankId(file);
            return null;
        }
        boolean legacyItemSources = !configuration.contains("base_item_source")
                && configuration.contains("item_sources");
        Object rawItemSource = legacyItemSources
                ? configuration.get("item_sources")
                : configuration.get("base_item_source");
        ItemSourceRef itemSource;
        if (legacyItemSources) {
            List<ItemSourceRef> legacySources = ItemRequirement.parseSources(rawItemSource);
            itemSource = legacySources.size() == 1 ? legacySources.getFirst() : null;
        } else {
            itemSource = ItemSourceUtil.parse(rawItemSource);
        }
        if (legacyItemSources) {
            issue("loader.gem_legacy_item_sources", Map.of(
                    "file", file.getName(),
                    "id", id
            ));
        }
        if (itemSource == null) {
            issue("loader.gem_missing_item_source", Map.of(
                    "file", file.getName(),
                    "id", id,
                    "item_sources", String.valueOf(rawItemSource)
            ));
            return null;
        }
        GemDefinition definition = GemDefinition.fromConfig(configuration);
        if (definition == null) {
            issue("loader.invalid_config", Map.of("type", typeName(), "file", file.getName()));
            return null;
        }
        for (String diagnostic : definition.reroll().diagnostics()) {
            issue("loader.invalid_config", Map.of(
                    "type", typeName(),
                    "file", file.getName(),
                    "id", definition.id(),
                    "error", diagnostic
            ));
        }
        if (definition.dependencies().contains(definition.id()) || definition.conflicts().contains(definition.id())) {
            issue("loader.gem_relationship_self_reference", Map.of(
                    "file", file.getName(),
                    "id", definition.id()
            ));
            return null;
        }
        for (String dependency : definition.dependencies()) {
            if (definition.conflicts().contains(dependency)) {
                issue("loader.gem_relationship_conflict", Map.of(
                        "file", file.getName(),
                        "id", definition.id(),
                        "related_gem", dependency
                ));
                return null;
            }
        }
        return definition;
    }

    @Override
    protected String idOf(GemDefinition value) {
        return value.id();
    }
}
