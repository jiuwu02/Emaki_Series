package emaki.jiuwu.craft.corelib.script;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.text.Texts;

public record ScriptExecutionRequest(Plugin sourcePlugin,
        ActionContext actionContext,
        String scriptPath,
        String functionName,
        Map<String, Object> arguments,
        long timeoutMillis,
        boolean silent) {

    public ScriptExecutionRequest {
        scriptPath = Texts.trim(scriptPath);
        functionName = Texts.isBlank(functionName) ? "main" : Texts.trim(functionName);
        arguments = arguments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(arguments));
        sourcePlugin = sourcePlugin == null && actionContext != null ? actionContext.sourcePlugin() : sourcePlugin;
    }
}
