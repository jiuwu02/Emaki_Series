package emaki.jiuwu.craft.structure.config;

import java.util.regex.Pattern;

public final class NameRules {

    private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");

    private NameRules() {
    }

    public static boolean isValid(String value) {
        return value != null && ALLOWED.matcher(value).matches();
    }
}
