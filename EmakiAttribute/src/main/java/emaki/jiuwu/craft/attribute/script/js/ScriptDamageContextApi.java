package emaki.jiuwu.craft.attribute.script.js;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.corelib.api.script.ScriptServerApi.ScriptEntityApi;
import emaki.jiuwu.craft.corelib.script.ScriptEntitySnapshot;
import emaki.jiuwu.craft.corelib.script.ScriptEntitySnapshot.EntityView;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.text.Texts;


public final class ScriptDamageContextApi {

    private final EntityView source;
    private final EntityView target;
    private final EntityView projectile;
    private final ScriptEntityApi sourceApi;
    private final ScriptEntityApi targetApi;
    private final ScriptEntityApi projectileApi;
    private DamageContext legacyContext;
    private final CauseView cause;
    private final String damageType;
    private final double sourceDamage;
    private final double baseDamage;
    private final double originalRoll;
    private final Map<String, Double> attackerSnapshot;
    private final Map<String, Double> targetSnapshot;
    private final Map<String, Object> variables;
    private final boolean targetPlayer;
    private final Map<String, Boolean> flags = new LinkedHashMap<>();
    private final List<MessageIntent> messages = new ArrayList<>();
    private double damage;
    private double targetHealingAmount;
    private double attackerHealingAmount;
    private double recovery;
    private boolean cancelled;
    private boolean critical;

    public ScriptDamageContextApi(DamageContext context) {
        this(
                ScriptEntitySnapshot.capture(context == null ? null : context.attacker()),
                ScriptEntitySnapshot.capture(context == null ? null : context.target()),
                ScriptEntitySnapshot.capture(context == null ? null : context.projectile()),
                context == null ? "" : context.causeName(),
                context == null ? "" : context.damageTypeId(),
                context == null ? 0D : context.sourceDamage(),
                context == null ? 0D : context.baseDamage(),
                context == null ? Map.of() : context.attackerSnapshot().values(),
                context == null ? Map.of() : context.targetSnapshot().values(),
                context == null ? Map.of() : context.variables().asMap(),
                context != null && context.target() instanceof org.bukkit.entity.Player
        );
        this.legacyContext = context == null ? DamageContext.empty() : context;
    }

    public ScriptDamageContextApi(
            EntityView source,
            EntityView target,
            EntityView projectile,
            String cause,
            String damageType,
            double sourceDamage,
            double damage,
            Map<String, Double> attackerSnapshot,
            Map<String, Double> targetSnapshot,
            Map<String, ?> variables,
            boolean targetPlayer) {
        this(source, target, projectile, cause, damageType, sourceDamage, damage,
                numberValue(variables == null ? null : variables.get("roll"), 0D),
                attackerSnapshot, targetSnapshot, variables, targetPlayer);
    }

    public ScriptDamageContextApi(
            EntityView source,
            EntityView target,
            EntityView projectile,
            String cause,
            String damageType,
            double sourceDamage,
            double damage,
            double originalRoll,
            Map<String, Double> attackerSnapshot,
            Map<String, Double> targetSnapshot,
            Map<String, ?> variables,
            boolean targetPlayer) {
        this.source = source == null ? EntityView.empty() : source;
        this.target = target == null ? EntityView.empty() : target;
        this.projectile = projectile == null ? EntityView.empty() : projectile;
        this.sourceApi = new ScriptEntityApi(this.source);
        this.targetApi = new ScriptEntityApi(this.target);
        this.projectileApi = new ScriptEntityApi(this.projectile);
        this.cause = new CauseView(cause);
        this.damageType = damageType == null ? "" : damageType;
        this.sourceDamage = sourceDamage;
        this.baseDamage = Math.max(0D, damage);
        this.originalRoll = Double.isFinite(originalRoll) ? originalRoll : 0D;
        this.damage = this.baseDamage;
        this.attackerSnapshot = immutableDoubleMap(attackerSnapshot);
        this.targetSnapshot = immutableDoubleMap(targetSnapshot);
        this.variables = variables == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(ScriptSnapshots.immutableMap(variables));
        this.targetPlayer = targetPlayer;
    }

    @HostAccess.Export
    public EntityView source() {
        return source;
    }

