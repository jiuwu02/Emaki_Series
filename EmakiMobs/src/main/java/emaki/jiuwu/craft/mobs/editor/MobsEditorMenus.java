package emaki.jiuwu.craft.mobs.editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.EntityType;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorRenderer;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;

final class MobsEditorMenus {

    static final String HOME = "home";
    static final String ACTIONS = "mob_actions";
    static final String THREAT = "mob_threat";
    static final String BOSS_BAR = "mob_boss_bar";
    static final String MODEL = "mob_model";
    static final String LOCK_TARGET = "mob_lock_target";
    static final String SPAWN_RULE_ENTRY = "spawn_rule_entry";
    static final String SELECTORS = "selectors";
    static final String SELECTOR_PREFIX = "selector:";
    static final String SCORE_ENTRY = "score_entry";
    static final String LIST_ENTRIES = EditorService.MENU_LIST_ENTRIES;
    static final String SELECTOR_OPEN_PREFIX = "selector_open:";
    static final String CONTEXT_SELECTOR = "selector_name";
    static final String FIELD_RULES = "rules";

    enum DocKind {
        MOB,
        LOOT,
        SPAWN,
        TARGET_SELECTORS
    }

    private record MenuInfo(String titleKey, String parent, boolean paginated) {
    }

    private static final Map<String, MenuInfo> MENUS = Map.ofEntries(
            Map.entry(HOME, new MenuInfo("editor.menu.home", HOME, false)),
            Map.entry(ACTIONS, new MenuInfo("editor.menu.mob_actions", HOME, false)),
            Map.entry(THREAT, new MenuInfo("editor.menu.mob_threat", HOME, false)),
            Map.entry(BOSS_BAR, new MenuInfo("editor.menu.mob_boss_bar", HOME, false)),
            Map.entry(MODEL, new MenuInfo("editor.menu.mob_model", HOME, false)),
            Map.entry(LOCK_TARGET, new MenuInfo("editor.menu.mob_lock_target", HOME, false)),
            Map.entry(SPAWN_RULE_ENTRY, new MenuInfo("editor.menu.spawn_rule_entry", HOME, false)),
            Map.entry(SELECTORS, new MenuInfo("editor.menu.selectors", HOME, false)),
            Map.entry(SCORE_ENTRY, new MenuInfo("editor.menu.score_entry", SELECTORS, false)),
            Map.entry(LIST_ENTRIES, new MenuInfo("editor.menu.list_entries", HOME, true)));

    private static final List<String> SPAWN_RULE_TYPES = List.of("natural", "autonomous");
    private static final List<String> TRIGGER_OPTIONS = List.of(
            "interval", "player_follow", "day_interval", "cron");
    private static final List<String> SELECTOR_MODE_OPTIONS = List.of(
            "highest", "lowest", "random", "weighted_random", "first_damager", "last_damager");
    private static final List<String> SCORE_TYPE_OPTIONS = List.of(
            "threat", "distance", "health", "equipment", "expression");

    private MobsEditorMenus() {
    }

    static String titleKey(String menuId) {
        if (menuId == null) {
            return "editor.menu.home";
        }
        if (menuId.startsWith(SELECTOR_PREFIX)) {
            return "editor.menu.selector";
        }
        MenuInfo info = MENUS.get(menuId);
        return info == null ? "editor.menu." + menuId : info.titleKey();
    }

    static EditorMenuMeta meta(String menuId) {
        if (menuId == null) {
            return null;
        }
        if (menuId.startsWith(SELECTOR_PREFIX)) {
            return new EditorMenuMeta(menuId, "editor.menu.selector", SELECTORS, null, false);
        }
        MenuInfo info = MENUS.get(menuId);
        if (info == null) {
            return null;
        }
        return new EditorMenuMeta(menuId, info.titleKey(), info.parent(), null, info.paginated());
    }

