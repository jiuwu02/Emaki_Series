package emaki.jiuwu.craft.attribute.loader;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class DamageTypeRegistry extends DirectoryLoader<DamageTypeDefinition> {

    private static final List<String> BUNDLED_RESOURCES = List.of(
            "physical.yml",
            "projectile.yml",
            "spell.yml"
    );
    private final AttributeRegistry attributeRegistry;
    private final Map<String, DamageTypeDefinition> aliasIndex = new LinkedHashMap<>();
    private volatile String definitionSignature = "";

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
    protected DamageTypeDefinition parse(File file, YamlSection configuration) {
        return DamageTypeDefinition.fromMap(configuration, this::resolveAttributeId);
    }

    @Override
    protected void seedBundledResources(File directory) {
        for (String resourceName : BUNDLED_RESOURCES) {
            copyBundledResource("damage_types/" + resourceName, new File(directory, resourceName));
        }
    }

    @Override
    protected boolean validateSchema(File file, YamlSection configuration) {
        boolean valid = true;
        if (Texts.isBlank(configuration.getString("id"))) {
            issue(
                    "loader.schema_missing_id",
                    Map.of(
                            "type", typeName(),
                            "file", file.getName(),
                            "field", "id"
                    )
            );
            valid = false;
        }
        Object stages = configuration.get("stages");
        if (stages != null) {
            for (Object stage : ConfigNodes.asObjectList(stages)) {
                if (stage instanceof Map<?, ?> || stage instanceof YamlSection) {
                    continue;
                }
                issue(
                        "loader.schema_invalid_section",
                        Map.of(
                                "type", typeName(),
                                "file", file.getName(),
                                "field", "stages"
                        )
                );
                valid = false;
                break;
            }
        }
        Object recovery = configuration.get("recovery");
        if (recovery != null && !(recovery instanceof Map<?, ?> || recovery instanceof YamlSection)) {
            issue(
                    "loader.schema_invalid_section",
                    Map.of(
                            "type", typeName(),
                            "file", file.getName(),
                            "field", "recovery"
                    )
            );
            valid = false;
        }
        return valid;
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
        definitionSignature = SignatureUtil.stableSignature(items.values());
    }

    public DamageTypeDefinition resolve(String id) {
        synchronized (stateLock) {
            if (Texts.isBlank(id)) {
                return null;
            }
            return aliasIndex.get(normalizeId(id));
        }
    }

    public String definitionSignature() {
        return definitionSignature;
    }

    private String resolveAttributeId(String id) {
        if (Texts.isBlank(id)) {
            return "";
        }
        String normalized = normalizeId(id);
        if (attributeRegistry != null) {
            var definition = attributeRegistry.resolve(normalized);
            if (definition != null) {
                return definition.id();
            }
        }
        return normalized;
    }
}
