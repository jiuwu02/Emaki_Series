package emaki.jiuwu.craft.corelib.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.text.Texts;

public record ScriptInvocationRequest(Plugin sourcePlugin,
        ActionContext actionContext,
        String scriptPath,
        String functionName,
        List<Object> arguments,
        Map<String, Object> namedArguments,
        long timeoutMillis,
        boolean silent,
        Map<String, Object> moduleOverrides) {

    public ScriptInvocationRequest(Plugin sourcePlugin,
            ActionContext actionContext,
            String scriptPath,
            String functionName,
            List<Object> arguments,
            Map<String, Object> namedArguments,
            long timeoutMillis,
            boolean silent) {
        this(sourcePlugin, actionContext, scriptPath, functionName, arguments, namedArguments, timeoutMillis, silent, Map.of());
    }

    public ScriptInvocationRequest {
        scriptPath = Texts.trim(scriptPath);
        functionName = Texts.isBlank(functionName) ? "main" : Texts.trim(functionName);
        arguments = arguments == null ? List.of() : List.copyOf(new ArrayList<>(arguments));
        namedArguments = namedArguments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(namedArguments));
        moduleOverrides = moduleOverrides == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(moduleOverrides));
        sourcePlugin = sourcePlugin == null && actionContext != null ? actionContext.sourcePlugin() : sourcePlugin;
    }

    public static ScriptInvocationRequest of(Plugin sourcePlugin,
            ActionContext actionContext,
            String scriptPath,
            String functionName,
            List<Object> arguments,
            long timeoutMillis,
            boolean silent) {
        return new ScriptInvocationRequest(sourcePlugin, actionContext, scriptPath, functionName, arguments, Map.of(), timeoutMillis, silent);
    }
}
