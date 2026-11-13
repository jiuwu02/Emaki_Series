package emaki.jiuwu.craft.kits.store;

import java.util.Locale;
import java.util.UUID;

public final class StoreKeys {

    private StoreKeys() {
    }

    public static String player(UUID uuid) {
        return uuid.toString();
    }

    public static String claim(String kitId, UUID player, String periodKey) {
        return "claim|" + norm(kitId) + "|" + player + "|" + norm(periodKey);
    }

    public static String cdkGlobal(String code) {
        return "cdk_global|" + norm(code);
    }

    public static String cdkPlayer(String code, UUID player) {
        return "cdk_player|" + norm(code) + "|" + player;
    }

    public static String milestone(String kitId, UUID player, int count) {
        return "milestone|" + norm(kitId) + "|" + player + "|" + count;
    }

    public static String bind(UUID invitee) {
        return "bind|" + invitee;
    }

    public static String join(UUID player) {
        return "join|" + player;
    }

    public static String selection(String kitId, UUID player, String periodKey) {
        return "selection|" + norm(kitId) + "|" + player + "|" + norm(periodKey);
    }

    private static String norm(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT);
    }
}
