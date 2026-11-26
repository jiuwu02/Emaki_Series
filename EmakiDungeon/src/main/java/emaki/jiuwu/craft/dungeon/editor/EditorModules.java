package emaki.jiuwu.craft.dungeon.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EditorModules {

    public enum Coord {
        POINT, REGION, NONE
    }

    public enum Kind {
        TEXT, NUMBER, DECIMAL, BOOLEAN
    }

    public record Field(String key, String[] path, Kind kind) {
    }

    public record ModuleSpec(String type, String section, String prefix, boolean single,
            Coord coord, List<Field> fields) {
    }

    private static Field field(String key, Kind kind, String... path) {
        return new Field(key, path, kind);
    }

    private static final List<ModuleSpec> ALL = List.of(
            new ModuleSpec("entry", "spawn_point", "entry", true, Coord.POINT, List.of()),
            new ModuleSpec("room", "room_zones", "room", false, Coord.REGION,
                    List.of(field("order", Kind.NUMBER, "order"))),
            new ModuleSpec("checkpoint", "check_points", "checkpoint", false, Coord.REGION,
                    List.of(field("record_id", Kind.TEXT, "record_id"),
                            field("min_players", Kind.NUMBER, "min_players"))),
            new ModuleSpec("respawn", "respawn_points", "respawn", false, Coord.POINT,
                    List.of(field("checkpoint", Kind.TEXT, "checkpoint"))),
            new ModuleSpec("exit", "exit_points", "exit", false, Coord.POINT,
                    List.of(field("need_confirm", Kind.BOOLEAN, "need_confirm"))),
            new ModuleSpec("gate", "gate_zones", "gate", false, Coord.REGION,
                    List.of(field("gate_id", Kind.TEXT, "gate_id"),
                            field("logic", Kind.TEXT, "logic"),
                            field("open_mode", Kind.TEXT, "open_mode"))),
            new ModuleSpec("mob_spawn", "mob_spawn_points", "mob_spawn", false, Coord.POINT,
                    List.of(field("spawn_id", Kind.TEXT, "spawn_id"),
                            field("range", Kind.NUMBER, "trigger", "range"),
                            field("min_players", Kind.NUMBER, "trigger", "min_players"),
                            field("once", Kind.BOOLEAN, "trigger", "once"))),
            new ModuleSpec("trigger", "trigger_zones", "trigger", false, Coord.REGION,
                    List.of(field("direction", Kind.TEXT, "direction"))),
            new ModuleSpec("block", "block_points", "block", false, Coord.POINT,
                    List.of(field("click", Kind.TEXT, "click"))),
            new ModuleSpec("chest", "reward_points", "chest", false, Coord.POINT,
                    List.of(field("loot_table", Kind.TEXT, "loot_table"))),
            new ModuleSpec("option", "option_points", "option", false, Coord.POINT,
                    List.of(field("option_id", Kind.TEXT, "option_id"),
                            field("select_mode", Kind.TEXT, "select_mode"),
                            field("time_limit_seconds", Kind.NUMBER, "time_limit_seconds"),
                            field("min_select", Kind.NUMBER, "min_select"),
                            field("max_select", Kind.NUMBER, "max_select"))),
            new ModuleSpec("tip", "tip_points", "tip", false, Coord.POINT,
                    List.of(field("text", Kind.TEXT, "text"),
                            field("channel", Kind.TEXT, "type"))),
            new ModuleSpec("teleport", "teleport_zones", "teleport", false, Coord.REGION,
                    List.of(field("all_members", Kind.BOOLEAN, "all_members"))),
            new ModuleSpec("boundary", "boundary_zones", "boundary", false, Coord.REGION, List.of()),
            new ModuleSpec("effect", "effect_zones", "effect", false, Coord.REGION,
                    List.of(field("interval_ticks", Kind.NUMBER, "interval_ticks"))),
            new ModuleSpec("mechanism", "mechanism_zones", "mechanism", false, Coord.REGION, List.of()),
            new ModuleSpec("room_timer", "room_timer_zones", "room_timer", false, Coord.REGION,
                    List.of(field("seconds", Kind.NUMBER, "seconds"))),
            new ModuleSpec("kill_target", "kill_targets", "kill_target", false, Coord.NONE,
                    List.of(field("target", Kind.TEXT, "target"),
                            field("count", Kind.NUMBER, "count"))),
            new ModuleSpec("item", "item_points", "item", false, Coord.POINT,
                    List.of(field("item", Kind.TEXT, "item"),
                            field("amount", Kind.NUMBER, "amount"),
                            field("consume", Kind.BOOLEAN, "consume"))),
            new ModuleSpec("fx", "fx_points", "fx", false, Coord.POINT,
                    List.of(field("sound", Kind.TEXT, "sound"),
                            field("particle", Kind.TEXT, "particle"),
                            field("radius", Kind.DECIMAL, "radius"))),
            new ModuleSpec("temp_item", "temp_items", "temp_item", false, Coord.NONE,
                    List.of(field("item", Kind.TEXT, "item"),
                            field("amount", Kind.NUMBER, "amount"),
                            field("timing", Kind.TEXT, "timing"),
                            field("hint", Kind.TEXT, "hint")))
    );

    private static final Map<String, ModuleSpec> BY_TYPE = index();

    private static Map<String, ModuleSpec> index() {
        Map<String, ModuleSpec> map = new LinkedHashMap<>();
        for (ModuleSpec spec : ALL) {
            map.put(spec.type(), spec);
        }
        return Collections.unmodifiableMap(map);
    }

    public static List<ModuleSpec> all() {
        return ALL;
    }

    public static List<String> types() {
        List<String> types = new ArrayList<>(ALL.size());
        for (ModuleSpec spec : ALL) {
            types.add(spec.type());
        }
        return List.copyOf(types);
    }

    public static ModuleSpec of(String type) {
        return type == null ? null : BY_TYPE.get(type);
    }

    private EditorModules() {
    }
}
