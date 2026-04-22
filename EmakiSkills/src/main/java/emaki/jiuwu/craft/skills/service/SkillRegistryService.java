package emaki.jiuwu.craft.skills.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.provider.EquipmentSkillCollector;
import emaki.jiuwu.craft.skills.provider.SkillSourceProvider;
import emaki.jiuwu.craft.skills.provider.SkillSourceRegistry;

public final class SkillRegistryService {

    private final JavaPlugin plugin;
    private final Supplier<Map<String, SkillDefinition>> definitionsSupplier;

    public SkillRegistryService(JavaPlugin plugin,
            Supplier<Map<String, SkillDefinition>> definitionsSupplier) {
        this.plugin = plugin;
        this.definitionsSupplier = definitionsSupplier;
    }

    public Map<String, SkillDefinition> allDefinitions() {
        Map<String, SkillDefinition> defs = definitionsSupplier.get();
        return defs == null ? Map.of() : defs;
    }

    public SkillDefinition getDefinition(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return null;
        }
        return allDefinitions().get(skillId);
    }

    public List<UnlockedSkillEntry> collectUnlockedSkills(Player player,
            EquipmentSkillCollector equipmentCollector,
            SkillSourceRegistry sourceRegistry) {
        if (player == null) {
            return List.of();
        }

        List<UnlockedSkillEntry> raw = new ArrayList<>();

        // 1. Collect from equipment
        if (equipmentCollector != null) {
            raw.addAll(equipmentCollector.collect(player));
        }

        // 2. Collect from all registered providers
        if (sourceRegistry != null) {
            for (SkillSourceProvider provider : sourceRegistry.all()) {
                try {
                    var entries = provider.collect(player);
                    if (entries != null) {
                        raw.addAll(entries);
                    }
                } catch (Exception exception) {
                    plugin.getLogger().warning("[SkillRegistry] Provider '"
                            + provider.id() + "' threw exception: " + exception.getMessage());
                }
            }
        }

        // 3. Deduplicate by skillId (keep first occurrence)
        Map<String, UnlockedSkillEntry> seen = new LinkedHashMap<>();
        for (UnlockedSkillEntry entry : raw) {
            seen.putIfAbsent(entry.skillId(), entry);
        }
        return List.copyOf(seen.values());
    }
}
