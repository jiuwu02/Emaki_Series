package emaki.jiuwu.craft.attribute.script.js;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeDamageEvent;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.corelib.api.script.ScriptServerApi.ScriptEntityApi;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptDamageEventApi {

    private final EmakiAttributeDamageEvent event;
    private final Map<String, Object> meta = new LinkedHashMap<>();

    public ScriptDamageEventApi(EmakiAttributeDamageEvent event) {
        this.event = event;
    }

    public ScriptEntityApi attacker() {
        return new ScriptEntityApi(event == null ? null : event.getAttacker());
    }

    public ScriptEntityApi target() {
        return new ScriptEntityApi(event == null ? null : event.getTarget());
    }

    public ScriptEntityApi projectile() {
        return new ScriptEntityApi(event == null ? null : event.getProjectile());
    }

    public String damageTypeId() {
        return event == null ? "" : event.getDamageTypeId();
    }

    public double baseDamage() {
        return event == null ? 0D : event.getBaseDamage();
    }

    public double finalDamage() {
        return event == null ? 0D : event.getFinalDamage();
    }

    public void setFinalDamage(double value) {
        if (event != null) {
            event.setFinalDamage(Math.max(0D, value));
        }
    }

    public void multiplyDamage(double multiplier) {
        if (event != null) {
            event.setFinalDamage(Math.max(0D, event.getFinalDamage() * multiplier));
        }
    }

    public void cancel() {
        if (event != null) {
            event.setCancelled(true);
        }
    }

    public boolean cancelled() {
        return event != null && event.isCancelled();
    }

    public boolean critical() {
        return event != null && event.isCritical();
    }

    public double roll() {
        return event == null ? 0D : event.getRoll();
    }

    public Map<String, Object> context() {
        return event == null ? Map.of() : event.getContext();
    }

    public Map<String, Double> stageValues() {
        return event == null || event.getDamageResult() == null ? Map.of() : event.getDamageResult().stageValues();
    }

    public double attackerAttribute(String attributeId) {
        return attributeValue(attackerSnapshotRaw(), attributeId);
    }

    public double targetAttribute(String attributeId) {
        return attributeValue(targetSnapshotRaw(), attributeId);
    }

    public Map<String, Double> attackerAttributes() {
        AttributeSnapshot snapshot = attackerSnapshotRaw();
        return snapshot == null ? Map.of() : snapshot.values();
    }

    public Map<String, Double> targetAttributes() {
        AttributeSnapshot snapshot = targetSnapshotRaw();
        return snapshot == null ? Map.of() : snapshot.values();
    }

    public Map<String, Object> attackerSnapshot() {
        AttributeSnapshot snapshot = attackerSnapshotRaw();
        return snapshot == null ? Map.of() : snapshot.toMap();
    }

    public Map<String, Object> targetSnapshot() {
        AttributeSnapshot snapshot = targetSnapshotRaw();
        return snapshot == null ? Map.of() : snapshot.toMap();
    }

    public Object meta(String key) {
        return meta.get(key);
    }

    public void setMeta(String key, Object value) {
        if (key != null) {
            if (value == null) {
                meta.remove(key);
            } else {
                meta.put(key, value);
            }
        }
    }

    public Map<String, Object> meta() {
        return Map.copyOf(meta);
    }

    private double attributeValue(AttributeSnapshot snapshot, String attributeId) {
        if (snapshot == null || Texts.isBlank(attributeId)) {
            return 0D;
        }
        return snapshot.values().getOrDefault(Texts.normalizeId(attributeId), 0D);
    }

    private AttributeSnapshot attackerSnapshotRaw() {
        return event == null || event.getDamageContext() == null ? null : event.getDamageContext().attackerSnapshot();
    }

    private AttributeSnapshot targetSnapshotRaw() {
        return event == null || event.getDamageContext() == null ? null : event.getDamageContext().targetSnapshot();
    }
}
