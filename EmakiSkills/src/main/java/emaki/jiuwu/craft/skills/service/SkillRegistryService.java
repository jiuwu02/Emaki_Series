package emaki.jiuwu.craft.skills.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.api.SkillSourceEntry;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSourceType;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.provider.EquipmentSkillCollector;
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
        return allDefinitions().get(Texts.normalizeId(skillId));
    }

    public List<UnlockedSkillEntry> collectUnlockedSkills(Player player,
            EquipmentSkillCollector equipmentCollector,
            SkillSourceRegistry sourceRegistry) {
        if (player == null) {
            return List.of();
        }

        List<UnlockedSkillEntry> raw = new ArrayList<>();
        if (equipmentCollector != null) {
            raw.addAll(equipmentCollector.collect(player));
        }

        if (sourceRegistry != null) {
            for (SkillSourceRegistry.RegisteredSource source : sourceRegistry.all()) {
                try {
                    var entries = source.provider().collect(player);
                    if (entries == null) {
                        continue;
                    }
                    for (SkillSourceEntry entry : entries) {
                        UnlockedSkillEntry mapped = mapSourceEntry(source, entry);
                        if (mapped != null) {
                            raw.add(mapped);
                        }
                    }
                } catch (RuntimeException | LinkageError exception) {
                    plugin.getLogger().warning("[SkillRegistry] Provider '" + source.id()
                            + "' owned by '" + source.owner().getName() + "' failed: " + exception.getMessage());
                }
            }
        }

        Map<String, UnlockedSkillEntry> seen = new LinkedHashMap<>();
        for (UnlockedSkillEntry entry : raw) {
            seen.putIfAbsent(entry.skillId(), entry);
        }
        return List.copyOf(seen.values());
    }

    private UnlockedSkillEntry mapSourceEntry(SkillSourceRegistry.RegisteredSource source,
            SkillSourceEntry entry) {
        if (entry == null) {
            return null;
        }
        String skillId = Texts.normalizeId(entry.skillId());
        SkillDefinition definition = getDefinition(skillId);
        if (Texts.isBlank(skillId) || definition == null || !definition.enabled()) {
            return null;
        }
        SkillSourceType type = "manual".equals(source.id()) ? SkillSourceType.MANUAL : SkillSourceType.PROVIDER;
        return new UnlockedSkillEntry(
                skillId,
                source.id(),
                type,
                Texts.isBlank(entry.sourceSlot()) ? null : entry.sourceSlot(),
                Texts.isBlank(entry.displayHint()) ? null : entry.displayHint());
    }
}
