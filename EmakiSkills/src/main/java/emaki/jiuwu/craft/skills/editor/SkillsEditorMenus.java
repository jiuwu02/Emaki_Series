package emaki.jiuwu.craft.skills.editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;

public final class SkillsEditorMenus {

    public static final String HOME = "home";
    public static final String SKILL_CONDITION = "skill_condition";
    public static final String SKILL_VARIABLES = "skill_variables";
    public static final String SKILL_SCRIPT = "skill_script";
    public static final String SKILL_SCRIPT_ACTIONS = "skill_script_actions";
    public static final String SKILL_UPGRADE = "skill_upgrade";
    public static final String SKILL_UPGRADE_ECONOMY = "skill_upgrade_economy";
    public static final String SKILL_RESOURCE_COST_ENTRY = "skill_resource_cost_entry";
    public static final String LIST_ENTRIES = EditorService.MENU_LIST_ENTRIES;

    public static final String ENTRY_INDEX_KEY = "entry_index";

    public static final List<String> TRIGGER_TYPES = List.of("active", "passive");
    public static final List<String> CONDITION_TYPES = List.of("all_of", "any_of");
    public static final List<String> SCRIPT_MODES = List.of("native", "mythic", "hybrid");
    public static final List<String> FAILURE_PENALTIES = List.of("none", "downgrade", "reset");
    public static final List<String> COST_TYPES = List.of("local-resource", "ea-resource",
            "attribute-check", "auraskills-mana", "mythiclib-mana");
    public static final List<String> COST_OPERATIONS = List.of("consume", "require");

    private SkillsEditorMenus() {
    }

    public static EditorMenuMeta meta(String menuId) {
        return switch (menuId == null ? "" : menuId) {
            case HOME -> new EditorMenuMeta(HOME, "editor.menu.home", HOME, new String[0], true);
            case SKILL_CONDITION -> new EditorMenuMeta(SKILL_CONDITION,
                    "editor.menu." + SKILL_CONDITION, HOME, new String[]{ "condition" }, true);
            case SKILL_VARIABLES -> new EditorMenuMeta(SKILL_VARIABLES,
                    "editor.menu." + SKILL_VARIABLES, HOME, new String[]{ "variables" }, true);
            case SKILL_SCRIPT -> new EditorMenuMeta(SKILL_SCRIPT,
                    "editor.menu." + SKILL_SCRIPT, HOME, new String[]{ "script" }, true);
            case SKILL_SCRIPT_ACTIONS -> new EditorMenuMeta(SKILL_SCRIPT_ACTIONS,
                    "editor.menu." + SKILL_SCRIPT_ACTIONS, HOME, new String[]{ "script", "actions" }, true);
            case SKILL_UPGRADE -> new EditorMenuMeta(SKILL_UPGRADE,
                    "editor.menu." + SKILL_UPGRADE, HOME, new String[]{ "upgrade" }, true);
            case SKILL_UPGRADE_ECONOMY -> new EditorMenuMeta(SKILL_UPGRADE_ECONOMY,
                    "editor.menu." + SKILL_UPGRADE_ECONOMY, HOME,
                    new String[]{ "upgrade", "economy" }, true);
            case SKILL_RESOURCE_COST_ENTRY -> new EditorMenuMeta(SKILL_RESOURCE_COST_ENTRY,
                    "editor.menu." + SKILL_RESOURCE_COST_ENTRY, HOME, null, true);
            case LIST_ENTRIES -> new EditorMenuMeta(LIST_ENTRIES, "editor.menu.list_entries",
                    HOME, null, true);
            default -> null;
        };
    }

    public static String titleKey(String menuId) {
        if (menuId == null || menuId.isEmpty()) {
            return "editor.menu.home";
        }
        return "editor.menu." + menuId;
    }

