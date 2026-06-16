package emaki.jiuwu.craft.corelib.script;

import org.graalvm.polyglot.HostAccess;

public final class UnavailableScriptModuleApi {

    private final String id;

    public UnavailableScriptModuleApi(String id) {
        this.id = id == null ? "" : id;
    }

    @HostAccess.Export
    public boolean available() {
        return false;
    }

    @HostAccess.Export
    public String id() {
        return id;
    }
}
