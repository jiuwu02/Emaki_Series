package emaki.jiuwu.craft.strengthen.editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorRenderer;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;

public final class StrengthenEditorMenus {

    public static final String HOME = "home";
    public static final String ECONOMY = "economy";
    public static final String LIMITS = "limits";
    public static final String SUCCESS_RATES = "success_rates";
    public static final String MATCHER = "matcher";
    public static final String STARS = "stars";
    public static final String STAR_ENTRY = "star_entry";
    public static final String EFFECT_ENTRY = "effect_entry";
    public static final String MATERIAL_ENTRY = "material_entry";
    public static final String CURRENCY_ENTRY = "currency_entry";
    public static final String TARGET = "target";
    public static final String PITY = "pity";
    public static final String PITY_TRACK_ENTRY = "pity_track_entry";
    public static final String COST_ENTRY = "cost_entry";
    public static final String CONDITION = "condition";
    public static final String ACTIONS = "actions";
    public static final String LIST_ENTRIES = EditorService.MENU_LIST_ENTRIES;

    public static final String ENTRY_INDEX_KEY = "entry_index";
    public static final String STAR_PATH_KEY = "star_path";

    public static final List<String> MODES = List.of("equipment", "affix", "gem");
    public static final List<String> MATCHER_TYPES = List.of(
            "component", "pdc_match", "lore_match", "variable_expr",
            "compare_target", "all_of", "any_of", "none_of");
    public static final List<String> MATCHER_OPERATORS = List.of(
            "exists", "absent", "==", "!=", ">", ">=", "<", "<=",
            "contains", "starts_with", "ends_with", "regex");
    public static final List<String> EFFECT_TYPES = List.of(
            "variables", "ea_attribute", "es_skill", "lore_action", "name_action");
    public static final List<String> CONSUME_TIMINGS = List.of("always", "success", "failure", "never");
    public static final List<String> PITY_SCOPES = List.of("item", "player");
    public static final List<String> PITY_EFFECTS = List.of("force_success", "chance_bonus");
    public static final List<String> CONDITION_TYPES = List.of("all_of", "any_of", "none_of");

    private StrengthenEditorMenus() {
    }

    public static EditorMenuMeta meta(String menuId) {
        if (menuId == null) {
            return null;
        }
        return switch (menuId) {
            case HOME -> new EditorMenuMeta(menuId, titleKey(menuId), null, new String[0], true);
            case ECONOMY -> menu(menuId, "economy");
            case LIMITS -> menu(menuId, "limits");
            case SUCCESS_RATES -> menu(menuId, "success_rates");
            case MATCHER -> menu(menuId, "matcher");
            case TARGET -> menu(menuId, "target");
            case PITY -> menu(menuId, "pity");
            case CONDITION -> menu(menuId, "condition");
            case ACTIONS -> menu(menuId, "actions");
            case STARS, STAR_ENTRY, EFFECT_ENTRY, MATERIAL_ENTRY, CURRENCY_ENTRY,
                    COST_ENTRY, PITY_TRACK_ENTRY, LIST_ENTRIES ->
                    new EditorMenuMeta(menuId, titleKey(menuId), HOME, null, true);
            default -> null;
        };
    }

    public static String titleKey(String menuId) {
        if (menuId == null || menuId.isEmpty() || HOME.equals(menuId)) {
            return "editor.menu.home";
        }
        return "editor.menu." + menuId;
    }

