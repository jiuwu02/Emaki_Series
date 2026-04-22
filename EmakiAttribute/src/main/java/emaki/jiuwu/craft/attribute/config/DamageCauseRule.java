package emaki.jiuwu.craft.attribute.config;

import java.util.Map;

import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record DamageCauseRule(String cause,
        String damageTypeId,
        Double damage,
        boolean enabled,
        DamageContextVariables context) {

    public DamageCauseRule     {
        cause = Texts.normalizeId(cause);
        damageTypeId = Texts.normalizeId(damageTypeId);
        context = context == null ? DamageContextVariables.empty() : context;
    }

    public static DamageCauseRule fromMap(Object raw, String defaultDamageType) {
        if (raw == null) {
            return null;
        }
        String cause;
        if (raw instanceof String string) {
            cause = string;
        } else {
            cause = ConfigNodes.string(raw, "cause", null);
        }
        cause = Texts.normalizeId(cause);
        if (Texts.isBlank(cause)) {
            return null;
        }
        String damageTypeId = ConfigNodes.string(raw, "damage_type", defaultDamageType);
        Double damage = Numbers.tryParseDouble(ConfigNodes.get(raw, "damage"), null);
        boolean enabled = ConfigNodes.contains(raw, "enabled") ? ConfigNodes.bool(raw, "enabled", true) : true;
        DamageContextVariables.Builder context = DamageContextVariables.builder();
        Object nestedContext = ConfigNodes.get(raw, "context");
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(nestedContext).entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            context.put(entry.getKey(), ConfigNodes.toPlainData(entry.getValue()));
        }
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (isReservedKey(entry.getKey())) {
                continue;
            }
            context.put(entry.getKey(), ConfigNodes.toPlainData(entry.getValue()));
        }
        return new DamageCauseRule(cause, damageTypeId, damage, enabled, context.build());
    }

    public boolean matches(String candidate) {
        if (!enabled || Texts.isBlank(candidate)) {
            return false;
        }
        return cause.equals(Texts.normalizeId(candidate));
    }

    public boolean hasDamageType() {
        return !Texts.isBlank(damageTypeId);
    }

    public double resolveDamage(double fallback) {
        return damage == null ? fallback : Math.max(0D, damage);
    }

    private static boolean isReservedKey(String key) {
        String normalized = Texts.normalizeId(key);
        return normalized.equals("cause")
                || normalized.equals("damage_type")
                || normalized.equals("damage")
                || normalized.equals("enabled")
                || normalized.equals("context");
    }
}

