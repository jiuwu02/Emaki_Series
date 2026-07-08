package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ParentAttributeData {

    private final UUID uuid;
    private String name;
    private int availablePoints;
    private int resetPoints;
    private long updatedAt;
    private boolean dirty;
    private final Map<String, Integer> allocations = new LinkedHashMap<>();

    public ParentAttributeData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = Texts.toStringSafe(name);
        this.updatedAt = System.currentTimeMillis();
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        String safeName = Texts.toStringSafe(name);
        if (!safeName.equals(this.name)) {
            this.name = safeName;
            markDirty();
        }
    }

    public int availablePoints() {
        return availablePoints;
    }

    public void availablePoints(int availablePoints) {
        int safeValue = Math.max(0, availablePoints);
        if (this.availablePoints != safeValue) {
            this.availablePoints = safeValue;
            markDirty();
        }
    }

    public int resetPoints() {
        return resetPoints;
    }

    public void resetPoints(int resetPoints) {
        int safeValue = Math.max(0, resetPoints);
        if (this.resetPoints != safeValue) {
            this.resetPoints = safeValue;
            markDirty();
        }
    }

    public Map<String, Integer> allocations() {
        return allocations;
    }

    public int allocation(String attributeId) {
        return allocations.getOrDefault(Texts.normalizeId(attributeId), 0);
    }

    public int allocatedTotal() {
        int total = 0;
        for (int value : allocations.values()) {
            total += Math.max(0, value);
        }
        return total;
    }

    public long updatedAt() {
        return updatedAt;
    }

    public boolean dirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public void markDirty() {
        this.dirty = true;
        this.updatedAt = System.currentTimeMillis();
    }
}
