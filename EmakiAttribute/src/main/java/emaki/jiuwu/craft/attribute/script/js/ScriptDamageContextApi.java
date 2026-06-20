package emaki.jiuwu.craft.attribute.script.js;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.corelib.api.script.ScriptServerApi.ScriptEntityApi;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptDamageContextApi {

    private final DamageContext context;
    private final Map<String, Object> variables;
    private boolean cancelled;
    private double damage;
    private boolean damageSet;
    private boolean critical;
    private double recovery;

    public ScriptDamageContextApi(DamageContext context) {
        this.context = context == null ? DamageContext.empty() : context;
        this.variables = new LinkedHashMap<>(this.context.context());
    }

    @HostAccess.Export
    public ScriptEntityApi attacker() {
        return new ScriptEntityApi(context.attacker());
    }

    @HostAccess.Export
    public ScriptEntityApi target() {
        return new ScriptEntityApi(context.target());
    }

    @HostAccess.Export
    public ScriptEntityApi projectile() {
        return new ScriptEntityApi(context.projectile());
    }

    @HostAccess.Export
    public String damageTypeId() {
        return context.damageTypeId();
    }

    @HostAccess.Export
    public String cause() {
        return context.causeName();
    }

    @HostAccess.Export
    public double sourceDamage() {
        return context.sourceDamage();
    }

    @HostAccess.Export
    public double baseDamage() {
        return context.baseDamage();
    }

    @HostAccess.Export
    public double attackerAttribute(String attributeId) {
        return attributeValue(context.attackerSnapshot(), attributeId);
    }

    @HostAccess.Export
    public double targetAttribute(String attributeId) {
        return attributeValue(context.targetSnapshot(), attributeId);
    }

    @HostAccess.Export
    public Map<String, Double> attackerAttributes() {
        return context.attackerSnapshot().values();
    }

    @HostAccess.Export
    public Map<String, Double> targetAttributes() {
        return context.targetSnapshot().values();
    }

    @HostAccess.Export
    public Object variable(String key) {
        return variables.get(key);
    }

    @HostAccess.Export
    public void setVariable(String key, Object value) {
        if (Texts.isBlank(key)) {
            return;
        }
        if (value == null) {
            variables.remove(key);
        } else {
            variables.put(key, value);
        }
    }

    @HostAccess.Export
    public Map<String, Object> variables() {
        return Map.copyOf(variables);
    }

    @HostAccess.Export
    public void cancel() {
        cancelled = true;
        damage = 0D;
        damageSet = true;
    }

    @HostAccess.Export
    public boolean cancelled() {
        return cancelled;
    }

    @HostAccess.Export
    public void setDamage(double value) {
        damage = Math.max(0D, value);
        damageSet = true;
    }

    @HostAccess.Export
    public double damage() {
        return damageSet ? damage : context.baseDamage();
    }

    @HostAccess.Export
    public void setCritical(boolean value) {
        critical = value;
    }

    @HostAccess.Export
    public boolean critical() {
        return critical;
    }

    @HostAccess.Export
    public void setRecovery(double value) {
        recovery = Math.max(0D, value);
    }

    @HostAccess.Export
    public double recovery() {
        return recovery;
    }

    @HostAccess.Export
    public void healAttacker(double amount) {
        heal(context.attacker(), amount);
    }

    @HostAccess.Export
    public void healTarget(double amount) {
        heal(context.target(), amount);
    }

    public DamageResult toDamageResult(Object rawReturn) {
        Map<String, Object> output = asMap(rawReturn);
        boolean success = booleanValue(output.get("success"), true);
        if (!success || booleanValue(output.get("cancelled"), cancelled)) {
            return new DamageResult(context.damageTypeId(), 0D, critical, 0D, Map.of("script", 0D), context.withVariables(variables));
        }
        double finalDamage = number(output.get("damage"), number(output.get("finalDamage"), damage()));
        boolean resolvedCritical = booleanValue(output.get("critical"), critical);
        double resolvedRecovery = number(output.get("recovery"), recovery);
        if (resolvedRecovery > 0D) {
            heal(context.attacker(), resolvedRecovery);
        }
        Map<String, Double> stages = new LinkedHashMap<>();
        stages.put("script", Math.max(0D, finalDamage));
        Object rawStages = output.get("stageValues");
        if (!(rawStages instanceof Map<?, ?>)) {
            rawStages = output.get("stages");
        }
        if (rawStages instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    stages.put(Texts.normalizeId(entry.getKey().toString()), number(entry.getValue(), 0D));
                }
            }
        }
        return new DamageResult(context.damageTypeId(), finalDamage, resolvedCritical, 0D, stages, context.withVariables(variables));
    }

    private double attributeValue(AttributeSnapshot snapshot, String attributeId) {
        if (snapshot == null || Texts.isBlank(attributeId)) {
            return 0D;
        }
        return snapshot.values().getOrDefault(Texts.normalizeId(attributeId), 0D);
    }

    private void heal(LivingEntity entity, double amount) {
        if (entity == null || amount <= 0D) {
            return;
        }
        entity.setHealth(Math.max(0D, Math.min(entity.getMaxHealth(), entity.getHealth() + amount)));
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return result;
        }
        if (value instanceof Number number) {
            return Map.of("damage", number.doubleValue());
        }
        if (value instanceof Boolean bool) {
            return bool ? Map.of() : Map.of("cancelled", true);
        }
        return Map.of();
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return fallback;
    }

    private double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Texts.toStringSafe(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
