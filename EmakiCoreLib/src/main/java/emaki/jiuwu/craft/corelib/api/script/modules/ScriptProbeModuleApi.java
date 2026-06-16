package emaki.jiuwu.craft.corelib.api.script.modules;

import org.graalvm.polyglot.HostAccess;

public class ScriptProbeModuleApi {

    private final String serviceClassName;

    protected ScriptProbeModuleApi(String serviceClassName) {
        this.serviceClassName = serviceClassName;
    }

    @HostAccess.Export
    public boolean available() {
        return ScriptServiceApiSupport.available(serviceClassName);
    }

    @HostAccess.Export
    public String apiVersion() {
        return ScriptServiceApiSupport.service(serviceClassName)
                .map(service -> ScriptServiceApiSupport.invokeString(service, "apiVersion", new Class<?>[0]))
                .orElse("");
    }

    @HostAccess.Export
    public String pluginName() {
        return ScriptServiceApiSupport.service(serviceClassName)
                .map(service -> ScriptServiceApiSupport.invokeString(service, "pluginName", new Class<?>[0]))
                .orElse("");
    }

    @HostAccess.Export
    public boolean ready() {
        return ScriptServiceApiSupport.service(serviceClassName)
                .map(service -> ScriptServiceApiSupport.invokeBoolean(service, "isReady", new Class<?>[0]))
                .orElse(false);
    }
}