    @HostAccess.Export
    public ScriptEntityApi attacker() {
        return sourceApi;
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
        return source;
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
    public String cause() {
        return cause.name();
    }

    @HostAccess.Export
    public CauseView causeView() {
        return cause;
    }

    @HostAccess.Export
    public String damageType() {
        return damageType;
    }

    @HostAccess.Export
    public String damageTypeId() {
        return damageType;
    }

    @HostAccess.Export
    public double sourceDamage() {
        return sourceDamage;
    }

    @HostAccess.Export
    public double baseDamage() {
        return baseDamage;
    }

    @HostAccess.Export
    public double damage() {
        return damage;
    }

    @HostAccess.Export
    public void setDamage(double damage) {
        this.damage = Math.max(0D, damage);
    }

    @HostAccess.Export
    public double addDamage(double amount) {
        setDamage(this.damage + amount);
        return this.damage;
    }

    @HostAccess.Export
    public double multiplyDamage(double multiplier) {
        setDamage(this.damage * multiplier);
        return this.damage;
    }

    @HostAccess.Export
    public Map<String, Double> attackerSnapshot() {
        return attackerSnapshot;
    }

    @HostAccess.Export
    public Map<String, Double> attackerAttributes() {
        return attackerSnapshot;
    }

    @HostAccess.Export
    public double attackerAttribute(String attributeId) {
        return attributeValue(attackerSnapshot, attributeId);
    }

    @HostAccess.Export
    public Map<String, Double> targetSnapshot() {
        return targetSnapshot;
    }

    @HostAccess.Export
    public Map<String, Double> targetAttributes() {
        return targetSnapshot;
    }

    @HostAccess.Export
    public double targetAttribute(String attributeId) {
        return attributeValue(targetSnapshot, attributeId);
    }

    @HostAccess.Export
    public Map<String, Object> variables() {
        return ScriptSnapshots.immutableMap(variables);
    }

    @HostAccess.Export
    public Object variable(String key) {
        return Texts.isBlank(key) ? null : variables.get(Texts.normalizeId(key));
    }

    @HostAccess.Export
    public void setVariable(String key, Object value) {
        if (Texts.isBlank(key)) {
            return;
        }
        String normalized = Texts.normalizeId(key);
        if (value == null) {
            variables.remove(normalized);
        } else {
            variables.put(normalized, ScriptSnapshots.immutableValue(value));
        }
    }

    @HostAccess.Export
    public double variable(String key, double fallback) {
        Object value = variables.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @HostAccess.Export
    public void flag(String key, boolean value) {
        if (!Texts.isBlank(key)) {
            flags.put(Texts.trim(key), value);
            if ("critical".equalsIgnoreCase(Texts.trim(key))) {
                critical = value;
            }
        }
    }

    @HostAccess.Export
    public boolean flag(String key) {
        return Boolean.TRUE.equals(flags.get(Texts.trim(key)));
    }

    @HostAccess.Export
    public void cancel() {
        cancelled = true;
        setDamage(0D);
    }

    @HostAccess.Export
    public boolean cancelled() {
        return cancelled;
    }

    @HostAccess.Export
    public void setCritical(boolean value) {
        critical = value;
        flag("critical", value);
    }

    @HostAccess.Export
    public boolean critical() {
        return critical;
    }

    @HostAccess.Export
    public void setRecovery(double value) {
        recovery = finitePositive(value);
    }

    @HostAccess.Export
    public double recovery() {
        return recovery;
    }

    @HostAccess.Export
    public void heal(double amount) {
        healTarget(amount);
    }

    @HostAccess.Export
    public void healAttacker(double amount) {
        attackerHealingAmount += finitePositive(amount);
    }

    @HostAccess.Export
    public void healTarget(double amount) {
        targetHealingAmount += finitePositive(amount);
    }

    @HostAccess.Export
    public void message(String message, Map<String, ?> placeholders) {
        if (targetPlayer && !Texts.isBlank(message)) {
            messages.add(new MessageIntent(message, ScriptSnapshots.immutableMap(placeholders)));
        }
    }

    public double healingAmount() {
        return targetHealingAmount;
    }

    public double attackerHealingAmount() {
        return attackerHealingAmount + recovery;
    }

    public List<MessageIntent> messages() {
        return List.copyOf(messages);
    }

    public ResultSnapshot toResultSnapshot(Object returnValue) {
        if (returnValue instanceof Number number) {
            setDamage(number.doubleValue());
        } else if (returnValue instanceof Boolean bool && !bool) {
            cancel();
        }
        String resolvedDamageType = damageType;
        boolean resolvedCritical = critical || flag("critical");
        double resolvedRoll = originalRoll;
        Map<String, Double> stageValues = new LinkedHashMap<>();
        Map<String, Object> outputVariables = new LinkedHashMap<>(variables);
        if (returnValue instanceof Map<?, ?> map) {
            if (!booleanValue(map.get("success"), true) || booleanValue(map.get("cancelled"), cancelled)) {
                cancel();
            }
            Object damageValue = firstValue(map, "damage", "finalDamage", "final_damage");
            if (damageValue != null) {
                setDamage(numberValue(damageValue, damage));
            }
            Object typeValue = firstValue(map, "damage_type", "damageTypeId", "damage_type_id");
            if (typeValue != null && !Texts.isBlank(String.valueOf(typeValue))) {
                resolvedDamageType = Texts.normalizeId(String.valueOf(typeValue));
            }
            resolvedCritical = booleanValue(map.get("critical"), resolvedCritical);
            resolvedRoll = numberValue(map.get("roll"), resolvedRoll);
            Object multiplierValue = firstValue(map, "critical_multiplier", "criticalMultiplier");
            if (multiplierValue != null) {
                outputVariables.put("critical_multiplier", Math.max(0D, numberValue(multiplierValue, 1D)));
            }
            Map<String, Double> defenseTrace = doubleMap(firstValue(map, "defense_trace", "defenseTrace"));
            if (!defenseTrace.isEmpty()) {
                outputVariables.put("defense_trace", defenseTrace);
            }
            Map<String, Double> returnedStages = doubleMap(firstValue(map, "stage_values", "stageValues", "stages"));
            stageValues.putAll(returnedStages);
            Object contextValue = map.get("context");
            if (contextValue instanceof Map<?, ?> contextMap) {
                for (Map.Entry<?, ?> entry : contextMap.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        outputVariables.put(Texts.normalizeId(String.valueOf(entry.getKey())),
                                ScriptSnapshots.immutableValue(entry.getValue()));
                    }
                }
            }
            setRecovery(numberValue(map.get("recovery"), recovery));
        }
        double finalDamage = cancelled ? 0D : damage;
        stageValues.putIfAbsent("script", finalDamage);
        return new ResultSnapshot(resolvedDamageType, finalDamage, resolvedCritical, resolvedRoll, stageValues, outputVariables);
    }

    public DamageResult toDamageResult(Object returnValue) {
        ResultSnapshot snapshot = toResultSnapshot(returnValue);
        DamageContext baseContext = legacyContext == null ? DamageContext.empty() : legacyContext;
        return new DamageResult(
                snapshot.damageTypeId(),
                snapshot.finalDamage(),
                snapshot.critical(),
                snapshot.roll(),
                snapshot.stageValues(),
                baseContext.withVariables(snapshot.variables())
        );
    }

    private static Map<String, Double> immutableDoubleMap(Map<String, Double> source) {
        return source == null || source.isEmpty() ? Map.of() : Map.copyOf(new LinkedHashMap<>(source));
    }

    private static double attributeValue(Map<String, Double> snapshot, String attributeId) {
        if (snapshot == null || Texts.isBlank(attributeId)) {
            return 0D;
        }
        return snapshot.getOrDefault(Texts.normalizeId(attributeId), 0D);
    }

    private static double finitePositive(double value) {
        return Double.isFinite(value) ? Math.max(0D, value) : 0D;
    }

    private static Object firstValue(Map<?, ?> map, String... keys) {
        if (map == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static Map<String, Double> doubleMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Double> parsed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            double number = numberValue(entry.getValue(), Double.NaN);
            if (Double.isFinite(number)) {
                parsed.put(Texts.normalizeId(String.valueOf(entry.getKey())), number);
            }
        }
        return parsed.isEmpty() ? Map.of() : Map.copyOf(parsed);
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static double numberValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public record CauseView(String name) {

        public CauseView {
            name = name == null ? "" : name;
        }

        @Override
        @HostAccess.Export
        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public record ResultSnapshot(
            String damageTypeId,
            double finalDamage,
            boolean critical,
            double roll,
            Map<String, Double> stageValues,
            Map<String, Object> variables) {

        public ResultSnapshot {
            damageTypeId = damageTypeId == null ? "" : damageTypeId;
            finalDamage = Math.max(0D, finalDamage);
            stageValues = stageValues == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stageValues));
            variables = variables == null ? Map.of() : ScriptSnapshots.immutableMap(variables);
        }
    }

    public record MessageIntent(String message, Map<String, Object> placeholders) {

        public MessageIntent {
            message = message == null ? "" : message;
            placeholders = placeholders == null ? Map.of() : ScriptSnapshots.immutableMap(placeholders);
        }
    }
}
