package emaki.jiuwu.craft.forge.editor;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorRenderer;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;

public final class ForgeEditorMenus {

    public static final String HOME = "home";
    public static final String QUALITY = "quality";
    public static final String RESULT = "result";
    public static final String ACTIONS = "actions";
    public static final String MATERIAL_ENTRY = "material_entry";
    public static final String LIST_ENTRIES = EditorService.MENU_LIST_ENTRIES;

    public static final String ENTRY_INDEX_KEY = "entry_index";

    private ForgeEditorMenus() {
    }

    public static EditorMenuMeta meta(String menuId) {
        return switch (menuId) {
            case HOME -> new EditorMenuMeta(menuId, "editor.menu." + menuId, null,
                    new String[0], true);
            case QUALITY -> new EditorMenuMeta(menuId, "editor.menu." + menuId, HOME,
                    new String[]{ "quality" }, true);
            case RESULT -> new EditorMenuMeta(menuId, "editor.menu." + menuId, HOME,
                    new String[]{ "result", "success" }, true);
            case ACTIONS -> new EditorMenuMeta(menuId, "editor.menu." + menuId, HOME,
                    new String[]{ "actions" }, true);
            case MATERIAL_ENTRY, LIST_ENTRIES -> new EditorMenuMeta(menuId, "editor.menu." + menuId, HOME,
                    null, true);
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
            case HOME -> home();
            case QUALITY -> quality();
            case RESULT -> result();
            case ACTIONS -> forgeActions();
            case MATERIAL_ENTRY -> materialEntry(session);
            default -> List.of();
        };
    }

    private static List<EditorFieldSpec> home() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("recipe_display_name", "editor.field.display_name", "display_name"));
        specs.add(EditorFieldSpec.number("recipe_forge_capacity", "editor.field.forge_capacity",
                "forge_capacity"));
        specs.add(EditorFieldSpec.list("recipe_blueprint_requirements", "editor.field.blueprint_requirements",
                LIST_ENTRIES, "blueprint_requirements"));
        specs.add(EditorFieldSpec.list("recipe_materials", "editor.field.materials",
                MATERIAL_ENTRY, "materials"));
        specs.add(EditorFieldSpec.number("recipe_optional_material_limit",
                "editor.field.optional_material_limit", "optional_material_limit"));
        specs.add(navigate("recipe_quality", "editor.field.quality", QUALITY, "quality"));
        specs.add(navigate("recipe_result", "editor.field.result", RESULT, "result"));
        specs.add(navigate("recipe_actions", "editor.field.actions", ACTIONS, "actions"));
        return specs;
    }

    private static List<EditorFieldSpec> quality() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("quality_enabled", "editor.field.quality_enabled",
                "quality", "enabled"));
        specs.add(EditorFieldSpec.toggle("quality_guarantee_enabled", "editor.field.guarantee_enabled",
                "quality", "guarantee", "enabled"));
        specs.add(EditorFieldSpec.number("quality_guarantee_attempts", "editor.field.guarantee_attempts",
                "quality", "guarantee", "attempts"));
        specs.add(EditorFieldSpec.text("quality_guarantee_minimum", "editor.field.guarantee_minimum",
                "quality", "guarantee", "minimum"));
        return specs;
    }

    private static List<EditorFieldSpec> result() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.list("result_outputs", "editor.field.outputs",
                LIST_ENTRIES, "result.success.outputs"));
        specs.add(EditorFieldSpec.list("result_name_actions", "editor.field.name_actions",
                LIST_ENTRIES, "result.success.name_actions"));
        specs.add(EditorFieldSpec.list("result_lore_actions", "editor.field.lore_actions",
                LIST_ENTRIES, "result.success.lore_actions"));
        specs.add(EditorFieldSpec.list("result_success_actions", "editor.field.result_actions",
                LIST_ENTRIES, "result.success.actions"));
        return specs;
    }

    private static List<EditorFieldSpec> forgeActions() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.list("actions_pre", "editor.field.actions_pre",
                LIST_ENTRIES, "actions.pre"));
        specs.add(EditorFieldSpec.list("actions_failure", "editor.field.actions_failure",
                LIST_ENTRIES, "actions.failure"));
        return specs;
    }

    private static List<EditorFieldSpec> materialEntry(EditorSession session) {
        int index = entryIndex(session);
        List<EditorFieldSpec> curated = materialCurated(index);
        YamlTextDocument draft = session.draft(EditorSession.SCOPE_DEFAULT);
        List<EditorFieldSpec> combined = new ArrayList<>(curated);
        combined.addAll(EditorRenderer.structuralSpecs(draft,
                new String[]{ "materials", String.valueOf(index) }, curated));
        return combined;
    }

    private static List<EditorFieldSpec> materialCurated(int index) {
        String prefix = "materials." + index + ".";
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("material_id", "editor.field.material_id", prefix + "material_id"));
        specs.add(EditorFieldSpec.text("material_count_key", "editor.field.count_key", prefix + "count_key"));
        specs.add(EditorFieldSpec.text("material_audit_id", "editor.field.audit_id", prefix + "audit_id"));
        specs.add(EditorFieldSpec.list("material_item_sources", "editor.field.item_sources",
                LIST_ENTRIES, prefix + "item_sources"));
        specs.add(EditorFieldSpec.number("material_amount", "editor.field.amount", prefix + "amount"));
        specs.add(EditorFieldSpec.number("material_capacity_cost", "editor.field.capacity_cost",
                prefix + "capacity_cost"));
        specs.add(EditorFieldSpec.list("material_effects", "editor.field.effects",
                LIST_ENTRIES, prefix + "effects"));
        return specs;
    }

    private static int entryIndex(EditorSession session) {
        try {
            return Math.max(0, Integer.parseInt(session.context(ENTRY_INDEX_KEY)));
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    private static EditorFieldSpec navigate(String id, String labelKey, String targetMenu, String... path) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey, path, List.of(),
                targetMenu, null, null);
    }
}
