package emaki.jiuwu.craft.corelib.matcher;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class MatcherDigest {

    public static final String PREFIX = "matcher-";

    private MatcherDigest() {
    }

    public static @NotNull String of(@Nullable Object matcherNode) {
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

    public static boolean derived(@Nullable String identity) {
        return Texts.toStringSafe(identity).startsWith(PREFIX);
    }
}
