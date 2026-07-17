package emaki.jiuwu.craft.skills.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSkillProfile {

    private String uuid;
    private final List<SkillSlotBinding> bindings;
    private final Map<String, PlayerLocalResourceState> localResources;
    private final Map<String, PlayerSkillLevelState> skillLevels;
    private final Set<String> manualSkillIds;
    private final PlayerCastTimingState timingState;
    private final Map<String, SkillSlotBinding> bindingByTrigger = new ConcurrentHashMap<>();
    private boolean castModeEnabled;
    private long revision;
    private long persistedRevision;

    public PlayerSkillProfile(String uuid, int slotCount) {
        this.uuid = uuid == null ? "" : uuid;
        this.bindings = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            bindings.add(new SkillSlotBinding(i, null, null));
        }
        this.localResources = new ConcurrentHashMap<>();
        this.skillLevels = new ConcurrentHashMap<>();
        this.manualSkillIds = ConcurrentHashMap.newKeySet();
        this.timingState = new PlayerCastTimingState();
        this.castModeEnabled = false;
        this.revision = 0L;
        this.persistedRevision = 0L;
        rebuildTriggerIndex();
    }

    public String uuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid == null ? "" : uuid;
    }

    public List<SkillSlotBinding> bindings() {
        return bindings;
    }

    public Map<String, PlayerLocalResourceState> localResources() {
        return localResources;
    }

    public Map<String, PlayerSkillLevelState> skillLevels() {
        return skillLevels;
    }

    public Set<String> manualSkillIds() {
        return manualSkillIds;
    }

    public PlayerCastTimingState timingState() {
        return timingState;
    }

    public boolean castModeEnabled() {
        return castModeEnabled;
    }

    public void setCastModeEnabled(boolean castModeEnabled) {
        this.castModeEnabled = castModeEnabled;
    }

    public SkillSlotBinding getBinding(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= bindings.size()) {
            return null;
        }
        return bindings.get(slotIndex);
    }

    public void setBinding(int slotIndex, SkillSlotBinding binding) {
        if (slotIndex < 0 || slotIndex >= bindings.size()) {
            return;
        }
        SkillSlotBinding old = bindings.get(slotIndex);
        if (old != null && !old.isEmpty() && old.triggerId() != null) {
            bindingByTrigger.remove(old.triggerId());
        }
        SkillSlotBinding effective = binding == null ? new SkillSlotBinding(slotIndex, null, null) : binding;
        bindings.set(slotIndex, effective);
        if (!effective.isEmpty() && effective.triggerId() != null && !effective.triggerId().isBlank()) {
            bindingByTrigger.put(effective.triggerId(), effective);
        }
        markDirty();
    }

    public void clearSlot(int slotIndex) {
        setBinding(slotIndex, new SkillSlotBinding(slotIndex, null, null));
    }

    public SkillSlotBinding findBindingByTrigger(String triggerId) {
        if (triggerId == null || triggerId.isBlank()) return null;
        return bindingByTrigger.get(triggerId);
    }

    public void rebuildTriggerIndex() {
        bindingByTrigger.clear();
        for (SkillSlotBinding binding : bindings) {
            if (binding != null && !binding.isEmpty() && binding.triggerId() != null && !binding.triggerId().isBlank()) {
                bindingByTrigger.put(binding.triggerId(), binding);
            }
        }
    }

    public synchronized void markDirty() {
        revision++;
    }

    public synchronized boolean isDirty() {
        return revision > persistedRevision;
    }

    public synchronized void clearDirty() {
        persistedRevision = revision;
    }

    public synchronized void markPersisted(long savedRevision) {
        persistedRevision = Math.max(persistedRevision, Math.min(savedRevision, revision));
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized long persistedRevision() {
        return persistedRevision;
    }

    public synchronized PlayerSkillProfile copy() {
        PlayerSkillProfile copy = new PlayerSkillProfile(uuid, bindings.size());
        copy.bindings.clear();
        copy.bindings.addAll(bindings);
        for (Map.Entry<String, PlayerLocalResourceState> entry : localResources.entrySet()) {
            PlayerLocalResourceState value = entry.getValue();
            if (value != null) {
                copy.localResources.put(entry.getKey(), value.copy());
            }
        }
        for (Map.Entry<String, PlayerSkillLevelState> entry : skillLevels.entrySet()) {
            PlayerSkillLevelState value = entry.getValue();
            if (value != null) {
                copy.skillLevels.put(entry.getKey(), value.copy());
            }
        }
        copy.manualSkillIds.addAll(manualSkillIds);
        copy.timingState.setForcedGlobalCastDelayUntil(timingState.forcedGlobalCastDelayUntil());
        copy.timingState.setGlobalCooldownUntil(timingState.globalCooldownUntil());
        copy.timingState.skillCooldownUntilBySkillId().putAll(timingState.skillCooldownUntilBySkillId());
        copy.castModeEnabled = castModeEnabled;
        copy.revision = revision;
        copy.persistedRevision = persistedRevision;
        copy.rebuildTriggerIndex();
        return copy;
    }
}
