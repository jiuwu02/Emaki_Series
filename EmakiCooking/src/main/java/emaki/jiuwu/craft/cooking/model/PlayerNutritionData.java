package emaki.jiuwu.craft.cooking.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.session.SessionData;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class PlayerNutritionData implements SessionData<PlayerNutritionData> {

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

    public void set(String typeId, double value) {
        String id = Texts.normalizeId(typeId);
        Double previous = values.put(id, value);
        if (previous == null || Double.compare(previous, value) != 0) {
            markDirty();
        }
    }

    @Override
    public void markDirty() {
        revision++;
    }

    @Override
    public void clearDirty() {
        persistedRevision = revision;
    }

    @Override
    public boolean dirty() {
        return revision > persistedRevision;
    }

    @Override
    public long revision() {
        return revision;
    }

    @Override
    public long persistedRevision() {
        return persistedRevision;
    }

    @Override
    public void markPersisted(long savedRevision) {
        if (savedRevision > persistedRevision) {
            persistedRevision = Math.min(savedRevision, revision);
        }
    }

    @Override
    public PlayerNutritionData copy() {
        PlayerNutritionData copy = new PlayerNutritionData(uuid, name);
        copy.values.putAll(values);
        copy.revision = revision;
        copy.persistedRevision = persistedRevision;
        return copy;
    }
}
