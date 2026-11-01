package emaki.jiuwu.craft.station.editor;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorRenderer;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;

public final class StationEditorMenus {

    public static final String HOME = "home";
    public static final String STATION_QUEUE = "station_queue";
    public static final String STATION_CHANNELS = "station_channels";
    public static final String STATION_OUTPUT = "station_output";
    public static final String DISMANTLE_OUTPUT = "dismantle_output";
    public static final String CONDITION = "condition";
    public static final String DISPLAY_CONDITION = "display_condition";
    public static final String RECIPE_COST = "recipe_cost";
    public static final String RECIPE_MATERIAL_ENTRY = "recipe_material_entry";
    public static final String RECIPE_ACTIONS = "recipe_actions";
    public static final String DISMANTLE_ROLLS = "dismantle_rolls";
    public static final String QUEUE_COST_TIER_ENTRY = "queue_cost_tier_entry";
    public static final String QUEUE_COSTS_FALLBACK = "queue_costs_fallback";
    public static final String QUEUE_COSTS_BATCH = "queue_costs_batch";
    public static final String LIST_ENTRIES = EditorService.MENU_LIST_ENTRIES;

    public static final String ENTRY_INDEX_KEY = "entry_index";

    public static final List<String> PROGRESS_MODES = List.of("offline", "online");
    public static final List<String> MATERIAL_CHANNELS = List.of("backpack", "storage");
    public static final List<String> OUTPUT_ROUTINGS =
            List.of("storage_first", "backpack_first", "storage_only", "backpack_only");
    public static final List<String> CURRENCY_TYPES = List.of("vault", "excellent");

    private StationEditorMenus() {
    }

    public static EditorMenuMeta meta(String menuId) {
        if (menuId == null) {
            return null;
        }
        return switch (menuId) {
            case HOME -> new EditorMenuMeta(menuId, titleKey(menuId), null, new String[0], true);
            case STATION_QUEUE -> menu(menuId, "queue");
            case STATION_CHANNELS -> menu(menuId, "channels");
            case STATION_OUTPUT, DISMANTLE_OUTPUT -> menu(menuId, "output");
            case CONDITION -> menu(menuId, "condition");
            case DISPLAY_CONDITION -> menu(menuId, "display_condition");
            case RECIPE_COST -> menu(menuId, "cost");
            case RECIPE_ACTIONS -> menu(menuId, "actions");
            case DISMANTLE_ROLLS -> menu(menuId, "rolls");
            case QUEUE_COSTS_FALLBACK -> menu(menuId, "fallback");
            case QUEUE_COSTS_BATCH -> menu(menuId, "batch");
            case RECIPE_MATERIAL_ENTRY, QUEUE_COST_TIER_ENTRY, LIST_ENTRIES ->
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
            case STATION_QUEUE -> stationQueue();
            case STATION_CHANNELS -> stationChannels();
            case STATION_OUTPUT -> stationOutput();
            case DISMANTLE_OUTPUT -> dismantleOutput();
            case CONDITION -> condition();
            case DISPLAY_CONDITION -> displayCondition();
            case RECIPE_COST -> recipeCost();
            case RECIPE_ACTIONS -> recipeActions();
            case DISMANTLE_ROLLS -> dismantleRolls();
            case QUEUE_COSTS_FALLBACK -> queueCostsFallback();
            case QUEUE_COSTS_BATCH -> queueCostsBatch();
            case RECIPE_MATERIAL_ENTRY -> materialEntry(session, entryIndex(session));
            case QUEUE_COST_TIER_ENTRY -> tierEntry(session, entryIndex(session));
            default -> List.of();
        };
    }

    private static List<EditorFieldSpec> home(EditorSession session) {
        return switch (StationEditorModule.docType(session.document().path())) {
            case StationEditorModule.ROOT_STATIONS -> stationHome();
            case StationEditorModule.ROOT_STATIONS_DISMANTLE -> dismantleStationHome();
            case StationEditorModule.ROOT_RECIPES -> recipeHome();
            case StationEditorModule.ROOT_RECIPES_DISMANTLE -> dismantleRecipeHome();
            default -> queueCostsHome();
        };
    }

