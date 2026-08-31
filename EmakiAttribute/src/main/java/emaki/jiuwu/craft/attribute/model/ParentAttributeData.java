package emaki.jiuwu.craft.attribute.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ParentAttributeData {

    private final UUID uuid;
    private String name;
    private int availablePoints;
    private int resetPoints;
    private long updatedAt;
    private long revision;
    private long persistedRevision;
    private final Map<String, Integer> allocations = new LinkedHashMap<>();

    public ParentAttributeData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = Texts.toStringSafe(name);
        this.updatedAt = System.currentTimeMillis();
    }

    public ParentAttributeData copy() {
        ParentAttributeData copy = new ParentAttributeData(uuid, name);
        copy.availablePoints = availablePoints;
        copy.resetPoints = resetPoints;
        copy.updatedAt = updatedAt;
        copy.revision = revision;
        copy.persistedRevision = persistedRevision;
        copy.allocations.putAll(allocations);
        return copy;
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
        return Collections.unmodifiableMap(new LinkedHashMap<>(allocations));
    }

    public void putAllocation(String attributeId, int points) {
        allocations.put(Texts.normalizeId(attributeId), points);
        markDirty();
    }

    public void addAllocation(String attributeId, int points) {
        allocations.merge(Texts.normalizeId(attributeId), points, Integer::sum);
        markDirty();
    }

    public boolean removeAllocation(String attributeId) {
        if (allocations.remove(Texts.normalizeId(attributeId)) == null) {
            return false;
        }
        markDirty();
        return true;
    }

    public void clearAllocations() {
        if (allocations.isEmpty()) {
            return;
        }
        allocations.clear();
        markDirty();
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

    public long revision() {
        return revision;
    }

    public long persistedRevision() {
        return persistedRevision;
    }

    public boolean dirty() {
        return revision > persistedRevision;
    }

    public void clearDirty() {
        this.persistedRevision = revision;
    }

    public void markPersisted(long revision) {
        this.persistedRevision = Math.max(this.persistedRevision, revision);
    }

    public void markDirty() {
        this.revision++;
        this.updatedAt = System.currentTimeMillis();
    }
}
