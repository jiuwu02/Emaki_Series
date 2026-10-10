package emaki.jiuwu.craft.cooking.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorRenderer;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;

public final class CookingEditorMenus {

    public static final String HOME = "home";
    public static final String CONDITION = "condition";
    public static final String RESULT = "result";
    public static final String RESULT_SUCCESS = "result_success";
    public static final String RECIPE_OUTPUT_ENTRY = "recipe_output_entry";
    public static final String RECIPE_INGREDIENT_ENTRY = "recipe_ingredient_entry";
    public static final String RECIPE_INPUT_ENTRY = "recipe_input_entry";
    public static final String LIST_ENTRIES = EditorService.MENU_LIST_ENTRIES;

    public static final String ENTRY_INDEX_KEY = "entry_index";

    public static final List<String> CONDITION_TYPES =
            List.of("all_of", "any_of", "none_of", "at_least", "exactly");

    private CookingEditorMenus() {
    }

    public static EditorMenuMeta meta(String menuId) {
        if (menuId == null) {
            return null;
        }
        return switch (menuId) {
            case HOME -> new EditorMenuMeta(menuId, titleKey(menuId), null, new String[0], true);
            case CONDITION -> new EditorMenuMeta(menuId, titleKey(menuId), HOME,
                    new String[]{"condition"}, true);
            case RESULT -> new EditorMenuMeta(menuId, titleKey(menuId), HOME,
                    new String[]{"result"}, true);
            case RESULT_SUCCESS -> new EditorMenuMeta(menuId, titleKey(menuId), RESULT,
                    new String[]{"result", "success"}, true);
            case RECIPE_OUTPUT_ENTRY, RECIPE_INGREDIENT_ENTRY, RECIPE_INPUT_ENTRY, LIST_ENTRIES ->
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
            case CONDITION -> condition();
            case RESULT -> result();
            case RESULT_SUCCESS -> resultSuccess();
            case RECIPE_OUTPUT_ENTRY -> outputEntry(session, entryIndex(session));
            case RECIPE_INGREDIENT_ENTRY -> ingredientEntry(session, entryIndex(session));
            case RECIPE_INPUT_ENTRY -> inputEntry(session, entryIndex(session));
            default -> List.of();
        };
    }

    private static List<EditorFieldSpec> home(EditorSession session) {
        String docType = docType(session);
        if (CookingEditorModule.ROOT_NUTRITION.equals(docType)) {
            return nutritionHome();
        }
        if (CookingEditorModule.ROOT_ITEM_ADJUSTMENTS.equals(docType)) {
            return adjustmentHome(session);
        }
        if (CookingEditorModule.RECIPE_ROOTS.contains(docType)) {
            return recipeHome(docType);
        }
        return List.of();
    }