    public static List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return switch (menuId) {
            case HOME -> home(session);
            case ECONOMY -> economy();
            case LIMITS -> limits();
            case SUCCESS_RATES, STARS -> List.of();
            case MATCHER -> matcher();
            case STAR_ENTRY -> starEntry(session);
            case EFFECT_ENTRY -> effectEntry(session);
            case MATERIAL_ENTRY -> materialEntry(session);
            case CURRENCY_ENTRY -> currencyEntry(session);
            case TARGET -> target();
            case PITY -> pity();
            case PITY_TRACK_ENTRY -> pityTrackEntry(session);
            case COST_ENTRY -> costEntry(session);
            case CONDITION -> condition();
            case ACTIONS -> actions();
            default -> List.of();
        };
    }

    public static List<EditorField> starEntries(EditorSession session) {
        Object stars = session.draft(EditorSession.SCOPE_DEFAULT).value("stars");
        if (!(stars instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<EditorField> fields = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String starKey = Texts.toStringSafe(entry.getKey());
            if (starKey.isEmpty()) {
                continue;
            }
            String name = entry.getValue() instanceof Map<?, ?> stage
                    ? Texts.toStringSafe(stage.get("name"))
                    : "";
            String displayName = "<white>★" + starKey + "</white>"
                    + (name.isBlank() ? "" : " <gray>" + name + "</gray>");
            fields.add(new EditorField(
                    "star." + starKey,
                    EditorField.Kind.NAVIGATE,
                    "editor.field.star_entry",
                    displayName,
                    null,
                    null,
                    List.of(name.isBlank() ? "-" : name),
                    List.of(),
                    -1,
                    true));
        }
        return fields;
    }

    private static List<EditorFieldSpec> home(EditorSession session) {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.number("home_schema_version", "editor.field.schema_version",
                "schema_version"));
        specs.add(EditorFieldSpec.text("home_display_name", "editor.field.display_name", "display_name"));
        specs.add(EditorFieldSpec.text("home_gui_template", "editor.field.gui_template", "gui_template"));
        specs.add(navigate("home_economy", "editor.field.economy", ECONOMY, "economy"));
        specs.add(navigate("home_limits", "editor.field.limits", LIMITS, "limits"));
        specs.add(navigate("home_success_rates", "editor.field.success_rates", SUCCESS_RATES,
                "success_rates"));
        specs.add(navigate("home_matcher", "editor.field.matcher", MATCHER, "matcher"));
        specs.add(EditorFieldSpec.list("home_slot_groups", "editor.field.slot_groups",
                LIST_ENTRIES, "slot_groups"));
        specs.add(EditorFieldSpec.list("home_source_patterns", "editor.field.source_patterns",
                LIST_ENTRIES, "source_patterns"));
        specs.add(EditorFieldSpec.list("home_name_actions", "editor.field.name_actions",
                LIST_ENTRIES, "name_actions"));
        specs.add(EditorFieldSpec.list("home_lore_actions", "editor.field.lore_actions",
                LIST_ENTRIES, "lore_actions"));
        if (enhancement(session)) {
            specs.add(EditorFieldSpec.cycle("home_mode", "editor.field.mode", MODES, "mode"));
            specs.add(navigate("home_target", "editor.field.target", TARGET, "target"));
            specs.add(EditorFieldSpec.list("home_materials", "editor.field.materials",
                    MATERIAL_ENTRY, "materials"));
            specs.add(EditorFieldSpec.list("home_costs", "editor.field.costs", COST_ENTRY, "costs"));
            specs.add(chanceSpec(session));
            specs.add(navigate("home_pity", "editor.field.pity", PITY, "pity"));
            specs.add(EditorFieldSpec.list("home_pity_tracks", "editor.field.pity_tracks",
                    PITY_TRACK_ENTRY, "pity_tracks"));
            specs.add(navigate("home_condition", "editor.field.condition", CONDITION, "condition"));
            specs.add(navigate("home_actions", "editor.field.actions", ACTIONS, "actions"));
        } else if (branchRecipe(session)) {
            specs.add(structuralNavigate("home_branch_tree", "editor.field.branch_tree", "branch_tree"));
        } else {
            specs.add(navigate("home_stars", "editor.field.stars", STARS, "stars"));
        }
        return specs;
    }

    private static EditorFieldSpec chanceSpec(EditorSession session) {
        Object chance = session.draft(EditorSession.SCOPE_DEFAULT).value("chance");
        if (chance instanceof Map<?, ?>) {
            return structuralNavigate("home_chance", "editor.field.chance", "chance");
        }
        return EditorFieldSpec.text("home_chance", "editor.field.chance", "chance");
    }

    private static List<EditorFieldSpec> economy() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("economy_enabled", "editor.field.enabled", "economy", "enabled"));
        specs.add(EditorFieldSpec.list("economy_currencies", "editor.field.currencies",
                CURRENCY_ENTRY, "economy.currencies"));
        return specs;
    }

    private static List<EditorFieldSpec> limits() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.number("limit_max_star", "editor.field.max_star", "limits", "max_star"));
        specs.add(EditorFieldSpec.number("limit_max_temper", "editor.field.max_temper",
                "limits", "max_temper"));
        specs.add(EditorFieldSpec.number("limit_temper_bonus",
                "editor.field.temper_chance_bonus_per_level", "limits",
                "temper_chance_bonus_per_level"));
        specs.add(EditorFieldSpec.number("limit_success_cap", "editor.field.success_chance_cap",
                "limits", "success_chance_cap"));
        return specs;
    }

    private static List<EditorFieldSpec> matcher() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("matcher_type", "editor.field.type", MATCHER_TYPES,
                "matcher", "type"));
        specs.add(EditorFieldSpec.text("matcher_component", "editor.field.component",
                "matcher", "component"));
        specs.add(EditorFieldSpec.cycle("matcher_operator", "editor.field.operator", MATCHER_OPERATORS,
                "matcher", "operator"));
        specs.add(EditorFieldSpec.text("matcher_value", "editor.field.value", "matcher", "value"));
        return specs;
    }

    private static List<EditorFieldSpec> starEntry(EditorSession session) {
        String base = starPath(session);
        String[] parts = base.split("\\.");
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.text("star_name", "editor.field.name", append(parts, "name")));
        curated.add(EditorFieldSpec.list("star_effects", "editor.field.effects",
                EFFECT_ENTRY, base + ".effects"));
        curated.add(EditorFieldSpec.list("star_materials", "editor.field.materials",
                MATERIAL_ENTRY, base + ".materials"));
        curated.add(structuralNavigate("star_actions", "editor.field.actions", base + ".actions"));
        return withStructural(session, curated, parts);
    }

    private static List<EditorFieldSpec> effectEntry(EditorSession session) {
        String[] base = append(starPath(session).split("\\."), "effects");
        base = append(base, String.valueOf(entryIndex(session)));
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.cycle("effect_type", "editor.field.type", EFFECT_TYPES,
                append(base, "type")));
        return withStructural(session, curated, base);
    }

    private static List<EditorFieldSpec> materialEntry(EditorSession session) {
        String[] base;
        if (enhancement(session)) {
            base = entryBase(session, "materials");
        } else {
            base = append(starPath(session).split("\\."), "materials");
            base = append(base, String.valueOf(entryIndex(session)));
        }
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.list("material_item_sources", "editor.field.item_sources",
                LIST_ENTRIES, dotted(base) + ".item_sources"));
        if (enhancement(session)) {
            curated.add(EditorFieldSpec.cycle("material_consume", "editor.field.consume",
                    CONSUME_TIMINGS, append(base, "consume")));
            curated.add(quantitySpec(session, base));
            curated.add(EditorFieldSpec.toggle("material_required", "editor.field.required",
                    append(base, "required")));
        } else {
            curated.add(EditorFieldSpec.number("material_amount", "editor.field.amount",
                    append(base, "amount")));
            curated.add(EditorFieldSpec.text("material_id", "editor.field.material_id",
                    append(base, "material_id")));
            curated.add(EditorFieldSpec.text("material_count_key", "editor.field.count_key",
                    append(base, "count_key")));
            curated.add(EditorFieldSpec.toggle("material_optional", "editor.field.optional",
                    append(base, "optional")));
            curated.add(EditorFieldSpec.toggle("material_protection", "editor.field.protection",
                    append(base, "protection")));
            curated.add(EditorFieldSpec.number("material_temper_boost", "editor.field.temper_boost",
                    append(base, "temper_boost")));
        }
        return withStructural(session, curated, base);
    }

    private static EditorFieldSpec quantitySpec(EditorSession session, String[] base) {
        String[] path = append(base, "quantity");
        Object quantity = session.draft(EditorSession.SCOPE_DEFAULT).valueLenient(path);
        if (quantity instanceof Map<?, ?>) {
            return structuralNavigate("material_quantity", "editor.field.quantity", dotted(path));
        }
        if (quantity instanceof Number) {
            return EditorFieldSpec.number("material_quantity", "editor.field.quantity", path);
        }
        return EditorFieldSpec.text("material_quantity", "editor.field.quantity", path);
    }

    private static List<EditorFieldSpec> currencyEntry(EditorSession session) {
        String[] base = entryBase(session, null, "economy", "currencies");
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.text("currency_provider", "editor.field.provider",
                append(base, "provider")));
        curated.add(EditorFieldSpec.text("currency_id", "editor.field.currency_id",
                append(base, "currency_id")));
        curated.add(EditorFieldSpec.number("currency_base_cost", "editor.field.base_cost",
                append(base, "base_cost")));
        curated.add(EditorFieldSpec.text("currency_cost_formula", "editor.field.cost_formula",
                append(base, "cost_formula")));
        curated.add(EditorFieldSpec.text("currency_display_name", "editor.field.display_name",
                append(base, "display_name")));
        return withStructural(session, curated, base);
    }

    private static List<EditorFieldSpec> target() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("target_provider", "editor.field.provider",
                "target", "provider"));
        return specs;
    }

    private static List<EditorFieldSpec> pity() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("pity_scope", "editor.field.scope", PITY_SCOPES, "pity", "scope"));
        specs.add(EditorFieldSpec.text("pity_group", "editor.field.group", "pity", "group"));
        specs.add(EditorFieldSpec.number("pity_threshold", "editor.field.threshold",
                "pity", "threshold"));
        specs.add(EditorFieldSpec.cycle("pity_effect", "editor.field.effect", PITY_EFFECTS,
                "pity", "effect"));
        return specs;
    }

    private static List<EditorFieldSpec> pityTrackEntry(EditorSession session) {
        String[] base = entryBase(session, "pity_tracks");
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.cycle("track_scope", "editor.field.scope", PITY_SCOPES,
                append(base, "scope")));
        curated.add(EditorFieldSpec.text("track_group", "editor.field.group", append(base, "group")));
        curated.add(EditorFieldSpec.number("track_threshold", "editor.field.threshold",
                append(base, "threshold")));
        curated.add(EditorFieldSpec.cycle("track_effect", "editor.field.effect", PITY_EFFECTS,
                append(base, "effect")));
        return withStructural(session, curated, base);
    }

    private static List<EditorFieldSpec> costEntry(EditorSession session) {
        String[] base = entryBase(session, "costs");
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.text("cost_provider", "editor.field.provider",
                append(base, "provider")));
        curated.add(EditorFieldSpec.text("cost_currency_id", "editor.field.currency_id",
                append(base, "currency_id")));
        curated.add(amountSpec(session, base));
        return withStructural(session, curated, base);
    }

    private static EditorFieldSpec amountSpec(EditorSession session, String[] base) {
        String[] path = append(base, "amount");
        Object amount = session.draft(EditorSession.SCOPE_DEFAULT).valueLenient(path);
        if (amount instanceof Map<?, ?>) {
            return structuralNavigate("cost_amount", "editor.field.amount", dotted(path));
        }
        return EditorFieldSpec.text("cost_amount", "editor.field.amount", path);
    }

    private static List<EditorFieldSpec> condition() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("condition_type", "editor.field.type", CONDITION_TYPES,
                "condition", "type"));
        specs.add(EditorFieldSpec.number("condition_required_count", "editor.field.required_count",
                "condition", "required_count"));
        specs.add(EditorFieldSpec.toggle("condition_invalid", "editor.field.invalid_as_failure",
                "condition", "invalid_as_failure"));
        return specs;
    }

    private static List<EditorFieldSpec> actions() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.list("actions_on_success", "editor.field.on_success",
                LIST_ENTRIES, "actions.on_success"));
        specs.add(EditorFieldSpec.list("actions_on_failure", "editor.field.on_failure",
                LIST_ENTRIES, "actions.on_failure"));
        return specs;
    }

    private static boolean enhancement(EditorSession session) {
        Path parent = session.document().path().getParent();
        if (parent == null || parent.getFileName() == null) {
            return false;
        }
        return StrengthenEditorModule.ROOT_ENHANCEMENT_RECIPES
                .equals(parent.getFileName().toString());
    }

    private static boolean branchRecipe(EditorSession session) {
        return session.draft(EditorSession.SCOPE_DEFAULT).value("branch_tree") != null;
    }

    private static String starPath(EditorSession session) {
        String path = session.context(STAR_PATH_KEY);
        return path == null || path.isBlank() ? "stars.0" : path;
    }

    private static String[] entryBase(EditorSession session, String listSegment) {
        String[] index = { String.valueOf(entryIndex(session)) };
        if (listSegment == null) {
            return index;
        }
        String[] base = new String[2];
        base[0] = listSegment;
        base[1] = index[0];
        return base;
    }

    private static String[] entryBase(EditorSession session, String listSegment, String... prefix) {
        String[] base = new String[prefix.length + 1];
        System.arraycopy(prefix, 0, base, 0, prefix.length);
        String[] index = entryBase(session, listSegment);
        for (int position = 0; position < index.length; position++) {
            base[prefix.length + position] = index[position];
        }
        return base;
    }

    private static int entryIndex(EditorSession session) {
        try {
            return Math.max(0, Integer.parseInt(session.context(ENTRY_INDEX_KEY)));
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    private static List<EditorFieldSpec> withStructural(EditorSession session,
            List<EditorFieldSpec> curated, String... base) {
        YamlTextDocument draft = session.draft(EditorSession.SCOPE_DEFAULT);
        List<EditorFieldSpec> combined = new ArrayList<>(curated);
        combined.addAll(EditorRenderer.structuralSpecs(draft, base, curated));
        return combined;
    }

    private static String[] append(String[] base, String segment) {
        String[] result = new String[base.length + 1];
        System.arraycopy(base, 0, result, 0, base.length);
        result[base.length] = segment;
        return result;
    }

    private static String dotted(String[] path) {
        return String.join(".", path);
    }

    private static EditorFieldSpec navigate(String id, String labelKey, String targetMenu, String path) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey, new String[]{path},
                List.of(), targetMenu, null, null);
    }

    private static EditorFieldSpec structuralNavigate(String id, String labelKey, String dottedPath) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey,
                dottedPath.split("\\."), List.of(), "#" + dottedPath, null, null);
    }

    private static EditorMenuMeta menu(String menuId, String... structuralRoot) {
        return new EditorMenuMeta(menuId, titleKey(menuId), HOME, structuralRoot, true);
    }
}
