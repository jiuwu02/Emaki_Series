package emaki.jiuwu.craft.corelib.api.script.modules;

import org.graalvm.polyglot.HostAccess;

public final class ScriptCoreLibModuleApi {

    private static final String SERVICE = "emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi";

    @HostAccess.Export
    public boolean available() {
        return ScriptServiceApiSupport.available(SERVICE);
    }

    @HostAccess.Export
    public String apiVersion() {
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.invokeString(service, "apiVersion", new Class<?>[0]))
                .orElse("");
    }

    @HostAccess.Export
    public String pluginName() {
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.invokeString(service, "pluginName", new Class<?>[0]))
                .orElse("");
    }

    @HostAccess.Export
    public boolean ready() {
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.invokeBoolean(service, "isReady", new Class<?>[0]))
                .orElse(false);
    }
}
