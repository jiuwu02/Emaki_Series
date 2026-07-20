package emaki.jiuwu.craft.attribute.script.js;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeDamageEvent;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.corelib.api.script.ScriptServerApi.ScriptEntityApi;
import emaki.jiuwu.craft.corelib.script.ScriptEntitySnapshot.EntityView;
import emaki.jiuwu.craft.corelib.script.ScriptEntitySnapshot.WorldView;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.text.Texts;


public final class ScriptDamageEventApi {

    private final EntityView attacker;
    private final EntityView target;
    private final EntityView projectile;
    private final ScriptEntityApi attackerApi;
    private final ScriptEntityApi targetApi;
    private final ScriptEntityApi projectileApi;
    private final String damageTypeId;
    private final double baseDamage;
    private final boolean critical;
    private final double roll;
    private final Map<String, Object> context;
    private final Map<String, Double> stageValues;
    private final Map<String, Double> attackerAttributes;
    private final Map<String, Double> targetAttributes;
    private final Map<String, Object> attackerSnapshot;
    private final Map<String, Object> targetSnapshot;
    private final Map<String, Object> meta = new LinkedHashMap<>();
    private double finalDamage;
    private boolean cancelled;

    public ScriptDamageEventApi(EmakiAttributeDamageEvent event) {
        DamageContext damageContext = event == null ? DamageContext.empty() : event.getDamageContext();
        DamageContextVariables variables = damageContext.variables();
        this.attacker = entityView(variables, "attacker", damageContext.attackerSnapshot());
        this.target = entityView(variables, "target", damageContext.targetSnapshot());
        this.projectile = entityView(variables, "projectile", null);
        this.attackerApi = new ScriptEntityApi(this.attacker);
        this.targetApi = new ScriptEntityApi(this.target);
        this.projectileApi = new ScriptEntityApi(this.projectile);
        this.damageTypeId = event == null ? "" : event.getDamageTypeId();
        this.baseDamage = event == null ? 0D : event.getBaseDamage();
        this.finalDamage = event == null ? 0D : Math.max(0D, event.getFinalDamage());
        this.cancelled = event != null && event.isCancelled();
        this.critical = event != null && event.isCritical();
        this.roll = event == null ? 0D : event.getRoll();
        this.context = ScriptSnapshots.immutableMap(event == null ? Map.of() : event.getContext());
        this.stageValues = immutableDoubleMap(event == null || event.getDamageResult() == null
                ? Map.of()
                : event.getDamageResult().stageValues());
        this.attackerAttributes = immutableDoubleMap(damageContext.attackerSnapshot().values());
        this.targetAttributes = immutableDoubleMap(damageContext.targetSnapshot().values());
        this.attackerSnapshot = ScriptSnapshots.immutableMap(damageContext.attackerSnapshot().toMap());
        this.targetSnapshot = ScriptSnapshots.immutableMap(damageContext.targetSnapshot().toMap());
    }

    @HostAccess.Export
    public ScriptEntityApi attacker() {
        return attackerApi;
    }

    @HostAccess.Export
    public ScriptEntityApi target() {
        return targetApi;
    }

    @HostAccess.Export
    public ScriptEntityApi projectile() {
        return projectileApi;
    }

    @HostAccess.Export
    public EntityView attackerView() {
        return attacker;
    }

    @HostAccess.Export
    public EntityView targetView() {
        return target;
    }

    @HostAccess.Export
    public EntityView projectileView() {
        return projectile;
    }

    @HostAccess.Export
    public String damageTypeId() {
        return damageTypeId;
    }

    @HostAccess.Export
    public double baseDamage() {
        return baseDamage;
    }

    @HostAccess.Export
    public double finalDamage() {
        return finalDamage;
    }

    @HostAccess.Export
    public void setFinalDamage(double value) {
        if (Double.isFinite(value)) {
            finalDamage = Math.max(0D, value);
        }
    }

    @HostAccess.Export
    public void multiplyDamage(double multiplier) {
        if (Double.isFinite(multiplier)) {
            setFinalDamage(finalDamage * multiplier);
        }
    }

    @HostAccess.Export
    public void cancel() {
        cancelled = true;
    }

    @HostAccess.Export
    public boolean cancelled() {
        return cancelled;
    }

    @HostAccess.Export
    public boolean critical() {
        return critical;
    }

    @HostAccess.Export
    public double roll() {
        return roll;
    }

    @HostAccess.Export
    public Map<String, Object> context() {
        return context;
    }

    @HostAccess.Export
    public Map<String, Double> stageValues() {
        return stageValues;
    }

    @HostAccess.Export
    public double attackerAttribute(String attributeId) {
        return attributeValue(attackerAttributes, attributeId);
    }

    @HostAccess.Export
    public double targetAttribute(String attributeId) {
        return attributeValue(targetAttributes, attributeId);
    }

    @HostAccess.Export
    public Map<String, Double> attackerAttributes() {
        return attackerAttributes;
    }

    @HostAccess.Export
    public Map<String, Double> targetAttributes() {
        return targetAttributes;
    }

    @HostAccess.Export
    public Map<String, Object> attackerSnapshot() {
        return attackerSnapshot;
    }

    @HostAccess.Export
    public Map<String, Object> targetSnapshot() {
        return targetSnapshot;
    }

    @HostAccess.Export
    public Object meta(String key) {
        return meta.get(key);
    }

    @HostAccess.Export
    public void setMeta(String key, Object value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            meta.remove(key);
        } else {
            meta.put(key, ScriptSnapshots.immutableValue(value));
        }
    }

    @HostAccess.Export
    public Map<String, Object> meta() {
        return ScriptSnapshots.immutableMap(meta);
    }

    void commitTo(EmakiAttributeDamageEvent event) {
        if (event == null) {
            return;
        }
        event.setFinalDamage(finalDamage);
        event.setCancelled(cancelled);
    }

    private static EntityView entityView(DamageContextVariables variables, String prefix, AttributeSnapshot snapshot) {
        String name = variables == null ? "" : variables.string(prefix + "_name", variables.string(prefix, ""));
        String uuid = variables == null ? "" : variables.string(prefix + "_uuid", "");
        String type = variables == null ? "" : variables.string(prefix + "_type", "").toLowerCase(Locale.ROOT);
        double health = variables == null ? 0D : variables.getDouble(prefix + "_health", 0D);
        double maxHealth = variables == null ? 0D : variables.getDouble(prefix + "_max_health", 0D);
        boolean exists = Texts.isNotBlank(uuid) || Texts.isNotBlank(type) || Texts.isNotBlank(name);
        boolean player = "player".equals(type);
        return new EntityView(
                exists,
                exists || snapshot != null,
                player,
                name,
                uuid,
                type,
                new WorldView(false, ""),
                Map.of(),
                health,
                maxHealth
        );
    }

    private static double attributeValue(Map<String, Double> attributes, String attributeId) {
        if (attributes == null || Texts.isBlank(attributeId)) {
            return 0D;
        }
        return attributes.getOrDefault(Texts.normalizeId(attributeId), 0D);
    }

    private static Map<String, Double> immutableDoubleMap(Map<String, Double> source) {
        return source == null || source.isEmpty() ? Map.of() : Map.copyOf(new LinkedHashMap<>(source));
    }
}