    public static List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return switch (menuId == null ? "" : menuId) {
            case HOME -> home(session);
            case SKILL_CONDITION -> condition();
            case SKILL_SCRIPT -> script();
            case SKILL_UPGRADE -> upgrade();
            case SKILL_RESOURCE_COST_ENTRY -> resourceCostEntry(entryIndex(session));
            default -> List.of();
        };
    }

    private static List<EditorFieldSpec> home(EditorSession session) {
        return SkillsEditorModule.ROOT_RESOURCES.equals(docType(session))
                ? resourceHome()
                : skillHome();
    }

    private static List<EditorFieldSpec> skillHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("skill_display_name", "editor.field.display_name",
                "display_name"));
        specs.add(EditorFieldSpec.list("skill_description", "editor.field.description",
                LIST_ENTRIES, "description"));
        specs.add(EditorFieldSpec.text("skill_icon_material", "editor.field.icon_material",
                "icon_material"));
        specs.add(EditorFieldSpec.cycle("skill_trigger_type", "editor.field.trigger_type",
                TRIGGER_TYPES, "trigger_type"));
        specs.add(EditorFieldSpec.number("skill_cooldown_ticks", "editor.field.cooldown_ticks",
                "cooldown_ticks"));
        specs.add(EditorFieldSpec.number("skill_global_cooldown_ticks",
                "editor.field.global_cooldown_ticks", "global_cooldown_ticks"));
        specs.add(EditorFieldSpec.list("skill_lore_aliases", "editor.field.lore_aliases",
                LIST_ENTRIES, "lore_aliases"));
        specs.add(EditorFieldSpec.list("skill_tags", "editor.field.tags", LIST_ENTRIES, "tags"));
        specs.add(EditorFieldSpec.list("skill_tab_tags", "editor.field.tab_tags",
                LIST_ENTRIES, "tab_tags"));
        specs.add(EditorFieldSpec.list("skill_conflicting_skills",
                "editor.field.conflicting_skills", LIST_ENTRIES, "conflicting_skills"));
        specs.add(EditorFieldSpec.list("skill_required_skills", "editor.field.required_skills",
                LIST_ENTRIES, "required_skills"));
        specs.add(EditorFieldSpec.toggle("skill_show_in_slots", "editor.field.show_in_slots",
                "show_in_slots"));
        specs.add(EditorFieldSpec.text("skill_ui_category", "editor.field.ui_category",
                "ui_category"));
        specs.add(EditorFieldSpec.number("skill_sort_order", "editor.field.sort_order",
                "sort_order"));
        specs.add(EditorFieldSpec.toggle("skill_enabled", "editor.field.enabled", "enabled"));
        specs.add(navigate("skill_condition", "editor.field.condition", SKILL_CONDITION,
                "condition"));
        specs.add(EditorFieldSpec.list("skill_resource_costs", "editor.field.resource_costs",
                SKILL_RESOURCE_COST_ENTRY, "resource_costs"));
        specs.add(navigate("skill_variables", "editor.field.variables", SKILL_VARIABLES,
                "variables"));
        specs.add(navigate("skill_script", "editor.field.script", SKILL_SCRIPT, "script"));
        specs.add(navigate("skill_upgrade", "editor.field.upgrade", SKILL_UPGRADE, "upgrade"));
        return specs;
    }

    private static List<EditorFieldSpec> resourceHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("resource_display_name", "editor.field.display_name",
                "display_name"));
        specs.add(EditorFieldSpec.number("resource_max", "editor.field.max", "max"));
        specs.add(EditorFieldSpec.number("resource_default_current", "editor.field.default_current",
                "default_current"));
        specs.add(EditorFieldSpec.number("resource_regen_amount", "editor.field.regen_amount",
                "regen_amount"));
        specs.add(EditorFieldSpec.number("resource_regen_interval_ticks",
                "editor.field.regen_interval_ticks", "regen_interval_ticks"));
        specs.add(EditorFieldSpec.number("resource_clamp_min", "editor.field.clamp_min",
                "clamp_min"));
        specs.add(EditorFieldSpec.number("resource_clamp_max", "editor.field.clamp_max",
                "clamp_max"));
        return specs;
    }

    private static List<EditorFieldSpec> condition() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("condition_type", "editor.field.type", CONDITION_TYPES,
                "condition", "type"));
        specs.add(EditorFieldSpec.list("condition_entries", "editor.field.entries",
                LIST_ENTRIES, "condition.entries"));
        return specs;
    }

    private static List<EditorFieldSpec> script() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("script_enabled", "editor.field.enabled",
                "script", "enabled"));
        specs.add(EditorFieldSpec.cycle("script_mode", "editor.field.mode", SCRIPT_MODES,
                "script", "mode"));
        specs.add(EditorFieldSpec.toggle("script_stop_on_failure", "editor.field.stop_on_failure",
                "script", "stop_on_failure"));
        specs.add(navigate("script_actions", "editor.field.actions", SKILL_SCRIPT_ACTIONS,
                "script", "actions"));
        return specs;
    }

    private static List<EditorFieldSpec> upgrade() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("upgrade_enabled", "editor.field.enabled",
                "upgrade", "enabled"));
        specs.add(EditorFieldSpec.number("upgrade_max_level", "editor.field.max_level",
                "upgrade", "max_level"));
        specs.add(EditorFieldSpec.text("upgrade_gui_template", "editor.field.gui_template",
                "upgrade", "gui_template"));
        specs.add(EditorFieldSpec.cycle("upgrade_failure_penalty",
                "editor.field.failure_penalty", FAILURE_PENALTIES, "upgrade", "failure_penalty"));
        specs.add(navigate("upgrade_economy", "editor.field.economy", SKILL_UPGRADE_ECONOMY,
                "upgrade", "economy"));
        return specs;
    }

    private static List<EditorFieldSpec> resourceCostEntry(int index) {
        String position = String.valueOf(index);
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("cost_type", "editor.field.type", COST_TYPES,
                "resource_costs", position, "type"));
        specs.add(EditorFieldSpec.text("cost_target_id", "editor.field.target_id",
                "resource_costs", position, "target_id"));
        specs.add(EditorFieldSpec.number("cost_amount", "editor.field.amount",
                "resource_costs", position, "amount"));
        specs.add(EditorFieldSpec.cycle("cost_operation", "editor.field.operation",
                COST_OPERATIONS, "resource_costs", position, "operation"));
        specs.add(EditorFieldSpec.text("cost_failure_message", "editor.field.failure_message",
                "resource_costs", position, "failure_message"));
        return specs;
    }

    private static int entryIndex(EditorSession session) {
        try {
            return Math.max(0, Integer.parseInt(session.context(ENTRY_INDEX_KEY)));
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    private static String docType(EditorSession session) {
        Path parent = session.document().path().getParent();
        return parent == null || parent.getFileName() == null ? "" : parent.getFileName().toString();
    }

    private static EditorFieldSpec navigate(String id, String labelKey, String targetMenu,
            String... path) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey, path, List.of(),
                targetMenu, null, null);
    }
}
