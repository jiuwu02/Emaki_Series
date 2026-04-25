package emaki.jiuwu.craft.skills.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSkillProfile {

    private String uuid;
    private final List<SkillSlotBinding> bindings;
    private final Map<String, PlayerLocalResourceState> localResources;
    private final Map<String, PlayerSkillLevelState> skillLevels;
    private final PlayerCastTimingState timingState;
    private final Map<String, SkillSlotBinding> bindingByTrigger = new ConcurrentHashMap<>();
    private boolean castModeEnabled;
    private boolean dirty;

    public PlayerSkillProfile(String uuid, int slotCount) {
        this.uuid = uuid == null ? "" : uuid;
        this.bindings = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            bindings.add(new SkillSlotBinding(i, null, null));
        }
        this.localResources = new ConcurrentHashMap<>();
        this.skillLevels = new ConcurrentHashMap<>();
        this.timingState = new PlayerCastTimingState();
        this.castModeEnabled = false;
        this.dirty = false;
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

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }
}
