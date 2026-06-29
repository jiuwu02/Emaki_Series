package emaki.jiuwu.craft.attribute.script.js;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptDamagePipelineRegistry {

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

    public DamageResult resolve(DamageContext context) {
        if (context == null || javaScriptService == null || !javaScriptService.enabled()) {
            return null;
        }
        Pipeline pipeline = pipelines.get(Texts.normalizeId(context.damageTypeId()));
        if (pipeline == null) {
            return null;
        }
        ScriptDamageContextApi api = new ScriptDamageContextApi(context);
        ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                plugin,
                null,
                pipeline.scriptPath(),
                pipeline.functionName(),
                java.util.List.of(api),
                Map.of("pipeline", pipeline.id(), "damage_type", context.damageTypeId()),
                scriptConfig.clampTimeoutMillis(pipeline.timeoutMillis()),
                true
        ));
        if (result == null || !result.success() || result.returnValue() == null) {
            return null;
        }
        return api.toDamageResult(result.returnValue());
    }

    private record Pipeline(String id, String scriptPath, String functionName, long timeoutMillis) {
    }
}
