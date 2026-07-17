package emaki.jiuwu.craft.cooking.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * 玩家的营养数据。按营养类型 id 持有当前数值，并通过 revision 跟踪持久化状态。
 */
public final class PlayerNutritionData {

    private final UUID uuid;
    private String name;
    private final Map<String, Double> values = new LinkedHashMap<>();
    private long revision;
    private long persistedRevision;

    public PlayerNutritionData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (Texts.isNotBlank(name) && !name.equals(this.name)) {
            this.name = name;
            markDirty();
        }
    }

    public Map<String, Double> values() {
        return Map.copyOf(values);
    }

    /**
     * 读取指定营养类型的当前值；不存在时返回 {@code fallback}。
     */
    public double value(String typeId, double fallback) {
        Double value = values.get(Texts.normalizeId(typeId));
        return value == null ? fallback : value;
    }

    public double get(String typeId) {
        return value(typeId, 0D);
    }

    public boolean has(String typeId) {
        return values.containsKey(Texts.normalizeId(typeId));
    }

    /**
     * 直接写入指定营养类型的值（调用方负责截断）。值发生变化时递增 revision。
     */
    public void set(String typeId, double value) {
        String id = Texts.normalizeId(typeId);
        Double previous = values.put(id, value);
        if (previous == null || Double.compare(previous, value) != 0) {
            markDirty();
        }
    }

    public void markDirty() {
        revision++;
    }

    public void clearDirty() {
        persistedRevision = revision;
    }

    public boolean dirty() {
        return revision > persistedRevision;
    }

    public long revision() {
        return revision;
    }

    public long persistedRevision() {
        return persistedRevision;
    }

    public void markPersisted(long savedRevision) {
        if (savedRevision > persistedRevision) {
            persistedRevision = Math.min(savedRevision, revision);
        }
    }

    public PlayerNutritionData copy() {
        PlayerNutritionData copy = new PlayerNutritionData(uuid, name);
        copy.values.putAll(values);
        copy.revision = revision;
        copy.persistedRevision = persistedRevision;
        return copy;
    }
}
