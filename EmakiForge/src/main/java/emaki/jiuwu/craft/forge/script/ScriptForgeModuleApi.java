package emaki.jiuwu.craft.forge.script;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.forge.api.EmakiForgeApi;

public final class ScriptForgeModuleApi {

    @HostAccess.Export
    public boolean available() {
        return EmakiForgeApi.available();
    }

    @HostAccess.Export
    public String apiVersion() {
        return EmakiForgeApi.apiVersion();
    }

    @HostAccess.Export
    public String pluginName() {
        return EmakiForgeApi.pluginName();
    }

    @HostAccess.Export
    public boolean ready() {
        return EmakiForgeApi.isReady();
    }
}
