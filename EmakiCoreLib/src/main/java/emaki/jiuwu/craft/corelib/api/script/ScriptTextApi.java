package emaki.jiuwu.craft.corelib.api.script;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptTextApi {

    @HostAccess.Export
    public String string(Object value) {
        return Texts.toStringSafe(value);
    }

    @HostAccess.Export
    public boolean blank(String value) {
        return Texts.isBlank(value);
    }

    @HostAccess.Export
    public boolean notBlank(String value) {
        return Texts.isNotBlank(value);
    }

    @HostAccess.Export
    public String lower(String value) {
        return Texts.lower(value);
    }

    @HostAccess.Export
    public String normalizeId(String value) {
        return Texts.normalizeId(value);
    }
}
