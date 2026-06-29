package emaki.jiuwu.craft.corelib.script;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;

public record ScriptModuleContext(ActionContext actionContext,
        Map<String, Object> arguments,
        ActionExecutor actionExecutor,
        ScriptConfig config,
        String scriptPath,
        Plugin sourcePlugin,
        Map<String, Object> moduleOverrides) {

    public ScriptModuleContext(ActionContext actionContext,
            Map<String, Object> arguments,
            ActionExecutor actionExecutor,
            ScriptConfig config,
            String scriptPath,
            Plugin sourcePlugin) {
        this(actionContext, arguments, actionExecutor, config, scriptPath, sourcePlugin, Map.of());
    }

    public ScriptModuleContext {
        arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        moduleOverrides = moduleOverrides == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(moduleOverrides));
        config = config == null ? ScriptConfig.defaults() : config;
        sourcePlugin = sourcePlugin == null && actionContext != null ? actionContext.sourcePlugin() : sourcePlugin;
    }
}
