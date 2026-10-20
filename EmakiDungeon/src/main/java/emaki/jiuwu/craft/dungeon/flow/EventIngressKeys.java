package emaki.jiuwu.craft.dungeon.flow;

import java.util.Map;
import java.util.UUID;

public final class EventIngressKeys {

    public static final String ZONE = "flow.zone";
    public static final String RANGE = "flow.range";
    public static final String BLOCK = "flow.block";
    public static final String ITEM = "flow.item";
    public static final String DEATH = "flow.death";
    public static final String ACTIVATE = "flow.activate";
    public static final String REDSTONE = "flow.redstone";
    public static final String PLAYER_STATE = "flow.player_state";
    public static final String CHEST = "flow.chest";

    public static final String KEY_PLAYER = "player";
    public static final String KEY_ENTITY = "entity";

    private EventIngressKeys() {
    }

    public static UUID player(Map<String, Object> payload) {
        return uuid(payload, KEY_PLAYER);
    }

    public static UUID entity(Map<String, Object> payload) {
        return uuid(payload, KEY_ENTITY);
    }

    private static UUID uuid(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get(key);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
