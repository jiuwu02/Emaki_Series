package emaki.jiuwu.craft.attribute.loader;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class DamageTypeRegistry extends DirectoryLoader<DamageTypeDefinition> {

    private static final List<String> BUNDLED_RESOURCES = List.of(
            "physical.yml",
            "projectile.yml",
            "spell.yml",
            "skill.yml"
    );
    private static final Map<String, String> LEGACY_ATTRIBUTE_REFERENCE_ALIASES = Map.ofEntries(
            Map.entry("final_physical_damage", "physical_damage_bonus"),
            Map.entry("physical_crit_chance", "physical_crit_rate"),
            Map.entry("physical_crit_multiplier", "physical_crit_damage"),
            Map.entry("physical_armor", "physical_defense"),
            Map.entry("projectile_damage", "projectile_attack"),
            Map.entry("final_projectile_damage", "projectile_damage_bonus"),
            Map.entry("projectile_crit_chance", "projectile_crit_rate"),
            Map.entry("projectile_crit_multiplier", "projectile_crit_damage"),
            Map.entry("projectile_armor", "projectile_defense"),
            Map.entry("magic_attack", "spell_attack"),
            Map.entry("final_magic_damage", "spell_damage_bonus"),
            Map.entry("magic_crit_chance", "spell_crit_rate"),
            Map.entry("magic_crit_multiplier", "spell_crit_damage"),
            Map.entry("magic_resistance", "spell_defense"),
            Map.entry("flat_lifesteal", "percentage_lifesteal"),
            Map.entry("percent_lifesteal", "lifesteal")
    );

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
    protected void seedBundledResources(File directory) {
        for (String resourceName : BUNDLED_RESOURCES) {
            copyBundledResource("damage_types/" + resourceName, new File(directory, resourceName));
        }
    }

    @Override
    protected boolean validateSchema(File file, YamlConfiguration configuration) {
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
                if (stage instanceof Map<?, ?> || stage instanceof ConfigurationSection) {
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
        if (recovery != null && !(recovery instanceof Map<?, ?> || recovery instanceof ConfigurationSection)) {
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
        String normalized = normalizeId(id);
        if (attributeRegistry != null) {
            var definition = attributeRegistry.resolve(normalized);
            if (definition != null) {
                return definition.id();
            }
        }
        String legacyMapped = LEGACY_ATTRIBUTE_REFERENCE_ALIASES.get(normalized);
        return legacyMapped == null ? normalized : legacyMapped;
    }
}
