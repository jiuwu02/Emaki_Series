package emaki.jiuwu.craft.gem.script;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.gem.api.EmakiGemApi;

public final class ScriptGemModuleApi {

    @HostAccess.Export
    public boolean available() {
        return EmakiGemApi.available();
    }

    @HostAccess.Export
    public String apiVersion() {
        return EmakiGemApi.apiVersion();
    }

    @HostAccess.Export
    public String pluginName() {
        return EmakiGemApi.pluginName();
    }

    @HostAccess.Export
    public boolean ready() {
        return EmakiGemApi.isReady();
    }
}
