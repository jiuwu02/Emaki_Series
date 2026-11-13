package emaki.jiuwu.craft.kits.config;

import java.util.Locale;
import java.util.Set;

public final class KitIds {

    private static final Set<String> RESERVED = Set.of("all", "list", "help", "reload", "cdk", "inspect");

    private KitIds() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean valid(String raw) {
        String id = normalize(raw);
        if (id.isEmpty() || id.length() > 64 || RESERVED.contains(id)) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
