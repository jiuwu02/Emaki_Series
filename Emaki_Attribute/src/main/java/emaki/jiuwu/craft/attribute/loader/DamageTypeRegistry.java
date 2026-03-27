package emaki.jiuwu.craft.attribute.loader;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

public final class DamageTypeRegistry extends DirectoryLoader<DamageTypeDefinition> {

    private final AttributeRegistry attributeRegistry;
    private final Map<String, DamageTypeDefinition> aliasIndex = new LinkedHashMap<>();

    public DamageTypeRegistry(EmakiAttributePlugin plugin, AttributeRegistry attributeRegistry) {
        super(plugin);
        this.attributeRegistry = attributeRegistry;
    }

    @Override
    protected String directoryName() {
        return "damage_types";
    }

    @Override
    protected String typeName() {
        return plugin.messageService() == null ? "伤害类型" : plugin.messageService().message("label.damage_type");
    }

    @Override
    protected DamageTypeDefinition parse(File file, YamlConfiguration configuration) {
        return DamageTypeDefinition.fromMap(configuration, this::resolveAttributeId);
    }

    @Override
    protected String idOf(DamageTypeDefinition value) {
        return value.id();
    }

    @Override
    protected void afterLoad() {
        aliasIndex.clear();
        for (DamageTypeDefinition definition : items.values()) {
            aliasIndex.put(definition.id(), definition);
            for (String alias : definition.aliases()) {
                aliasIndex.putIfAbsent(normalizeId(alias), definition);
            }
        }
    }

    public DamageTypeDefinition resolve(String id) {
        if (Texts.isBlank(id)) {
            return null;
        }
        return aliasIndex.get(normalizeId(id));
    }

    private String resolveAttributeId(String id) {
        if (Texts.isBlank(id)) {
            return "";
        }
        if (attributeRegistry != null) {
            var definition = attributeRegistry.resolve(id);
            if (definition != null) {
                return definition.id();
            }
        }
        return normalizeId(id);
    }
}
