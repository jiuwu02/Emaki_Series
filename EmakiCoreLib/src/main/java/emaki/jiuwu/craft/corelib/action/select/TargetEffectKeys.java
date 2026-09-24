package emaki.jiuwu.craft.corelib.action.select;

import emaki.jiuwu.craft.corelib.api.text.Texts;

final class TargetEffectKeys {

    private static final String DEFAULT_NAMESPACE = "minecraft:";

    private TargetEffectKeys() {
    }

    static String normalize(String raw) {
        String value = Texts.lower(raw);
        if (value.isEmpty()) {
            return "";
        }
        return value.indexOf(':') < 0 ? DEFAULT_NAMESPACE + value : value;
    }
}