    static List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        if (menuId == null) {
            return List.of();
        }
        if (menuId.startsWith(SELECTOR_PREFIX)) {
            return selectorEntry(session, menuId.substring(SELECTOR_PREFIX.length()));
        }
        return switch (menuId) {
            case HOME -> home(session);
            case ACTIONS -> withStructural(session, "actions", List.of());
            case THREAT -> withStructural(session, "threat", threat());
            case BOSS_BAR -> withStructural(session, "boss_bar", bossBar());
            case MODEL -> withStructural(session, "model", model());
            case LOCK_TARGET -> withStructural(session, "lock_target", lockTarget());
            case SPAWN_RULE_ENTRY -> spawnRuleEntry(session);
            case SELECTORS -> List.of();
            case SCORE_ENTRY -> scoreEntry(session);
            default -> List.of();
        };
    }

    static List<EditorField> selectorEntries(EditorSession session) {
        Object node = session.draft().value("selectors");
        if (!(node instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<EditorField> fields = new ArrayList<>();
        for (Object key : map.keySet()) {
            String name = Texts.toStringSafe(key);
            if (name.isEmpty()) {
                continue;
            }
            fields.add(new EditorField(SELECTOR_OPEN_PREFIX + name, EditorField.Kind.COMMAND,
                    "editor.field.selector_entry", name, null, null,
                    List.of(name), List.of(), -1, true));
        }
        return fields;
    }

    static DocKind docKind(EditorSession session) {
        Path path = session.document().path();
        Path parent = path.getParent();
        String directory = parent == null || parent.getFileName() == null ? "" : parent.getFileName().toString();
        if ("loot_tables".equals(directory)) {
            return DocKind.LOOT;
        }
        if ("spawn_rules".equals(directory)) {
            return DocKind.SPAWN;
        }
        if ("mobs".equals(directory)) {
            return DocKind.MOB;
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        if (name.startsWith("target_selectors")) {
            return DocKind.TARGET_SELECTORS;
        }
        return DocKind.MOB;
    }

    private static List<EditorFieldSpec> home(EditorSession session) {
        DocKind kind = docKind(session);
        return switch (kind) {
            case LOOT -> withStructural(session, "", lootHome());
            case SPAWN -> withStructural(session, "", spawnHome());
            case TARGET_SELECTORS -> withStructural(session, "", targetSelectorHome());
            case MOB -> withStructural(session, "", mobHome());
        };
    }

    private static List<EditorFieldSpec> mobHome() {
        return List.of(
                EditorFieldSpec.cycle("type", "editor.field.type", entityTypeOptions(), "type"),
                EditorFieldSpec.text("display_name", "editor.field.display_name", "display_name"),
                EditorFieldSpec.number("experience", "editor.field.experience", "experience"),
                nav("components", "editor.field.components", "#components", "components"),
                EditorFieldSpec.list("ea_attributes", "editor.field.ea_attributes",
                        LIST_ENTRIES, "ea_attributes"),
                nav("actions", "editor.field.actions", ACTIONS, "actions"),
                nav("threat", "editor.field.threat", THREAT, "threat"),
                nav("boss_bar", "editor.field.boss_bar", BOSS_BAR, "boss_bar"),
                nav("model", "editor.field.model", MODEL, "model"),
                EditorFieldSpec.text("target_selector", "editor.field.target_selector", "target_selector"),
                nav("lock_target", "editor.field.lock_target", LOCK_TARGET, "lock_target"));
    }

    private static List<EditorFieldSpec> lootHome() {
        return List.of(
                EditorFieldSpec.text("mob_id", "editor.field.mob_id", "mob_id"),
                nav("pools", "editor.field.pools", "#pools", "pools"));
    }

    private static List<EditorFieldSpec> spawnHome() {
        return List.of(EditorFieldSpec.list(FIELD_RULES, "editor.field.rules",
                SPAWN_RULE_ENTRY, FIELD_RULES));
    }

    private static List<EditorFieldSpec> targetSelectorHome() {
        return List.of(
                EditorFieldSpec.number("snapshot_interval_ticks",
                        "editor.field.snapshot_interval_ticks", "snapshot_interval_ticks"),
                EditorFieldSpec.number("snapshot_players_per_tick",
                        "editor.field.snapshot_players_per_tick", "snapshot_players_per_tick"),
                nav("equipment_tables", "editor.field.equipment_tables",
                        "#equipment_tables", "equipment_tables"),
                nav("selectors", "editor.field.selectors", SELECTORS, "selectors"));
    }

    private static List<EditorFieldSpec> threat() {
        return List.of(
                EditorFieldSpec.toggle("threat_enabled", "editor.field.threat_enabled",
                        "threat", "enabled"),
                EditorFieldSpec.number("threat_max_range", "editor.field.threat_max_range",
                        "threat", "max_range"),
                nav("threat_weights", "editor.field.threat_weights",
                        "#threat.weights", "threat", "weights"),
                nav("threat_decay", "editor.field.threat_decay",
                        "#threat.decay", "threat", "decay"));
    }

    private static List<EditorFieldSpec> bossBar() {
        return List.of(
                EditorFieldSpec.text("boss_bar_title", "editor.field.boss_bar_title",
                        "boss_bar", "title"),
                EditorFieldSpec.cycle("boss_bar_color", "editor.field.boss_bar_color",
                        barColorOptions(), "boss_bar", "color"),
                EditorFieldSpec.cycle("boss_bar_style", "editor.field.boss_bar_style",
                        barStyleOptions(), "boss_bar", "style"),
                EditorFieldSpec.number("boss_bar_range", "editor.field.boss_bar_range",
                        "boss_bar", "range"));
    }

    private static List<EditorFieldSpec> model() {
        return List.of(
                EditorFieldSpec.text("model_blueprint", "editor.field.model_blueprint",
                        "model", "blueprint"),
                EditorFieldSpec.number("model_scale", "editor.field.model_scale",
                        "model", "scale"),
                nav("model_animations", "editor.field.model_animations",
                        "#model.animations", "model", "animations"),
                nav("model_keyframes", "editor.field.model_keyframes",
                        "#model.keyframes", "model", "keyframes"),
                nav("model_lod", "editor.field.model_lod", "#model.lod", "model", "lod"));
    }

    private static List<EditorFieldSpec> lockTarget() {
        return List.of(
                EditorFieldSpec.toggle("lock_enabled", "editor.field.lock_enabled",
                        "lock_target", "enabled"),
                EditorFieldSpec.number("lock_interval_ticks", "editor.field.lock_interval_ticks",
                        "lock_target", "interval_ticks"));
    }

    private static List<EditorFieldSpec> spawnRuleEntry(EditorSession session) {
        String base = "rules." + entryIndex(session);
        YamlTextDocument draft = session.draft();
        List<EditorFieldSpec> curated = List.of(
                EditorFieldSpec.text("rule_mob_id", "editor.field.rule_mob_id",
                        extend(base, "mob_id")),
                EditorFieldSpec.cycle("rule_type", "editor.field.rule_type",
                        withCurrent(draft, extend(base, "type"), SPAWN_RULE_TYPES),
                        extend(base, "type")),
                EditorFieldSpec.list("rule_biomes", "editor.field.rule_biomes",
                        LIST_ENTRIES, base + ".biomes"),
                EditorFieldSpec.list("rule_y_range", "editor.field.rule_y_range",
                        LIST_ENTRIES, base + ".y_range"),
                EditorFieldSpec.number("rule_light_level_max", "editor.field.rule_light_level_max",
                        extend(base, "light_level_max")),
                EditorFieldSpec.number("rule_replacement_chance",
                        "editor.field.rule_replacement_chance", extend(base, "replacement_chance")),
                EditorFieldSpec.number("rule_max_nearby", "editor.field.rule_max_nearby",
                        extend(base, "max_nearby")),
                nav("rule_count", "editor.field.rule_count",
                        "#" + base + ".count", extend(base, "count")),
                EditorFieldSpec.cycle("rule_trigger", "editor.field.rule_trigger",
                        withCurrent(draft, extend(base, "trigger"), TRIGGER_OPTIONS),
                        extend(base, "trigger")),
                EditorFieldSpec.number("rule_interval_ticks", "editor.field.rule_interval_ticks",
                        extend(base, "interval_ticks")),
                EditorFieldSpec.list("rule_structures", "editor.field.rule_structures",
                        LIST_ENTRIES, base + ".structures"),
                nav("rule_distance", "editor.field.rule_distance",
                        "#" + base + ".distance", extend(base, "distance")),
                EditorFieldSpec.toggle("rule_require_surface", "editor.field.rule_require_surface",
                        extend(base, "require_surface")));
        return withStructural(session, base, curated);
    }

    private static List<EditorFieldSpec> selectorEntry(EditorSession session, String name) {
        String base = "selectors." + name;
        YamlTextDocument draft = session.draft();
        List<EditorFieldSpec> curated = List.of(
                EditorFieldSpec.number("sel_range", "editor.field.sel_range",
                        extend(base, "range")),
                EditorFieldSpec.cycle("sel_mode", "editor.field.sel_mode",
                        withCurrent(draft, extend(base, "mode"), SELECTOR_MODE_OPTIONS),
                        extend(base, "mode")),
                EditorFieldSpec.list("sel_filter", "editor.field.sel_filter",
                        LIST_ENTRIES, base + ".filter"),
                EditorFieldSpec.list("sel_score", "editor.field.sel_score",
                        SCORE_ENTRY, base + ".score"));
        return withStructural(session, base, curated);
    }

    private static List<EditorFieldSpec> scoreEntry(EditorSession session) {
        String selector = session.context(CONTEXT_SELECTOR);
        if (selector == null || selector.isEmpty()) {
            return List.of();
        }
        String base = "selectors." + selector + ".score." + entryIndex(session);
        YamlTextDocument draft = session.draft();
        List<EditorFieldSpec> curated = List.of(
                EditorFieldSpec.cycle("score_type", "editor.field.score_type",
                        withCurrent(draft, extend(base, "type"), SCORE_TYPE_OPTIONS),
                        extend(base, "type")),
                EditorFieldSpec.number("score_factor", "editor.field.score_factor",
                        extend(base, "factor")),
                EditorFieldSpec.text("score_table", "editor.field.score_table",
                        extend(base, "table")),
                EditorFieldSpec.text("score_value", "editor.field.score_value",
                        extend(base, "value")));
        return withStructural(session, base, curated);
    }

    private static List<String> entityTypeOptions() {
        List<String> options = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            options.add(type.name().toLowerCase(Locale.ROOT));
        }
        return options;
    }

    private static List<String> barColorOptions() {
        List<String> options = new ArrayList<>();
        for (BarColor color : BarColor.values()) {
            options.add(color.name());
        }
        return options;
    }

    private static List<String> barStyleOptions() {
        List<String> options = new ArrayList<>();
        for (BarStyle style : BarStyle.values()) {
            options.add(style.name());
        }
        return options;
    }

    private static List<String> withCurrent(YamlTextDocument draft, String[] path,
            List<String> options) {
        List<String> result = new ArrayList<>(options);
        String current = Texts.toStringSafe(draft.valueLenient(path));
        if (!current.isBlank() && !result.contains(current)) {
            result.add(current);
        }
        return result;
    }

    private static List<EditorFieldSpec> withStructural(EditorSession session, String base,
            List<EditorFieldSpec> curated) {
        String[] segments = base == null || base.isEmpty() ? new String[0] : base.split("\\.");
        List<EditorFieldSpec> combined = new ArrayList<>(curated);
        combined.addAll(EditorRenderer.structuralSpecs(session.draft(), segments, curated));
        return combined;
    }

    private static int entryIndex(EditorSession session) {
        try {
            return Math.max(0, Integer.parseInt(session.context("entry_index")));
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    private static String[] extend(String base, String... tail) {
        String[] head = base == null || base.isEmpty() ? new String[0] : base.split("\\.");
        String[] result = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, result, head.length, tail.length);
        return result;
    }

    private static EditorFieldSpec nav(String id, String labelKey, String targetMenu, String... path) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey, path, List.of(),
                targetMenu, null, null);
    }
}
