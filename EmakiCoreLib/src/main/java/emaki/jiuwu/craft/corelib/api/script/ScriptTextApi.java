package emaki.jiuwu.craft.corelib.api.script;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptTextApi {

    public String string(Object value) {
        return Texts.toStringSafe(value);
    }

    public boolean blank(String value) {
        return Texts.isBlank(value);
    }

    public boolean notBlank(String value) {
        return Texts.isNotBlank(value);
    }

    public String lower(String value) {
        return Texts.lower(value);
    }

    public String normalizeId(String value) {
        return Texts.normalizeId(value);
    }
}
