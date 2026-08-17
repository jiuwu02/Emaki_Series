package emaki.jiuwu.craft.corelib.trigger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class TriggerRegistry {

    // Standard trigger ID constants
    public static final String LEFT_CLICK = "left_click";
    public static final String RIGHT_CLICK = "right_click";
    public static final String SHIFT_LEFT_CLICK = "shift_left_click";
    public static final String SHIFT_RIGHT_CLICK = "shift_right_click";
    public static final String DROP_Q = "drop_q";
    public static final String ATTACK = "attack";
    public static final String DAMAGED = "damaged";
    public static final String DAMAGED_BY_ENTITY = "damaged_by_entity";
    public static final String DEATH = "death";
    public static final String KILL_ENTITY = "kill_entity";
    public static final String KILL_PLAYER = "kill_player";
    public static final String SHOOT_BOW = "shoot_bow";
    public static final String ARROW_HIT = "arrow_hit";
    public static final String ARROW_LAND = "arrow_land";
    public static final String SHOOT_TRIDENT = "shoot_trident";
    public static final String TRIDENT_HIT = "trident_hit";
    public static final String TRIDENT_LAND = "trident_land";
    public static final String BREAK_BLOCK = "break_block";
    public static final String PLACE_BLOCK = "place_block";
    public static final String DROP_ITEM = "drop_item";
    public static final String SHIFT_DROP_ITEM = "shift_drop_item";
    public static final String SWAP_ITEMS = "swap_items";
    public static final String SHIFT_SWAP_ITEMS = "shift_swap_items";
    public static final String LOGIN = "login";
    public static final String SNEAK = "sneak";
    public static final String TELEPORT = "teleport";
    public static final String TIMER = "timer";
    public static final String COMBO_ATTACK = "combo_attack";

    private static final Logger LOGGER = Logger.getLogger(TriggerRegistry.class.getName());

    private final Map<String, TriggerDefinition> definitions = new LinkedHashMap<>();

    public void register(TriggerDefinition definition) {
        definitions.put(definition.id(), definition);
    }

    public TriggerDefinition get(String id) {
        return definitions.get(id);
    }

    public Map<String, TriggerDefinition> all() {
        return Collections.unmodifiableMap(definitions);
    }

    public boolean isEnabled(String id) {
        TriggerDefinition def = definitions.get(id);
        return def != null && def.enabled();
    }

    public String getDisplayName(String id) {
        TriggerDefinition def = definitions.get(id);
        if (def != null) {
            return def.displayName();
        }
        LOGGER.warning("Trigger '" + id + "' is not registered; falling back to [" + id + "]");
        return "[" + id + "]";
    }

    public void clear() {
        definitions.clear();
    }

    public void loadFromConfig(Map<String, TriggerDefinition> configEntries) {
        for (var entry : configEntries.entrySet()) {
            definitions.put(entry.getKey(), entry.getValue());
        }
    }
}
