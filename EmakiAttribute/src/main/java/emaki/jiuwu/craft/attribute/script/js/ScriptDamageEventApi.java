package emaki.jiuwu.craft.attribute.script.js;

import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

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

    @HostAccess.Export
    public ScriptEntityApi attacker() {
        return new ScriptEntityApi(event == null ? null : event.getAttacker());
    }

    @HostAccess.Export
    public ScriptEntityApi target() {
        return new ScriptEntityApi(event == null ? null : event.getTarget());
    }

    @HostAccess.Export
    public ScriptEntityApi projectile() {
        return new ScriptEntityApi(event == null ? null : event.getProjectile());
    }

    @HostAccess.Export
    public String damageTypeId() {
        return event == null ? "" : event.getDamageTypeId();
    }

    @HostAccess.Export
    public double baseDamage() {
        return event == null ? 0D : event.getBaseDamage();
    }

    @HostAccess.Export
    public double finalDamage() {
        return event == null ? 0D : event.getFinalDamage();
    }

    @HostAccess.Export
    public void setFinalDamage(double value) {
        if (event != null) {
            event.setFinalDamage(Math.max(0D, value));
        }
    }

    @HostAccess.Export
    public void multiplyDamage(double multiplier) {
        if (event != null) {
            event.setFinalDamage(Math.max(0D, event.getFinalDamage() * multiplier));
        }
    }

    @HostAccess.Export
    public void cancel() {
        if (event != null) {
            event.setCancelled(true);
        }
    }

    @HostAccess.Export
    public boolean cancelled() {
        return event != null && event.isCancelled();
    }

    @HostAccess.Export
    public boolean critical() {
        return event != null && event.isCritical();
    }

    @HostAccess.Export
    public double roll() {
        return event == null ? 0D : event.getRoll();
    }

    @HostAccess.Export
    public Map<String, Object> context() {
        return event == null ? Map.of() : event.getContext();
    }

    @HostAccess.Export
    public Map<String, Double> stageValues() {
        return event == null || event.getDamageResult() == null ? Map.of() : event.getDamageResult().stageValues();
    }

    @HostAccess.Export
    public double attackerAttribute(String attributeId) {
        return attributeValue(attackerSnapshotRaw(), attributeId);
    }

    @HostAccess.Export
    public double targetAttribute(String attributeId) {
        return attributeValue(targetSnapshotRaw(), attributeId);
    }

    @HostAccess.Export
    public Map<String, Double> attackerAttributes() {
        AttributeSnapshot snapshot = attackerSnapshotRaw();
        return snapshot == null ? Map.of() : snapshot.values();
    }

    @HostAccess.Export
    public Map<String, Double> targetAttributes() {
        AttributeSnapshot snapshot = targetSnapshotRaw();
        return snapshot == null ? Map.of() : snapshot.values();
    }

    @HostAccess.Export
    public Map<String, Object> attackerSnapshot() {
        AttributeSnapshot snapshot = attackerSnapshotRaw();
        return snapshot == null ? Map.of() : snapshot.toMap();
    }

    @HostAccess.Export
    public Map<String, Object> targetSnapshot() {
        AttributeSnapshot snapshot = targetSnapshotRaw();
        return snapshot == null ? Map.of() : snapshot.toMap();
    }

    @HostAccess.Export
    public Object meta(String key) {
        return meta.get(key);
    }

    @HostAccess.Export
    public void setMeta(String key, Object value) {
        if (key != null) {
            if (value == null) {
                meta.remove(key);
            } else {
                meta.put(key, value);
            }
        }
    }

    @HostAccess.Export
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