    private static List<EditorFieldSpec> recipeHome(String station) {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("recipe_display_name", "editor.field.display_name", "display_name"));
        if (CookingEditorModule.ROOT_WOK.equals(station)) {
            specs.add(EditorFieldSpec.list("recipe_ingredients", "editor.field.ingredients",
                    RECIPE_INGREDIENT_ENTRY, "ingredients"));
        } else if (CookingEditorModule.ROOT_FERMENTATION_BARREL.equals(station)) {
            specs.add(EditorFieldSpec.list("recipe_inputs", "editor.field.inputs",
                    RECIPE_INPUT_ENTRY, "inputs"));
        } else {
            specs.add(navigate("recipe_input_nav", "editor.field.input", "#input", "input"));
        }
        stationFields(specs, station);
        specs.add(EditorFieldSpec.text("recipe_permission", "editor.field.permission", "permission"));
        specs.add(navigate("recipe_condition_nav", "editor.field.condition", CONDITION, "condition"));
        specs.add(navigate("recipe_result_nav", "editor.field.result", RESULT, "result"));
        return specs;
    }

    private static void stationFields(List<EditorFieldSpec> specs, String station) {
        switch (station) {
            case CookingEditorModule.ROOT_CHOPPING_BOARD -> specs.add(EditorFieldSpec.number(
                    "recipe_cuts_required", "editor.field.cuts_required", "cuts_required"));
            case CookingEditorModule.ROOT_WOK -> {
                specs.add(EditorFieldSpec.number("recipe_heat_level", "editor.field.heat_level",
                        "heat_level"));
                specs.add(EditorFieldSpec.number("recipe_stir_total_min", "editor.field.stir_total_min",
                        "stir_total", "min"));
                specs.add(EditorFieldSpec.number("recipe_stir_total_max", "editor.field.stir_total_max",
                        "stir_total", "max"));
                specs.add(EditorFieldSpec.number("recipe_fault_tolerance", "editor.field.fault_tolerance",
                        "fault_tolerance"));
            }
            case CookingEditorModule.ROOT_GRINDER -> specs.add(EditorFieldSpec.number(
                    "recipe_grind_time", "editor.field.grind_time_seconds", "grind_time_seconds"));
            case CookingEditorModule.ROOT_STEAMER -> specs.add(EditorFieldSpec.number(
                    "recipe_required_steam", "editor.field.required_steam", "required_steam"));
            case CookingEditorModule.ROOT_OVEN -> specs.add(EditorFieldSpec.number(
                    "recipe_bake_time", "editor.field.bake_time_seconds", "bake_time_seconds"));
            case CookingEditorModule.ROOT_JUICER -> specs.add(EditorFieldSpec.number(
                    "recipe_presses_required", "editor.field.presses_required", "presses_required"));
            case CookingEditorModule.ROOT_FERMENTATION_BARREL -> specs.add(EditorFieldSpec.number(
                    "recipe_fermentation_time", "editor.field.fermentation_time_seconds",
                    "fermentation_time_seconds"));
            default -> {
            }
        }
    }

    private static List<EditorFieldSpec> nutritionHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("nutrition_display_name", "editor.field.display_name",
                "display_name"));
        specs.add(EditorFieldSpec.number("nutrition_min", "editor.field.nutrition_min", "min"));
        specs.add(EditorFieldSpec.number("nutrition_max", "editor.field.nutrition_max", "max"));
        specs.add(EditorFieldSpec.number("nutrition_default", "editor.field.nutrition_default", "default"));
        return specs;
    }

    private static List<EditorFieldSpec> adjustmentHome(EditorSession session) {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.list("adjustment_item_sources", "editor.field.item_sources",
                LIST_ENTRIES, "item_sources"));
        YamlTextDocument draft = session.draft(EditorSession.SCOPE_DEFAULT);
        if (draft.value("adjustment") instanceof Map<?, ?>) {
            specs.add(navigate("adjustment_shared", "editor.field.adjustment", "#adjustment",
                    "adjustment"));
        }
        if (draft.value("stations") instanceof Map<?, ?>) {
            specs.add(navigate("adjustment_stations", "editor.field.stations", "#stations", "stations"));
        }
        return specs;
    }

    private static List<EditorFieldSpec> condition() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("condition_type", "editor.field.condition_type",
                CONDITION_TYPES, "condition", "type"));
        specs.add(EditorFieldSpec.list("condition_entries", "editor.field.condition_entries",
                LIST_ENTRIES, "condition.entries"));
        return specs;
    }

    private static List<EditorFieldSpec> result() {
        return List.of(navigate("result_success_nav", "editor.field.result_success", RESULT_SUCCESS,
                "result", "success"));
    }

    private static List<EditorFieldSpec> resultSuccess() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.list("success_outputs", "editor.field.outputs",
                RECIPE_OUTPUT_ENTRY, "result.success.outputs"));
        specs.add(EditorFieldSpec.list("success_actions", "editor.field.actions",
                LIST_ENTRIES, "result.success.actions"));
        return specs;
    }

    private static List<EditorFieldSpec> outputEntry(EditorSession session, int index) {
        String position = String.valueOf(index);
        String prefix = "result.success." + position + ".";
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.text("output_item_source", "editor.field.item_source",
                prefix + "item_source"));
        curated.add(EditorFieldSpec.number("output_amount", "editor.field.amount",
                prefix + "amount"));
        return withStructural(session, curated, "result", "success", position);
    }

    private static List<EditorFieldSpec> ingredientEntry(EditorSession session, int index) {
        String position = String.valueOf(index);
        String prefix = "ingredients." + position + ".";
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.list("ingredient_item_sources", "editor.field.item_sources",
                LIST_ENTRIES, prefix + "item_sources"));
        curated.add(EditorFieldSpec.number("ingredient_amount", "editor.field.amount",
                prefix + "amount"));
        curated.add(EditorFieldSpec.text("ingredient_stir_rule", "editor.field.stir_rule",
                prefix + "stir_rule"));
        return withStructural(session, curated, "ingredients", position);
    }

    private static List<EditorFieldSpec> inputEntry(EditorSession session, int index) {
        String position = String.valueOf(index);
        String prefix = "inputs." + position + ".";
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.text("input_slot_id", "editor.field.slot_id", prefix + "slot_id"));
        curated.add(EditorFieldSpec.text("input_count_key", "editor.field.count_key",
                prefix + "count_key"));
        curated.add(EditorFieldSpec.list("input_item_sources", "editor.field.item_sources",
                LIST_ENTRIES, prefix + "item_sources"));
        curated.add(EditorFieldSpec.number("input_amount", "editor.field.amount", prefix + "amount"));
        return withStructural(session, curated, "inputs", position);
    }

    private static List<EditorFieldSpec> withStructural(EditorSession session,
            List<EditorFieldSpec> curated, String... base) {
        YamlTextDocument draft = session.draft(EditorSession.SCOPE_DEFAULT);
        List<EditorFieldSpec> combined = new ArrayList<>(curated);
        combined.addAll(EditorRenderer.structuralSpecs(draft, base, curated));
        return combined;
    }

    private static String docType(EditorSession session) {
        return CookingEditorModule.docType(session.document().path());
    }

    private static int entryIndex(EditorSession session) {
        try {
            return Math.max(0, Integer.parseInt(session.context(ENTRY_INDEX_KEY)));
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    private static EditorFieldSpec navigate(String id, String labelKey, String targetMenu,
            String... path) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey, path, List.of(),
                targetMenu, null, null);
    }
}
