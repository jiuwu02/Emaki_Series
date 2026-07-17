package emaki.jiuwu.craft.corelib.api.script.modules;

import org.graalvm.polyglot.HostAccess;

public class ScriptProbeModuleApi {

    private final ScriptServiceApiSupport.ServiceSnapshot snapshot;

    protected ScriptProbeModuleApi(String serviceClassName) {
        this.snapshot = ScriptServiceApiSupport.serviceSnapshot(serviceClassName);
    }

    @HostAccess.Export
    public boolean available() {
        return snapshot.available();
    }

    @HostAccess.Export
    public String apiVersion() {
        return snapshot.apiVersion();
    }

    @HostAccess.Export
    public String pluginName() {
        return snapshot.pluginName();
    }

    @HostAccess.Export
    public boolean ready() {
        return snapshot.ready();
    }
}
