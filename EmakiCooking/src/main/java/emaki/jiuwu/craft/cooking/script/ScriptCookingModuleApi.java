package emaki.jiuwu.craft.cooking.script;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.cooking.api.EmakiCookingApi;

public final class ScriptCookingModuleApi {

    @HostAccess.Export
    public boolean available() {
        return EmakiCookingApi.available();
    }

    @HostAccess.Export
    public String apiVersion() {
        return EmakiCookingApi.apiVersion();
    }

    @HostAccess.Export
    public String pluginName() {
        return EmakiCookingApi.pluginName();
    }

    @HostAccess.Export
    public boolean ready() {
        return EmakiCookingApi.isReady();
    }
}
