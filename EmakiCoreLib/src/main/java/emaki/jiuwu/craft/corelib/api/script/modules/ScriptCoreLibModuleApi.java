package emaki.jiuwu.craft.corelib.api.script.modules;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;

public final class ScriptCoreLibModuleApi {

    @HostAccess.Export
    public boolean available() {
        return EmakiCoreLibApi.available();
    }

    @HostAccess.Export
    public String apiVersion() {
        return EmakiCoreLibApi.apiVersion();
    }

    @HostAccess.Export
    public String pluginName() {
        return EmakiCoreLibApi.pluginName();
    }

    @HostAccess.Export
    public boolean ready() {
        return EmakiCoreLibApi.isReady();
    }
}
