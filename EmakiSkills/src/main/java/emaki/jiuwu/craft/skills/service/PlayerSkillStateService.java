package emaki.jiuwu.craft.skills.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.provider.EquipmentSkillCollector;
import emaki.jiuwu.craft.skills.provider.SkillSourceRegistry;
import emaki.jiuwu.craft.skills.trigger.TriggerConflictResolver;
import emaki.jiuwu.craft.skills.trigger.TriggerRegistry;

public final class PlayerSkillStateService {

    private final JavaPlugin plugin;
    private final PlayerSkillDataStore dataStore;
    private final SkillRegistryService registryService;
    private final EquipmentSkillCollector equipmentCollector;
    private final SkillSourceRegistry sourceRegistry;
    private final TriggerConflictResolver conflictResolver;
    private final TriggerRegistry triggerRegistry;

    public PlayerSkillStateService(JavaPlugin plugin,
            PlayerSkillDataStore dataStore,
            SkillRegistryService registryService,
            EquipmentSkillCollector equipmentCollector,
            SkillSourceRegistry sourceRegistry,
            TriggerConflictResolver conflictResolver,
            TriggerRegistry triggerRegistry) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.registryService = registryService;
        this.equipmentCollector = equipmentCollector;
        this.sourceRegistry = sourceRegistry;
        this.conflictResolver = conflictResolver;
        this.triggerRegistry = triggerRegistry;
    }

    public List<UnlockedSkillEntry> getUnlockedSkills(Player player) {
        return registryService.collectUnlockedSkills(player, equipmentCollector, sourceRegistry);
    }

    public boolean equipSkill(Player player, int slotIndex, String skillId) {
        if (player == null || skillId == null || skillId.isBlank()) {
            return false;
        }
        SkillDefinition definition = registryService.getDefinition(skillId);
        if (definition == null || !definition.enabled()) {
            return false;
        }
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return false;
        }
        SkillSlotBinding current = profile.getBinding(slotIndex);
        if (current == null) {
            return false;
        }
        // Preserve existing trigger if any
        profile.setBinding(slotIndex, new SkillSlotBinding(slotIndex, skillId, current.triggerId()));
        return true;
    }

    public boolean unequipSkill(Player player, int slotIndex) {
        if (player == null) {
            return false;
        }
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return false;
        }
        SkillSlotBinding current = profile.getBinding(slotIndex);
        if (current == null) {
            return false;
        }
        profile.clearSlot(slotIndex);
        return true;
    }

    public boolean bindTrigger(Player player, int slotIndex, String triggerId) {
        if (player == null || triggerId == null || triggerId.isBlank()) {
            return false;
        }
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return false;
        }
        SkillSlotBinding current = profile.getBinding(slotIndex);
        if (current == null || current.isEmpty()) {
            return false;
        }

        // Check for conflicts
        String conflict = checkTriggerConflict(player, slotIndex, triggerId);
        if (conflict != null) {
            return false;
        }

        profile.setBinding(slotIndex, new SkillSlotBinding(slotIndex, current.skillId(), triggerId));
        return true;
    }

    public String checkTriggerConflict(Player player, int targetSlot, String triggerId) {
        if (player == null || triggerId == null || triggerId.isBlank()) {
            return null;
        }
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return null;
        }
        for (SkillSlotBinding binding : profile.bindings()) {
            if (binding.slotIndex() == targetSlot || binding.isEmpty() || binding.triggerId() == null) {
                continue;
            }
            if (conflictResolver.conflicts(triggerId, binding.triggerId())) {
                SkillDefinition def = registryService.getDefinition(binding.skillId());
                String skillName = def != null ? def.displayName() : binding.skillId();
                return "Slot " + binding.slotIndex() + " (" + skillName + ") uses conflicting trigger";
            }
        }
        return null;
    }

    public void validateBindings(Player player) {
        if (player == null) {
            return;
        }
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return;
        }
        List<UnlockedSkillEntry> unlocked = getUnlockedSkills(player);
        Set<String> unlockedIds = new HashSet<>();
        for (UnlockedSkillEntry entry : unlocked) {
            unlockedIds.add(entry.skillId());
        }

        for (SkillSlotBinding binding : profile.bindings()) {
            if (binding.isEmpty()) {
                continue;
            }
            if (!unlockedIds.contains(binding.skillId()) || !isValidTrigger(binding.triggerId())) {
                profile.clearSlot(binding.slotIndex());
            }
        }
    }

    private boolean isValidTrigger(String triggerId) {
        return triggerId != null && triggerRegistry != null && triggerRegistry.isEnabled(triggerId);
    }

    public SkillDefinition getDefinition(String skillId) {
        return registryService.getDefinition(skillId);
    }

    public PlayerSkillProfile getProfile(Player player) {
        return dataStore.get(player);
    }
}
