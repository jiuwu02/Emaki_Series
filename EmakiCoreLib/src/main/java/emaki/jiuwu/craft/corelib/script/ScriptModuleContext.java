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
        actionContext = detachedContext(actionContext);
        @SuppressWarnings("unchecked")
        Map<String, Object> safeArguments = (Map<String, Object>) ScriptHostObjectProxy.snapshotValue(
                arguments == null ? Map.of() : arguments
        );
        arguments = Collections.unmodifiableMap(new LinkedHashMap<>(safeArguments));
        @SuppressWarnings("unchecked")
        Map<String, Object> safeOverrides = (Map<String, Object>) ScriptHostObjectProxy.wrapIfExported(
                moduleOverrides == null ? Map.of() : moduleOverrides
        );
        moduleOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(safeOverrides));
        actionExecutor = null;
        config = config == null ? ScriptConfig.defaults() : config;
        sourcePlugin = null;
    }

    private static ActionContext detachedContext(ActionContext context) {
        if (context == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) ScriptHostObjectProxy.snapshotValue(context.attributes());
        return new ActionContext(
                null,
                null,
                context.phase(),
                context.silent(),
                context.placeholders(),
                attributes
        );
    }
}
