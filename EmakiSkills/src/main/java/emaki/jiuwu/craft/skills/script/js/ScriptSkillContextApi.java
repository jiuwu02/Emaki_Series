package emaki.jiuwu.craft.skills.script.js;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.api.script.ScriptServerApi.ScriptEntityApi;
import emaki.jiuwu.craft.corelib.api.script.modules.ScriptServiceApiSupport;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;
import emaki.jiuwu.craft.skills.model.ResolvedSkillParameters;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;

public final class ScriptSkillContextApi {

    private final SkillScriptContext context;
    @HostAccess.Export
    public final SkillStateApi state;

    public ScriptSkillContextApi(SkillScriptContext context) {
        this.context = context;
        this.state = new SkillStateApi(context);
    }

    public SkillScriptContext rawContext() {
        return context;
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
            context.putVariable(key, value);
        }
    }

    @HostAccess.Export
    public Map<String, String> variables() {
        return context == null ? Map.of() : context.variables();
    }

    @HostAccess.Export
    public ScriptEntityApi caster() {
        return new ScriptEntityApi(context == null ? null : context.caster());
    }

    @HostAccess.Export
    public ScriptEntityApi target() {
        return new ScriptEntityApi(context == null ? null : context.targetEntity());
    }

    @HostAccess.Export
    public boolean hasTarget() {
        return context != null && context.hasTarget();
    }

    @HostAccess.Export
    public void setTarget(ScriptEntityApi entity) {
        if (context != null) {
            Entity raw = entity == null ? null : entity.entity();
            context.setTarget(raw);
        }
    }

    @HostAccess.Export
    public Map<String, Object> targetLocation() {
        if (context == null || context.targetLocation() == null) {
            return Map.of();
        }
        Location location = context.targetLocation();
        return Map.of(
                "world", location.getWorld() == null ? "" : location.getWorld().getName(),
                "x", location.getX(),
                "y", location.getY(),
                "z", location.getZ(),
                "yaw", location.getYaw(),
                "pitch", location.getPitch()
        );
    }

    @HostAccess.Export
    public boolean runAction(String actionId, Map<String, ?> arguments) {
        if (!(context != null && context.plugin() instanceof EmakiSkillsPlugin plugin) || plugin.coreLib().actionExecutor() == null) {
            return false;
        }
        if (Texts.isBlank(actionId)) {
            return false;
        }
        ActionContext actionContext = ActionContext.create(plugin, context.caster(), "skill_js", false)
                .withPlaceholders(context.variables())
                .withAttributes(skillAttributes());
        Map<String, String> resolved = stringMap(arguments);
        ActionResult result = plugin.coreLib().actionExecutor().execute(actionContext, actionId, resolved).join();
        return result != null && result.success() && !result.skipped();
    }

    @HostAccess.Export
    public boolean runActionLine(String line) {
        if (!(context != null && context.plugin() instanceof EmakiSkillsPlugin plugin) || plugin.coreLib().actionExecutor() == null) {
            return false;
        }
        if (Texts.isBlank(line)) {
            return false;
        }
        ActionContext actionContext = ActionContext.create(plugin, context.caster(), "skill_js", false)
                .withPlaceholders(context.variables())
                .withAttributes(skillAttributes());
        return plugin.coreLib().actionExecutor().executeAll(actionContext, java.util.List.of(line), true).join().success();
    }

    @HostAccess.Export
    public boolean castMythic(String mythicSkillId) {
        return castMythic(mythicSkillId, Map.of());
    }

    @HostAccess.Export
    public boolean castMythic(String mythicSkillId, Map<String, ?> parameters) {
        if (!(context != null && context.plugin() instanceof EmakiSkillsPlugin plugin) || plugin.mythicSkillCastService() == null) {
            return false;
        }
        Map<String, String> resolved = stringMap(parameters);
        TriggerInvocation invocation = context.invocation() instanceof TriggerInvocation triggerInvocation ? triggerInvocation : null;
        return plugin.mythicSkillCastService().cast(context.caster(), mythicSkillId, invocation, new ResolvedSkillParameters(resolved));
    }

    @HostAccess.Export
    public boolean applyDamage(ScriptEntityApi target, String damageTypeId, double baseDamage) {
        return applyDamage(target, damageTypeId, baseDamage, Map.of());
    }

    @HostAccess.Export
    public boolean applyDamage(ScriptEntityApi target, String damageTypeId, double baseDamage, Map<String, ?> damageContext) {
        if (!(context != null && context.plugin() instanceof EmakiSkillsPlugin plugin)) {
            return false;
        }
        LivingEntity attacker = context.caster() instanceof LivingEntity livingAttacker ? livingAttacker : null;
        ScriptEntityApi resolvedTarget = target == null ? target() : target;
        LivingEntity targetEntity = resolvedTarget != null && resolvedTarget.entity() instanceof LivingEntity livingTarget ? livingTarget : null;
        if (targetEntity == null) {
            return false;
        }
        Map<String, ?> mergedContext = mergeSkillDamageContext(plugin, damageContext);
        return ScriptServiceApiSupport.service("emaki.jiuwu.craft.attribute.service.AttributeServiceFacade")
                .map(service -> ScriptServiceApiSupport.invokeBoolean(service,
                        "applyDamage",
                        new Class<?>[] { LivingEntity.class, LivingEntity.class, String.class, double.class, Map.class },
                        attacker,
                        targetEntity,
                        damageTypeId,
                        baseDamage,
                        mergedContext))
                .orElse(false);
    }

    private Map<String, Object> skillAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (context == null) {
            return attributes;
        }
        attributes.put("skill_id", context.skillId());
        attributes.put("trigger_id", context.triggerId());
        attributes.put("target", context.targetEntity());
        attributes.put("target_location", context.targetLocation());
        return attributes;
    }

    private Map<String, ?> mergeSkillDamageContext(EmakiSkillsPlugin plugin, Map<String, ?> values) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("source", "skills_js");
        merged.put("skill_id", context == null ? "" : context.skillId());
        merged.put("trigger_id", context == null ? "" : context.triggerId());
        if (plugin != null) {
            merged.put("source_plugin", plugin.getName());
        }
        if (values != null) {
            merged.putAll(values);
        }
        return merged;
    }

    private Map<String, String> stringMap(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (entry.getKey() != null) {
                result.put(Texts.normalizeId(entry.getKey()), Texts.toStringSafe(entry.getValue()));
            }
        }
        return result;
    }

    public static final class SkillStateApi {

        private final SkillScriptContext context;

        SkillStateApi(SkillScriptContext context) {
            this.context = context;
        }

        @HostAccess.Export
        public Object get(String key) {
            return context == null ? null : context.sharedValue(key);
        }

        @HostAccess.Export
        public void set(String key, Object value) {
            if (context != null) {
                context.putSharedValue(key, value);
            }
        }

        @HostAccess.Export
        public boolean has(String key) {
            return context != null && context.sharedState().containsKey(key);
        }

        @HostAccess.Export
        public void remove(String key) {
            if (context != null && key != null) {
                context.sharedState().remove(key);
            }
        }
    }
}