    private static List<EditorFieldSpec> stationHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("station_display_name", "editor.field.display_name", "display_name"));
        specs.add(EditorFieldSpec.text("station_layout", "editor.field.layout", "layout"));
        specs.add(EditorFieldSpec.text("station_preview_layout", "editor.field.preview_layout",
                "preview_layout"));
        specs.add(EditorFieldSpec.text("station_queue_layout", "editor.field.queue_layout", "queue_layout"));
        specs.add(EditorFieldSpec.text("station_permission", "editor.field.permission", "permission"));
        specs.add(navigate("station_queue_nav", "editor.field.queue", STATION_QUEUE, "queue"));
        specs.add(navigate("station_channels_nav", "editor.field.channels", STATION_CHANNELS, "channels"));
        specs.add(navigate("station_output_nav", "editor.field.output", STATION_OUTPUT, "output"));
        specs.add(navigate("station_condition_nav", "editor.field.condition", CONDITION, "condition"));
        return specs;
    }

    private static List<EditorFieldSpec> dismantleStationHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("dismantle_station_display_name", "editor.field.display_name",
                "display_name"));
        specs.add(EditorFieldSpec.text("dismantle_station_layout", "editor.field.layout", "layout"));
        specs.add(EditorFieldSpec.text("dismantle_station_permission", "editor.field.permission",
                "permission"));
        specs.add(navigate("dismantle_station_output_nav", "editor.field.dismantle_output",
                DISMANTLE_OUTPUT, "output"));
        specs.add(navigate("dismantle_station_condition_nav", "editor.field.condition", CONDITION,
                "condition"));
        return specs;
    }

    private static List<EditorFieldSpec> recipeHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("recipe_display_name", "editor.field.display_name", "display_name"));
        specs.add(EditorFieldSpec.list("recipe_station_ids", "editor.field.station_ids",
                LIST_ENTRIES, "station_ids"));
        specs.add(EditorFieldSpec.list("recipe_tags", "editor.field.tags", LIST_ENTRIES, "tags"));
        specs.add(EditorFieldSpec.text("recipe_permission", "editor.field.permission", "permission"));
        specs.add(EditorFieldSpec.toggle("recipe_visible", "editor.field.visible", "visible"));
        specs.add(EditorFieldSpec.number("recipe_duration", "editor.field.duration_seconds",
                "duration_seconds"));
        specs.add(navigate("recipe_cost_nav", "editor.field.cost", RECIPE_COST, "cost"));
        specs.add(EditorFieldSpec.list("recipe_materials", "editor.field.materials",
                RECIPE_MATERIAL_ENTRY, "materials"));
        specs.add(EditorFieldSpec.list("recipe_outputs", "editor.field.result_outputs",
                LIST_ENTRIES, "result.outputs"));
        specs.add(navigate("recipe_condition_nav", "editor.field.recipe_condition", CONDITION, "condition"));
        specs.add(navigate("recipe_display_condition_nav", "editor.field.display_condition",
                DISPLAY_CONDITION, "display_condition"));
        specs.add(navigate("recipe_actions_nav", "editor.field.recipe_actions", RECIPE_ACTIONS, "actions"));
        return specs;
    }

    private static List<EditorFieldSpec> dismantleRecipeHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("dismantle_recipe_display_name", "editor.field.display_name",
                "display_name"));
        specs.add(EditorFieldSpec.text("dismantle_recipe_station_id", "editor.field.station_id",
                "station_id"));
        specs.add(EditorFieldSpec.list("dismantle_recipe_tags", "editor.field.tags", LIST_ENTRIES, "tags"));
        specs.add(EditorFieldSpec.text("dismantle_recipe_permission", "editor.field.permission",
                "permission"));
        specs.add(navigate("dismantle_recipe_cost_nav", "editor.field.dismantle_cost",
                RECIPE_COST, "cost"));
        specs.add(EditorFieldSpec.list("dismantle_recipe_item_sources", "editor.field.item_sources",
                LIST_ENTRIES, "item_sources"));
        specs.add(EditorFieldSpec.list("dismantle_recipe_pool", "editor.field.pool", LIST_ENTRIES, "pool"));
        specs.add(navigate("dismantle_recipe_rolls_nav", "editor.field.rolls", DISMANTLE_ROLLS, "rolls"));
        specs.add(navigate("dismantle_recipe_condition_nav", "editor.field.condition", CONDITION,
                "condition"));
        return specs;
    }

    private static List<EditorFieldSpec> queueCostsHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.list("queue_costs_tiers", "editor.field.tiers",
                QUEUE_COST_TIER_ENTRY, "tiers"));
        specs.add(navigate("queue_costs_fallback_nav", "editor.field.queue_costs_fallback",
                QUEUE_COSTS_FALLBACK, "fallback"));
        specs.add(navigate("queue_costs_batch_nav", "editor.field.queue_costs_batch",
                QUEUE_COSTS_BATCH, "batch"));
        return specs;
    }

    private static List<EditorFieldSpec> stationQueue() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.number("queue_base_length", "editor.field.queue_base_length",
                "queue", "base_length"));
        specs.add(EditorFieldSpec.number("queue_max_length", "editor.field.queue_max_length",
                "queue", "max_length"));
        specs.add(EditorFieldSpec.cycle("queue_progress_mode", "editor.field.queue_progress_mode",
                PROGRESS_MODES, "queue", "progress_mode"));
        specs.add(EditorFieldSpec.number("queue_cancel_refund_rate",
                "editor.field.queue_cancel_refund_rate", "queue", "cancel_refund_rate"));
        specs.add(EditorFieldSpec.number("queue_speed_multiplier", "editor.field.queue_speed_multiplier",
                "queue", "speed_multiplier"));
        specs.add(EditorFieldSpec.toggle("queue_allow_purchase", "editor.field.queue_allow_purchase",
                "queue", "allow_purchase"));
        return specs;
    }

    private static List<EditorFieldSpec> stationChannels() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("channel_backpack", "editor.field.channel_backpack",
                "channels", "backpack"));
        specs.add(EditorFieldSpec.toggle("channel_storage", "editor.field.channel_storage",
                "channels", "storage"));
        specs.add(EditorFieldSpec.cycle("channel_default", "editor.field.channel_default",
                MATERIAL_CHANNELS, "channels", "default"));
        return specs;
    }

    private static List<EditorFieldSpec> stationOutput() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("output_default", "editor.field.output_default",
                OUTPUT_ROUTINGS, "output", "default"));
        specs.add(EditorFieldSpec.toggle("output_player_switchable",
                "editor.field.output_player_switchable", "output", "player_switchable"));
        return specs;
    }

    private static List<EditorFieldSpec> dismantleOutput() {
        return List.of(EditorFieldSpec.cycle("output_default", "editor.field.output_default",
                OUTPUT_ROUTINGS, "output", "default"));
    }

    private static List<EditorFieldSpec> condition() {
        return List.of(EditorFieldSpec.toggle("condition_invalid", "editor.field.condition_invalid",
                "condition", "invalid_as_failure"));
    }

    private static List<EditorFieldSpec> displayCondition() {
        return List.of(EditorFieldSpec.toggle("condition_invalid", "editor.field.condition_invalid",
                "display_condition", "invalid_as_failure"));
    }

    private static List<EditorFieldSpec> recipeCost() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("cost_currency_type", "editor.field.currency_type",
                CURRENCY_TYPES, "cost", "currency", "type"));
        specs.add(EditorFieldSpec.number("cost_currency_amount", "editor.field.currency_amount",
                "cost", "currency", "amount"));
        return specs;
    }

    private static List<EditorFieldSpec> recipeActions() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.list("actions_pre", "editor.field.actions_pre",
                LIST_ENTRIES, "actions.pre"));
        specs.add(EditorFieldSpec.list("actions_success", "editor.field.actions_success",
                LIST_ENTRIES, "actions.success"));
        specs.add(EditorFieldSpec.list("actions_failure", "editor.field.actions_failure",
                LIST_ENTRIES, "actions.failure"));
        return specs;
    }

    private static List<EditorFieldSpec> dismantleRolls() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.number("rolls_min", "editor.field.rolls_min", "rolls", "min"));
        specs.add(EditorFieldSpec.number("rolls_max", "editor.field.rolls_max", "rolls", "max"));
        return specs;
    }

    private static List<EditorFieldSpec> queueCostsFallback() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("fallback_currency_type", "editor.field.currency_type",
                CURRENCY_TYPES, "fallback", "currency", "type"));
        specs.add(EditorFieldSpec.text("fallback_currency_amount", "editor.field.currency_amount_expr",
                "fallback", "currency", "amount"));
        specs.add(EditorFieldSpec.text("fallback_item_source", "editor.field.item_source",
                "fallback", "item", "source"));
        specs.add(EditorFieldSpec.number("fallback_item_amount", "editor.field.item_amount",
                "fallback", "item", "amount"));
        specs.add(EditorFieldSpec.number("fallback_max_amount", "editor.field.max_amount",
                "fallback", "max_amount"));
        return specs;
    }

    private static List<EditorFieldSpec> queueCostsBatch() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("batch_enabled", "editor.field.batch_enabled",
                "batch", "enabled"));
        specs.add(EditorFieldSpec.list("batch_options", "editor.field.batch_options",
                LIST_ENTRIES, "batch.options"));
        return specs;
    }

    private static List<EditorFieldSpec> materialEntry(EditorSession session, int index) {
        String position = String.valueOf(index);
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.text("material_id", "editor.field.material_id",
                "materials", position, "material_id"));
        curated.add(EditorFieldSpec.text("requirement_id", "editor.field.requirement_id",
                "materials", position, "requirement_id"));
        curated.add(EditorFieldSpec.text("count_key", "editor.field.count_key",
                "materials", position, "count_key"));
        curated.add(EditorFieldSpec.list("material_item_sources", "editor.field.item_sources",
                LIST_ENTRIES, "materials." + position + ".item_sources"));
        curated.add(EditorFieldSpec.number("material_amount", "editor.field.material_amount",
                "materials", position, "amount"));
        curated.add(EditorFieldSpec.toggle("material_consume", "editor.field.consume",
                "materials", position, "consume"));
        return withStructural(session, curated, "materials", position);
    }

    private static List<EditorFieldSpec> tierEntry(EditorSession session, int index) {
        String position = String.valueOf(index);
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.text("tier_count_range", "editor.field.count_range",
                "tiers", position, "count_range"));
        curated.add(EditorFieldSpec.cycle("tier_currency_type", "editor.field.currency_type",
                CURRENCY_TYPES, "tiers", position, "currency", "type"));
        curated.add(EditorFieldSpec.number("tier_currency_amount", "editor.field.currency_amount",
                "tiers", position, "currency", "amount"));
        curated.add(EditorFieldSpec.text("tier_item_source", "editor.field.item_source",
                "tiers", position, "item", "source"));
        curated.add(EditorFieldSpec.number("tier_item_amount", "editor.field.item_amount",
                "tiers", position, "item", "amount"));
        return withStructural(session, curated, "tiers", position);
    }

    private static List<EditorFieldSpec> withStructural(EditorSession session,
            List<EditorFieldSpec> curated, String... base) {
        YamlTextDocument draft = session.draft(EditorSession.SCOPE_DEFAULT);
        List<EditorFieldSpec> combined = new ArrayList<>(curated);
        combined.addAll(EditorRenderer.structuralSpecs(draft, base, curated));
        return combined;
    }

    private static int entryIndex(EditorSession session) {
        try {
            return Math.max(0, Integer.parseInt(session.context(ENTRY_INDEX_KEY)));
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    private static EditorMenuMeta menu(String menuId, String... structuralRoot) {
        return new EditorMenuMeta(menuId, titleKey(menuId), HOME, structuralRoot, true);
    }

    private static EditorFieldSpec navigate(String id, String labelKey, String targetMenu, String path) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey, new String[]{path},
                List.of(), targetMenu, null, null);
    }
}
