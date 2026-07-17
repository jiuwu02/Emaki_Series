package emaki.jiuwu.craft.attribute.script.js;

import emaki.jiuwu.craft.attribute.script.js.ScriptDamageContextApi;
import emaki.jiuwu.craft.attribute.script.js.ScriptDamageContextApi.MessageIntent;
import emaki.jiuwu.craft.attribute.script.js.ScriptDamageContextApi.ResultSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptEntitySnapshot.EntityView;
import emaki.jiuwu.craft.corelib.script.ScriptEntitySnapshot.WorldView;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptDamagePipelineRegistry {

    public static final String TARGET_HEAL_INTENT = "__emaki_script_target_heal";
    public static final String ATTACKER_HEAL_INTENT = "__emaki_script_attacker_heal";
    public static final String TARGET_MESSAGES_INTENT = "__emaki_script_target_messages";

    private final EmakiAttributePlugin plugin;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final Map<String, Pipeline> pipelines = new LinkedHashMap<>();

    public JavaScriptDamagePipelineRegistry(EmakiAttributePlugin plugin, JavaScriptService javaScriptService, ScriptConfig scriptConfig) {
        this.plugin = plugin;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
    }

    public void register(String id, String scriptPath, String functionName, long timeoutMillis) {
        String normalized = Texts.normalizeId(id);
        if (Texts.isBlank(normalized) || Texts.isBlank(scriptPath) || Texts.isBlank(functionName)) {
            return;
        }
        pipelines.put(normalized, new Pipeline(normalized, scriptPath, functionName, timeoutMillis));
    }

    public void unregister(String id) {
        pipelines.remove(Texts.normalizeId(id));
    }

    public void clear() {
        pipelines.clear();
    }

    public int size() {
        return pipelines.size();
    }

    public boolean handles(String damageTypeId) {
        return pipelines.containsKey(Texts.normalizeId(damageTypeId));
    }

    public DamageResult resolve(DamageContext context) {
        double roll = context == null || context.variables() == null
                ? 0D
                : context.variables().getDouble("roll", 0D);
        return resolve(context, roll);
    }

    public DamageResult resolve(DamageContext context, double originalRoll) {
        if (context == null || javaScriptService == null || !javaScriptService.enabled()) {
            return null;
        }
        Pipeline pipeline = pipelines.get(Texts.normalizeId(context.damageTypeId()));
        if (pipeline == null) {
            return null;
        }
        DamageContextVariables variables = context.variables();
        ScriptDamageContextApi api = new ScriptDamageContextApi(
                entityView(variables, "attacker", context.attackerSnapshot()),
                entityView(variables, "target", context.targetSnapshot()),
                entityView(variables, "projectile", null),
                context.cause() == null ? "" : context.cause().name(),
                context.damageTypeId(),
                context.sourceDamage(),
                context.baseDamage(),
                originalRoll,
                context.attackerSnapshot() == null ? Map.of() : context.attackerSnapshot().values(),
                context.targetSnapshot() == null ? Map.of() : context.targetSnapshot().values(),
                variables == null ? Map.of() : variables.asMap(),
                isPlayer(variables, "target")
        );
        ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                plugin,
                null,
                pipeline.scriptPath(),
                pipeline.functionName(),
                java.util.List.of(api),
                ScriptSnapshots.immutableMap(Map.of("pipeline", pipeline.id(), "damage_type", context.damageTypeId())),
                scriptConfig.clampTimeoutMillis(pipeline.timeoutMillis()),
                true
        ));
        if (result == null || !result.success() || result.returnValue() == null) {
            return null;
        }
        ResultSnapshot snapshot = api.toResultSnapshot(result.returnValue());
        DamageContextVariables.Builder outputVariables = DamageContextVariables.builder()
                .putAll(snapshot.variables());
        if (api.healingAmount() > 0D) {
            outputVariables.put(TARGET_HEAL_INTENT, api.healingAmount());
        }
        if (api.attackerHealingAmount() > 0D) {
            outputVariables.put(ATTACKER_HEAL_INTENT, api.attackerHealingAmount());
        }
        List<Map<String, Object>> messages = messageIntents(api.messages());
        if (!messages.isEmpty()) {
            outputVariables.put(TARGET_MESSAGES_INTENT, messages);
        }
        DamageContext resolvedContext = context.withVariables(outputVariables.build());
        return new DamageResult(
                snapshot.damageTypeId(),
                snapshot.finalDamage(),
                snapshot.critical(),
                snapshot.roll(),
                snapshot.stageValues(),
                resolvedContext
        );
    }

    private static List<Map<String, Object>> messageIntents(List<MessageIntent> intents) {
        if (intents == null || intents.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> messages = new ArrayList<>(intents.size());
        for (MessageIntent intent : intents) {
            if (intent == null || Texts.isBlank(intent.message())) {
                continue;
            }
            messages.add(ScriptSnapshots.immutableMap(Map.of(
                    "message", intent.message(),
                    "placeholders", intent.placeholders()
            )));
        }
        return List.copyOf(messages);
    }

    private static EntityView entityView(DamageContextVariables variables, String prefix, AttributeSnapshot snapshot) {
        String name = variables == null ? "" : variables.string(prefix + "_name", variables.string(prefix, ""));
        String uuid = variables == null ? "" : variables.string(prefix + "_uuid", "");
        String type = variables == null ? "" : variables.string(prefix + "_type", "").toLowerCase(Locale.ROOT);
        double health = variables == null ? 0D : variables.getDouble(prefix + "_health", 0D);
        double maxHealth = variables == null ? 0D : variables.getDouble(prefix + "_max_health", 0D);
        boolean exists = Texts.isNotBlank(uuid) || Texts.isNotBlank(type) || Texts.isNotBlank(name);
        return new EntityView(
                exists,
                exists || snapshot != null,
                "player".equals(type),
                name,
                uuid,
                type,
                new WorldView(false, ""),
                Map.of(),
                health,
                maxHealth
        );
    }

    private static boolean isPlayer(DamageContextVariables variables, String prefix) {
        return variables != null && "player".equalsIgnoreCase(variables.string(prefix + "_type", ""));
    }

    private record Pipeline(String id, String scriptPath, String functionName, long timeoutMillis) {
    }
}
