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
        Set<String> unlockedIds = unlockedSkillIds(player);
        SkillSlotBinding current = profile.getBinding(slotIndex);
        if (current == null || !unlockedIds.contains(definition.id()) || !canEquipSkill(profile, slotIndex, definition)) {
            return false;
        }
        if (fireSlotChange(player, slotIndex, definition.id(), null, PlayerSkillSlotChangeEvent.Action.EQUIP)) {
            return false;
        }
        return dataStore.mutate(player, active -> {
            active.setBinding(slotIndex, new SkillSlotBinding(slotIndex, definition.id(), current.triggerId()));
            stabilizeBindings(active, unlockedIds);
        });
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
        Set<String> unlockedIds = unlockedSkillIds(player);
        return dataStore.mutate(player, active -> {
            active.clearSlot(slotIndex);
            stabilizeBindings(active, unlockedIds);
        });
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
        if (current == null || current.isEmpty() || !isValidTrigger(triggerId)) {
            return false;
        }

        String conflict = checkTriggerConflict(player, slotIndex, triggerId);
        if (conflict != null) {
            return false;
        }

        if (fireSlotChange(player, slotIndex, current.skillId(), triggerId, PlayerSkillSlotChangeEvent.Action.BIND_TRIGGER)) {
            return false;
        }

        Set<String> unlockedIds = unlockedSkillIds(player);
        return dataStore.mutate(player, active -> {
            active.setBinding(slotIndex, new SkillSlotBinding(slotIndex, current.skillId(), triggerId));
            stabilizeBindings(active, unlockedIds);
        });
    }

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
        if (player == null || dataStore.get(player) == null) {
            return;
        }
        Set<String> unlockedIds = unlockedSkillIds(player);
        dataStore.mutate(player, profile -> stabilizeBindings(profile, unlockedIds));
    }

    private Set<String> unlockedSkillIds(Player player) {
        Set<String> unlockedIds = new HashSet<>();
        for (UnlockedSkillEntry entry : getUnlockedSkills(player)) {
            if (entry != null && entry.skillId() != null && !entry.skillId().isBlank()) {
                unlockedIds.add(entry.skillId());
            }
        }
        return Set.copyOf(unlockedIds);
    }

    private void stabilizeBindings(PlayerSkillProfile profile, Set<String> unlockedIds) {
        if (profile == null) {
            return;
        }
        Set<String> safeUnlockedIds = unlockedIds == null ? Set.of() : unlockedIds;
        boolean changed;
        do {
            PlayerSkillProfile snapshot = profile.copy();
            Set<Integer> invalidSlots = new HashSet<>();
            for (SkillSlotBinding binding : snapshot.bindings()) {
                if (binding == null || binding.isEmpty()) {
                    continue;
                }
                SkillDefinition definition = registryService.getDefinition(binding.skillId());
                if (!safeUnlockedIds.contains(binding.skillId())
                        || !isValidTrigger(binding.triggerId())
                        || hasTriggerConflict(snapshot, binding)
                        || definition == null
                        || !canEquipSkill(snapshot, binding.slotIndex(), definition)) {
                    invalidSlots.add(binding.slotIndex());
                }
            }
            changed = !invalidSlots.isEmpty();
            for (int slotIndex : invalidSlots) {
                profile.clearSlot(slotIndex);
            }
        } while (changed);
    }

    private boolean hasTriggerConflict(PlayerSkillProfile profile, SkillSlotBinding candidate) {
        if (profile == null || candidate == null || candidate.triggerId() == null || candidate.triggerId().isBlank()) {
            return false;
        }
        for (SkillSlotBinding binding : profile.bindings()) {
            if (binding == null
                    || binding.slotIndex() == candidate.slotIndex()
                    || binding.isEmpty()
                    || binding.triggerId() == null
                    || binding.triggerId().isBlank()) {
                continue;
            }
            if (conflictResolver.conflicts(candidate.triggerId(), binding.triggerId())) {
                return true;
            }
        }
        return false;
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
        if (triggerId == null || triggerId.isBlank()) {
            return true;
        }
        if (triggerRegistry == null) {
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
