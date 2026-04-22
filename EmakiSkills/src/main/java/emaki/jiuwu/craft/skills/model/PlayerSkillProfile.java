package emaki.jiuwu.craft.skills.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSkillProfile {

    private String uuid;
    private final List<SkillSlotBinding> bindings;
    private final Map<String, PlayerLocalResourceState> localResources;
    private final PlayerCastTimingState timingState;
    private boolean castModeEnabled;
    private boolean dirty;

    public PlayerSkillProfile(String uuid, int slotCount) {
        this.uuid = uuid == null ? "" : uuid;
        this.bindings = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            bindings.add(new SkillSlotBinding(i, null, null));
        }
        this.localResources = new ConcurrentHashMap<>();
        this.timingState = new PlayerCastTimingState();
        this.castModeEnabled = false;
        this.dirty = false;
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
        bindings.set(slotIndex, binding == null ? new SkillSlotBinding(slotIndex, null, null) : binding);
        markDirty();
    }

    public void clearSlot(int slotIndex) {
        setBinding(slotIndex, new SkillSlotBinding(slotIndex, null, null));
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
