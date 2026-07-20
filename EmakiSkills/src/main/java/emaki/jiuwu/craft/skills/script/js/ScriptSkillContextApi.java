package emaki.jiuwu.craft.skills.script.js;

import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.api.script.ScriptServerApi.ScriptEntityApi;
import emaki.jiuwu.craft.corelib.script.ScriptEntitySnapshot;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.script.SkillScriptIntentExecutor;


public final class ScriptSkillContextApi {

    private final SkillScriptIntentExecutor.WorkerContext context;
    @HostAccess.Export
    public final SkillStateApi state;

    public ScriptSkillContextApi(SkillScriptIntentExecutor.WorkerContext context) {
        this.context = context;
        this.state = new SkillStateApi(context);
    }

    @HostAccess.Export
    public String skillId() {
        return context == null ? "" : context.skillId();
    }

    @HostAccess.Export
    public String triggerId() {
        return context == null ? "" : context.triggerId();
    }

    @HostAccess.Export
    public String variable(String key) {
        String value = context == null ? null : context.variable(key);
        return value == null ? "" : value;
    }

    @HostAccess.Export
    public void setVariable(String key, Object value) {
        if (context != null) {
            context.setVariable(key, value);
        }
    }

    @HostAccess.Export
    public Map<String, String> variables() {
        return context == null ? Map.of() : context.variables();
    }

    @HostAccess.Export
    public ScriptEntitySnapshot.EntityView caster() {
        return context == null ? ScriptEntitySnapshot.EntityView.empty() : context.caster();
    }

    @HostAccess.Export
    public ScriptEntitySnapshot.EntityView target() {
        return context == null ? ScriptEntitySnapshot.EntityView.empty() : context.target();
    }

    @HostAccess.Export
    public boolean hasTarget() {
        return context != null && context.hasTarget();
    }

    @HostAccess.Export
    public void setTarget(Object entity) {
        if (context != null) {
            context.setTarget(entityUuid(entity));
        }
    }

    @HostAccess.Export
    public Map<String, Object> targetLocation() {
        return context == null ? Map.of() : context.targetLocation();
    }

    @HostAccess.Export
    public boolean runAction(String actionId, Map<String, ?> arguments) {
        return context != null && context.runAction(actionId, stringMap(arguments));
    }

    @HostAccess.Export
    public boolean runActionLine(String line) {
        return context != null && context.runActionLine(line);
    }

    @HostAccess.Export
    public boolean castMythic(String mythicSkillId) {
        return castMythic(mythicSkillId, Map.of());
    }

    @HostAccess.Export
    public boolean castMythic(String mythicSkillId, Map<String, ?> parameters) {
        return context != null && context.castMythic(mythicSkillId, stringMap(parameters));
    }

    @HostAccess.Export
    public boolean applyDamage(Object target, String damageTypeId, double baseDamage) {
        return applyDamage(target, damageTypeId, baseDamage, Map.of());
    }

    @HostAccess.Export
    public boolean applyDamage(Object target,
            String damageTypeId,
            double baseDamage,
            Map<String, ?> damageContext) {
        if (context == null) {
            return false;
        }
        String targetUuid = entityUuid(target);
        if (Texts.isBlank(targetUuid)) {
            targetUuid = context.target().uuid();
        }
        return context.applyDamage(targetUuid, damageTypeId, baseDamage, objectMap(damageContext));
    }

    private static String entityUuid(Object entity) {
        if (entity instanceof ScriptEntitySnapshot.EntityView view) {
            return view.uuid();
        }
        if (entity instanceof ScriptEntityApi api) {
            return api.uuid();
        }
        if (entity instanceof Map<?, ?> map) {
            Object uuid = map.get("uuid");
            return uuid == null ? "" : Texts.toStringSafe(uuid);
        }
        return entity instanceof String value ? value : "";
    }

    private static Map<String, String> stringMap(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (entry.getKey() != null) {
                result.put(Texts.normalizeId(entry.getKey()), Texts.toStringSafe(entry.getValue()));
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, Object> objectMap(Map<String, ?> values) {
        return values == null || values.isEmpty() ? Map.of() : ScriptSnapshots.immutableMap(values);
    }

    public static final class SkillStateApi {

        private final SkillScriptIntentExecutor.WorkerContext context;

        SkillStateApi(SkillScriptIntentExecutor.WorkerContext context) {
            this.context = context;
        }

        @HostAccess.Export
        public Object get(String key) {
            return context == null ? null : context.sharedValue(key);
        }

        @HostAccess.Export
        public void set(String key, Object value) {
            if (context != null) {
                context.setSharedValue(key, value);
            }
        }

        @HostAccess.Export
        public boolean has(String key) {
            return context != null && context.sharedState().containsKey(key);
        }

        @HostAccess.Export
        public void remove(String key) {
            if (context != null) {
                context.setSharedValue(key, null);
            }
        }
    }
}
