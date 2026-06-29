package emaki.jiuwu.craft.cooking.model;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * 玩家的营养数据。按营养类型 id 持有当前数值，并跟踪脏标记以决定是否需要落盘。
 *
 * <p>仿照 EmakiLevel 的 {@code PlayerLevelData} 模式实现。</p>
 */
public final class PlayerNutritionData {

    private final UUID uuid;
    private volatile String name;
    private final Map<String, Double> values = new ConcurrentHashMap<>();
    private volatile boolean dirty;

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
        return values;
    }

    /**
     * 读取指定营养类型的当前值；不存在时返回 {@code fallback}。
     */
    public double value(String typeId, double fallback) {
        Double value = values.get(Texts.normalizeId(typeId));
        return value == null ? fallback : value;
    }

    public boolean has(String typeId) {
        return values.containsKey(Texts.normalizeId(typeId));
    }

    /**
     * 直接写入指定营养类型的值（调用方负责截断）。值发生变化时标脏。
     */
    public void set(String typeId, double value) {
        String id = Texts.normalizeId(typeId);
        Double previous = values.put(id, value);
        if (previous == null || previous != value) {
            markDirty();
        }
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public boolean dirty() {
        return dirty;
    }
}
