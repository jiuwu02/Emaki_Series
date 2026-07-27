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
    private final Map<String, DamageTypeDefinition> runtimeDefinitions = new LinkedHashMap<>();
    private final Map<String, String> runtimeSources = new LinkedHashMap<>();
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
        rebuildIndexes();
    }

    public boolean registerRuntime(DamageTypeDefinition definition, String source) {
        synchronized (stateLock) {
            if (definition == null || Texts.isBlank(definition.id())) {
                return false;
            }
            String id = normalizeId(definition.id());
            runtimeDefinitions.put(id, definition);
            runtimeSources.put(id, Texts.toStringSafe(source));
            rebuildIndexes();
            return true;
        }
    }

    public void clearRuntimeBySource(String source) {
        synchronized (stateLock) {
            String safeSource = Texts.toStringSafe(source);
            if (safeSource.isBlank()) {
                runtimeDefinitions.clear();
                runtimeSources.clear();
                rebuildIndexes();
                return;
            }
            for (String id : java.util.List.copyOf(runtimeSources.keySet())) {
                if (safeSource.equals(runtimeSources.get(id))) {
                    runtimeSources.remove(id);
                    runtimeDefinitions.remove(id);
                }
            }
            rebuildIndexes();
        }
    }

    public void clearRuntime() {
        synchronized (stateLock) {
            runtimeDefinitions.clear();
            runtimeSources.clear();
            rebuildIndexes();
        }
    }

    @Override
    public Map<String, DamageTypeDefinition> all() {
        synchronized (stateLock) {
            Map<String, DamageTypeDefinition> merged = new LinkedHashMap<>(items);
            merged.putAll(runtimeDefinitions);
            return Map.copyOf(merged);
        }
    }

    @Override
    public DamageTypeDefinition get(String id) {
        synchronized (stateLock) {
            if (Texts.isBlank(id)) {
                return null;
            }
            String normalized = normalizeId(id);
            DamageTypeDefinition runtime = runtimeDefinitions.get(normalized);
            return runtime == null ? items.get(normalized) : runtime;
        }
    }

    public DamageTypeDefinition resolve(String id) {
        synchronized (stateLock) {
            if (Texts.isBlank(id)) {
                return null;
            }
            return aliasIndex.get(normalizeId(id));
        }
    }

    private void rebuildIndexes() {
        aliasIndex.clear();
        Map<String, DamageTypeDefinition> merged = new LinkedHashMap<>(items);
        merged.putAll(runtimeDefinitions);
        for (DamageTypeDefinition definition : merged.values()) {
            aliasIndex.put(definition.id(), definition);
            for (String alias : definition.aliases()) {
                aliasIndex.putIfAbsent(normalizeId(alias), definition);
            }
        }
        definitionSignature = SignatureUtil.stableSignature(merged.values());
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
                if (definition.parentAttribute()) {
                    issue("loader.damage_type_parent_attribute_forbidden", Map.of("attribute", definition.id()));
                    return "";
                }
                return definition.id();
            }
        }
        return normalized;
    }
}
