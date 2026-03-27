package emaki.jiuwu.craft.attribute.loader;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.DamageStageDefinition;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;

public final class DamageTypeRegistry extends DirectoryLoader<DamageTypeDefinition> {

    private final Map<String, DamageTypeDefinition> aliasIndex = new LinkedHashMap<>();

    public DamageTypeRegistry(EmakiAttributePlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "damage_types";
    }

    @Override
    protected String typeName() {
        return "damage type";
    }

    @Override
    protected DamageTypeDefinition parse(File file, YamlConfiguration configuration) {
        List<DamageStageDefinition> stages = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(configuration.get("stages"))) {
            DamageStageDefinition stage = DamageStageDefinition.fromMap(entry);
            if (stage != null) {
                stages.add(stage);
            }
        }
        return new DamageTypeDefinition(
            configuration.getString("id"),
            configuration.getString("display_name"),
            configuration.getStringList("aliases"),
            new java.util.LinkedHashSet<>(configuration.getStringList("allowed_events")),
            configuration.getBoolean("hard_lock", false),
            stages,
            configuration.getString("description")
        );
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
}
