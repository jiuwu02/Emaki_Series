package emaki.jiuwu.craft.skills.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.skills.api.event.PlayerSkillSlotChangeEvent;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillActivationType;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.provider.EquipmentSkillCollector;
import emaki.jiuwu.craft.skills.provider.SkillSourceRegistry;
import emaki.jiuwu.craft.skills.trigger.SkillTriggerDefinition;
import emaki.jiuwu.craft.skills.trigger.TriggerConflictResolver;
import emaki.jiuwu.craft.skills.trigger.TriggerCategory;
import emaki.jiuwu.craft.skills.trigger.TriggerRegistry;

public final class PlayerSkillStateService {

    private final JavaPlugin plugin;
    private final PlayerSkillDataStore dataStore;
    private final SkillRegistryService registryService;
    private final EquipmentSkillCollector equipmentCollector;
    private final SkillSourceRegistry sourceRegistry;
    private final TriggerConflictResolver conflictResolver;
    private final TriggerRegistry triggerRegistry;
    private final Supplier<AppConfig> configSupplier;

    public PlayerSkillStateService(JavaPlugin plugin,
            PlayerSkillDataStore dataStore,
            SkillRegistryService registryService,
            EquipmentSkillCollector equipmentCollector,
            SkillSourceRegistry sourceRegistry,
            TriggerConflictResolver conflictResolver,
            TriggerRegistry triggerRegistry,
            Supplier<AppConfig> configSupplier) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.registryService = registryService;
        this.equipmentCollector = equipmentCollector;
        this.sourceRegistry = sourceRegistry;
        this.conflictResolver = conflictResolver;
        this.triggerRegistry = triggerRegistry;
        this.configSupplier = configSupplier;
    }

    public List<UnlockedSkillEntry> getUnlockedSkills(Player player) {
        return registryService.collectUnlockedSkills(player, equipmentCollector, sourceRegistry);
    }

    public List<UnlockedSkillEntry> getUnlockedActiveSkills(Player player) {
        return getUnlockedSkills(player).stream()
                .filter(entry -> {
                    SkillDefinition definition = registryService.getDefinition(entry.skillId());
                    return definition != null
                            && definition.activationType() == SkillActivationType.ACTIVE
                            && definition.showInSlots();
                })
                .toList();
    }

    public boolean equipSkill(Player player, int slotIndex, String skillId) {
        if (player == null || skillId == null || skillId.isBlank()) {
            return false;
        }
        SkillDefinition definition = registryService.getDefinition(skillId);
        if (definition == null || !definition.enabled()
                || definition.activationType() != SkillActivationType.ACTIVE
                || !definition.showInSlots()) {
            return false;
        }
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return false;
        }
        SkillSlotBinding current = profile.getBinding(slotIndex);
        if (current == null || !canEquipSkill(profile, slotIndex, definition)) {
            return false;
        }
        if (fireSlotChange(player, slotIndex, skillId, null, PlayerSkillSlotChangeEvent.Action.EQUIP)) {
            return false;
        }
        return dataStore.mutate(player, active ->
                active.setBinding(slotIndex, new SkillSlotBinding(slotIndex, skillId, current.triggerId())));
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
        if (fireSlotChange(player, slotIndex, current.skillId(), null, PlayerSkillSlotChangeEvent.Action.UNEQUIP)) {
            return false;
        }
        return dataStore.mutate(player, active -> active.clearSlot(slotIndex));
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

        String conflict = checkTriggerConflict(player, slotIndex, triggerId);
        if (conflict != null) {
            return false;
        }

        if (fireSlotChange(player, slotIndex, current.skillId(), triggerId, PlayerSkillSlotChangeEvent.Action.BIND_TRIGGER)) {
            return false;
        }

        return dataStore.mutate(player, active ->
                active.setBinding(slotIndex, new SkillSlotBinding(slotIndex, current.skillId(), triggerId)));
    }

    /**
     * Fires the slot change event and returns {@code true} when the change was
     * cancelled. Callers must already own the player's entity scheduler domain.
     */
    private boolean fireSlotChange(Player player,
            int slotIndex,
            String skillId,
            String triggerId,
            PlayerSkillSlotChangeEvent.Action action) {
        PlayerSkillSlotChangeEvent event = new PlayerSkillSlotChangeEvent(player, slotIndex, skillId, triggerId, action);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
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
        if (dataStore.get(player) == null) {
            return;
        }
        List<UnlockedSkillEntry> unlocked = getUnlockedSkills(player);
        Set<String> unlockedIds = new HashSet<>();
        for (UnlockedSkillEntry entry : unlocked) {
            unlockedIds.add(entry.skillId());
        }

        dataStore.mutate(player, profile -> {
            for (SkillSlotBinding binding : List.copyOf(profile.bindings())) {
                if (binding.isEmpty()) {
                    continue;
                }
                SkillDefinition definition = registryService.getDefinition(binding.skillId());
                if (!unlockedIds.contains(binding.skillId())
                        || !isValidTrigger(binding.triggerId())
                        || definition == null
                        || !canEquipSkill(profile, binding.slotIndex(), definition)) {
                    profile.clearSlot(binding.slotIndex());
                }
            }
        });
    }

    private boolean canEquipSkill(PlayerSkillProfile profile, int targetSlot, SkillDefinition definition) {
        if (profile == null || definition == null || !definition.showInSlots()) {
            return false;
        }
        for (String requiredSkillId : definition.requiredSkillIds()) {
            if (!isSkillEquipped(profile, requiredSkillId, targetSlot)) {
                return false;
            }
        }
        for (SkillSlotBinding binding : profile.bindings()) {
            if (binding == null || binding.slotIndex() == targetSlot || binding.isEmpty()) {
                continue;
            }
            if (definition.id().equals(binding.skillId())) {
                return false;
            }
            SkillDefinition equipped = registryService.getDefinition(binding.skillId());
            if (skillsConflict(definition, equipped) || skillsConflict(equipped, definition)) {
                return false;
            }
        }
        return passesTagLimits(profile, targetSlot, definition);
    }

    private boolean isSkillEquipped(PlayerSkillProfile profile, String skillId, int excludedSlot) {
        if (profile == null || skillId == null || skillId.isBlank()) {
            return false;
        }
        for (SkillSlotBinding binding : profile.bindings()) {
            if (binding != null && binding.slotIndex() != excludedSlot && skillId.equals(binding.skillId())) {
                return true;
            }
        }
        return false;
    }

    private boolean skillsConflict(SkillDefinition left, SkillDefinition right) {
        return left != null && right != null && left.conflictingSkillIds().contains(right.id());
    }

    private boolean passesTagLimits(PlayerSkillProfile profile, int targetSlot, SkillDefinition definition) {
        if (definition.tags().isEmpty()) {
            return true;
        }
        AppConfig config = configSupplier == null ? null : configSupplier.get();
        Map<String, Integer> limits = config == null ? Map.of() : config.skillTagEquipLimits();
        if (limits.isEmpty()) {
            return true;
        }
        for (String tag : definition.tags()) {
            int limit = limits.getOrDefault(tag, 0);
            if (limit <= 0) {
                continue;
            }
            int count = 1;
            for (SkillSlotBinding binding : profile.bindings()) {
                if (binding == null || binding.slotIndex() == targetSlot || binding.isEmpty()) {
                    continue;
                }
                SkillDefinition equipped = registryService.getDefinition(binding.skillId());
                if (equipped != null && equipped.tags().contains(tag)) {
                    count++;
                }
            }
            if (count > limit) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidTrigger(String triggerId) {
        if (triggerId == null || triggerRegistry == null) {
            return false;
        }
        SkillTriggerDefinition definition = triggerRegistry.get(triggerId);
        return definition != null && definition.enabled() && definition.category() == TriggerCategory.ACTIVE;
    }

    public SkillDefinition getDefinition(String skillId) {
        return registryService.getDefinition(skillId);
    }

    public PlayerSkillProfile getProfile(Player player) {
        return dataStore.get(player);
    }
}
