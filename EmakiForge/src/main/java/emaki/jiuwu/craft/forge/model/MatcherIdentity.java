package emaki.jiuwu.craft.forge.model;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class MatcherIdentity {

    private static final String PREFIX = "matcher-";

    private MatcherIdentity() {
    }

    static String syntheticKey(Object matcherNode) {
        if (matcherNode == null) {
            return "";
        }
        Object plain = ConfigNodes.toPlainData(matcherNode);
        if (plain == null) {
            return "";
        }
        String rendered = String.valueOf(plain);
        if (Texts.isBlank(rendered)) {
            return "";
        }
        return PREFIX + Integer.toHexString(rendered.hashCode());
    }
}
