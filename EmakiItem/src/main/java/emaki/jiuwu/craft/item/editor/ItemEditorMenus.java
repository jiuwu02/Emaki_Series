package emaki.jiuwu.craft.item.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorSession;

public final class ItemEditorMenus {

    public static final String HOME = "home";
    public static final String BASIC = "basic";
    public static final String COMPONENTS = "components";
    public static final String COMPONENT_VALUE = "component_value";
    public static final String EFFECTS = "effects";
    public static final String EFFECT_EDIT = "effect_edit";
    public static final String SET = "set";
    public static final String SET_LIST = "set_list";
    public static final String SET_EDITOR = "set_editor";
    public static final String SET_THRESHOLD = "set_threshold";
    public static final String CONDITION = "condition";
    public static final String CONDITION_ENTRIES = "condition_entries";
    public static final String REPAIR = "repair";
    public static final String REPAIR_MATERIALS = "repair_materials";
    public static final String REPAIR_ECONOMY = "repair_economy";
    public static final String UPDATE = "update";
    public static final String ACTIONS = "actions";
    public static final String ACTION_LINES = "action_lines";
    public static final String LIST_ENTRIES = "list_entries";

    public static final String TEMPLATE_HOME = "item_editor_gui";
    public static final String TEMPLATE_PAGE = "item_editor_page_gui";

    public static final List<String> EQUIP_SLOTS = List.of(
            "all", "hand", "main_hand", "off_hand", "helmet", "chestplate", "leggings", "boots");

    public static final List<String> EFFECT_TYPES = List.of(
            "variables", "ea_attribute", "es_skill", "accessory_slot", "name_action", "lore_action");

    public static final List<String> SET_THRESHOLD_EFFECT_TYPES = List.of(
            "ea_attribute", "es_skill", "name_action", "lore_action");

    public static final List<String> CONDITION_TYPES = List.of("all_of", "any_of");

    public static final List<String> UPDATE_TRIGGERS = List.of(
            "join", "held_change", "inventory_click", "inventory_drag", "pickup", "interact", "command");

    public static final List<String> ACTION_TRIGGERS = List.of(
            "give", "interact", "damage_dealt", "left_click_air", "right_click_air", "right_click_block",
            "left_click_entity", "consume", "break_block", "place_block", "attack", "damaged",
            "damaged_by_entity", "death", "kill_entity", "kill_player", "shoot_bow", "arrow_hit",
            "arrow_land", "shoot_trident", "trident_hit", "trident_land", "drop_item", "shift_drop_item",
            "swap_items", "shift_swap_items", "login", "sneak", "teleport", "timer", "combo_attack",
            "left_click", "right_click", "shift_left_click", "shift_right_click", "drop_q");

    private record MenuMeta(String titleKey, String parent, String template, boolean paginated) {
    }

    private static final Map<String, MenuMeta> MENUS = buildMenus();

    private ItemEditorMenus() {
    }

    public static String template(String menuId) {
        MenuMeta meta = MENUS.get(menuId);
        return meta == null ? TEMPLATE_PAGE : meta.template();
    }

    public static String titleKey(String menuId) {
        MenuMeta meta = MENUS.get(menuId);
        return meta == null ? menuId : meta.titleKey();
    }

    public static EditorMenuMeta meta(String menuId) {
        MenuMeta meta = MENUS.get(menuId);
        if (meta == null) {
            return null;
        }
        return new EditorMenuMeta(menuId, meta.titleKey(), meta.parent(), null, meta.paginated());
    }

    public static List<EditorFieldSpec> specs(String menuId, EditorSession session) {
        return switch (menuId) {
            case BASIC -> basic();
            case UPDATE -> update();
            case SET -> set();
            case SET_EDITOR -> setEditor();
            case EFFECT_EDIT -> effectEdit(entryIndex(session));
            case REPAIR_MATERIALS -> repairMaterial(entryIndex(session));
            case CONDITION_ENTRIES -> conditionEntry(entryIndex(session));
            case CONDITION -> condition();
            case REPAIR -> repair();
            case REPAIR_ECONOMY -> repairEconomy();
            case COMPONENTS -> List.of();
            case EFFECTS -> effects();
            case ACTIONS -> actions();
            default -> List.of();
        };
    }

    private static int entryIndex(EditorSession session) {
        try {
            return Math.max(0, Integer.parseInt(session.context("entry_index")));
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    public static List<EditorFieldSpec> repairMaterial(int index) {
        List<EditorFieldSpec> specs = new ArrayList<>();
        String prefix = "repair.materials." + index + ".";
        specs.add(EditorFieldSpec.number("mat_amount", "editor.field.mat_amount", prefix + "amount"));
        specs.add(EditorFieldSpec.text("mat_restore", "editor.field.mat_restore", prefix + "restore"));
        specs.add(EditorFieldSpec.list("mat_item_sources", "editor.field.mat_item_sources", LIST_ENTRIES,
                prefix + "item_sources"));
        specs.add(EditorFieldSpec.cycle("mat_matcher_type", "editor.field.mat_matcher_type",
                List.of("component", "flag", "enchantment", "item", "potion", "regex"),
                prefix + "matcher", "type"));
        specs.add(EditorFieldSpec.text("mat_matcher_component", "editor.field.mat_matcher_component",
                prefix + "matcher", "component"));
        specs.add(EditorFieldSpec.cycle("mat_matcher_operator", "editor.field.mat_matcher_operator",
                List.of("equals", "not_equals", "greater", "greater_or_equal", "less", "less_or_equal",
                        "contains", "starts_with", "ends_with", "regex", "has_key", "has_value", "size",
                        "exists", "absent"),
                prefix + "matcher", "operator"));
        specs.add(EditorFieldSpec.text("mat_matcher_value", "editor.field.mat_matcher_value",
                prefix + "matcher", "value"));
        return specs;
    }

    public static List<EditorFieldSpec> conditionEntry(int index) {
        List<EditorFieldSpec> specs = new ArrayList<>();
        String prefix = "condition.entries." + index + ".";
        specs.add(EditorFieldSpec.cycle("cond_type", "editor.field.cond_type",
                List.of("expression", "group"), prefix + "type"));
        specs.add(EditorFieldSpec.text("cond_expression", "editor.field.cond_expression",
                prefix + "expression"));
        specs.add(EditorFieldSpec.number("cond_required_count", "editor.field.cond_required_count",
                prefix + "required_count"));
        specs.add(EditorFieldSpec.list("cond_nested", "editor.field.cond_nested", LIST_ENTRIES,
                prefix + "entries"));
        return specs;
    }

    private static Map<String, MenuMeta> buildMenus() {
        Map<String, MenuMeta> menus = new LinkedHashMap<>();
        menus.put(HOME, new MenuMeta("editor.menu.home", HOME, TEMPLATE_HOME, false));
        menus.put(BASIC, new MenuMeta("editor.menu.basic", HOME, TEMPLATE_PAGE, false));
        menus.put(COMPONENTS, new MenuMeta("editor.menu.components", HOME, TEMPLATE_PAGE, true));
        menus.put(COMPONENT_VALUE, new MenuMeta("editor.menu.component_value", COMPONENTS, TEMPLATE_PAGE, true));
        menus.put(EFFECTS, new MenuMeta("editor.menu.effects", HOME, TEMPLATE_PAGE, true));
        menus.put(EFFECT_EDIT, new MenuMeta("editor.menu.effect_edit", EFFECTS, TEMPLATE_PAGE, true));
        menus.put(SET, new MenuMeta("editor.menu.set", HOME, TEMPLATE_PAGE, false));
        menus.put(SET_LIST, new MenuMeta("editor.menu.set_list", SET, TEMPLATE_PAGE, true));
        menus.put(SET_EDITOR, new MenuMeta("editor.menu.set_editor", SET_LIST, TEMPLATE_PAGE, true));
        menus.put(SET_THRESHOLD, new MenuMeta("editor.menu.set_threshold", SET_EDITOR, TEMPLATE_PAGE, true));
        menus.put(CONDITION, new MenuMeta("editor.menu.condition", HOME, TEMPLATE_PAGE, false));
        menus.put(CONDITION_ENTRIES, new MenuMeta("editor.menu.condition_entries", CONDITION, TEMPLATE_PAGE, true));
        menus.put(REPAIR, new MenuMeta("editor.menu.repair", HOME, TEMPLATE_PAGE, false));
        menus.put(REPAIR_MATERIALS, new MenuMeta("editor.menu.repair_materials", REPAIR, TEMPLATE_PAGE, true));
        menus.put(REPAIR_ECONOMY, new MenuMeta("editor.menu.repair_economy", REPAIR, TEMPLATE_PAGE, false));
        menus.put(UPDATE, new MenuMeta("editor.menu.update", HOME, TEMPLATE_PAGE, false));
        menus.put(ACTIONS, new MenuMeta("editor.menu.actions", HOME, TEMPLATE_PAGE, true));
        menus.put(ACTION_LINES, new MenuMeta("editor.menu.action_lines", ACTIONS, TEMPLATE_PAGE, true));
        menus.put(LIST_ENTRIES, new MenuMeta("editor.menu.list_entries", HOME, TEMPLATE_PAGE, true));
        return menus;
    }

    private static List<EditorFieldSpec> basic() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("equip_slot", "editor.field.equip_slot", EQUIP_SLOTS, "equip_slot"));
        specs.add(EditorFieldSpec.text("item_source", "editor.field.item_source", "item", "item_source"));
        return specs;
    }

    private static List<EditorFieldSpec> update() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("update_enabled", "editor.field.update_enabled", "update", "enabled"));
        specs.add(EditorFieldSpec.number("update_version", "editor.field.update_version", "update", "version"));
        specs.add(EditorFieldSpec.toggle("preserve_amount", "editor.field.preserve_amount",
                "update", "preserve_amount"));
        specs.add(EditorFieldSpec.toggle("preserve_damage", "editor.field.preserve_damage",
                "update", "preserve_damage"));
        specs.add(EditorFieldSpec.toggle("preserve_unknown", "editor.field.preserve_unknown",
                "update", "preserve_unknown_attribute_sources"));
        for (String trigger : UPDATE_TRIGGERS) {
            specs.add(EditorFieldSpec.toggle("trigger_" + trigger, "editor.field.trigger." + trigger,
                    "update", "triggers", trigger));
        }
        return specs;
    }

    private static List<EditorFieldSpec> setEditor() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("set_display_name", "editor.field.set_display_name", "display_name"));
        specs.add(EditorFieldSpec.text("set_pieces", "editor.field.set_pieces", "pieces"));
        specs.add(EditorFieldSpec.text("set_thresholds", "editor.field.set_thresholds", "thresholds"));
        specs.add(EditorFieldSpec.text("set_lore_header", "editor.field.set_lore_header", "lore", "header"));
        specs.add(EditorFieldSpec.text("set_lore_equipped", "editor.field.set_lore_equipped",
                "lore", "equipped_format"));
        specs.add(EditorFieldSpec.text("set_lore_missing", "editor.field.set_lore_missing",
                "lore", "missing_format"));
        specs.add(EditorFieldSpec.text("set_lore_active", "editor.field.set_lore_active",
                "lore", "active_threshold_format"));
        specs.add(EditorFieldSpec.text("set_lore_inactive", "editor.field.set_lore_inactive",
                "lore", "inactive_threshold_format"));
        specs.add(EditorFieldSpec.text("set_lore_separator", "editor.field.set_lore_separator",
                "lore", "separator"));
        return specs;
    }

    private static List<EditorFieldSpec> set() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("set_id", "editor.field.set_id", "set", "id"));
        specs.add(EditorFieldSpec.text("set_piece", "editor.field.set_piece", "set", "piece"));
        specs.add(EditorFieldSpec.navigate("set_list", "editor.field.manage_sets", SET_LIST));
        return specs;
    }

    private static List<EditorFieldSpec> condition() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("condition_type", "editor.field.condition_type",
                CONDITION_TYPES, "condition", "type"));
        specs.add(EditorFieldSpec.number("condition_required_count", "editor.field.condition_required_count",
                "condition", "required_count"));
        specs.add(EditorFieldSpec.toggle("condition_invalid", "editor.field.condition_invalid",
                "condition", "invalid_as_failure"));
        specs.add(EditorFieldSpec.text("condition_fail_message", "editor.field.condition_fail_message",
                "condition", "on_fail", "message"));
        specs.add(EditorFieldSpec.toggle("condition_block_output", "editor.field.condition_block_output",
                "condition", "on_fail", "block_output"));
        specs.add(EditorFieldSpec.list("condition_entries", "editor.field.condition_entries",
                CONDITION_ENTRIES, "condition.entries"));
        specs.add(EditorFieldSpec.list("condition_pass_actions", "editor.field.condition_pass_actions",
                ACTION_LINES, "condition.on_pass.actions"));
        specs.add(EditorFieldSpec.list("condition_fail_actions", "editor.field.condition_fail_actions",
                ACTION_LINES, "condition.on_fail.actions"));
        return specs;
    }

    private static List<EditorFieldSpec> repair() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("repair_enabled", "editor.field.repair_enabled", "repair", "enabled"));
        specs.add(EditorFieldSpec.text("repair_name_prefix", "editor.field.repair_name_prefix",
                "repair", "disabled_display", "name_prefix"));
        specs.add(EditorFieldSpec.list("repair_materials", "editor.field.repair_materials",
                REPAIR_MATERIALS, "repair.materials"));
        specs.add(EditorFieldSpec.navigate("repair_economy", "editor.field.repair_economy", REPAIR_ECONOMY));
        specs.add(EditorFieldSpec.list("repair_lore_append", "editor.field.repair_lore_append",
                ACTION_LINES, "repair.disabled_display.lore_append"));
        specs.add(EditorFieldSpec.list("repair_on_disabled", "editor.field.repair_on_disabled",
                ACTION_LINES, "repair.on_disabled"));
        specs.add(EditorFieldSpec.list("repair_on_repaired", "editor.field.repair_on_repaired",
                ACTION_LINES, "repair.on_repaired"));
        return specs;
    }

    private static List<EditorFieldSpec> repairEconomy() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("repair_economy_enabled", "editor.field.repair_economy_enabled",
                "repair", "economy", "enabled"));
        specs.add(EditorFieldSpec.text("repair_economy_restore", "editor.field.repair_economy_restore",
                "repair", "economy", "restore"));
        specs.add(EditorFieldSpec.list("repair_currencies", "editor.field.repair_currencies",
                REPAIR_MATERIALS, "repair.economy.currencies"));
        return specs;
    }

    private static List<EditorFieldSpec> effects() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.list("effects_list", "editor.field.effects_list", EFFECT_EDIT, "effects"));
        return specs;
    }

    public static List<EditorFieldSpec> effectEdit(int index) {
        List<EditorFieldSpec> specs = new ArrayList<>();
        String prefix = "effects." + index + ".";
        specs.add(EditorFieldSpec.cycle("effect_type", "editor.field.effect_type", EFFECT_TYPES,
                "effects", Integer.toString(index), "type"));
        specs.add(EditorFieldSpec.list("effect_variables", "editor.field.effect_variables", LIST_ENTRIES,
                prefix + "variables"));
        specs.add(EditorFieldSpec.list("effect_attributes", "editor.field.effect_attributes", LIST_ENTRIES,
                prefix + "ea_attributes"));
        specs.add(EditorFieldSpec.list("effect_skills", "editor.field.effect_skills", LIST_ENTRIES,
                prefix + "es_skills"));
        specs.add(EditorFieldSpec.list("effect_skill_triggers", "editor.field.effect_skill_triggers",
                LIST_ENTRIES, prefix + "es_skill_triggers"));
        specs.add(EditorFieldSpec.list("effect_accessory_slots", "editor.field.effect_accessory_slots",
                LIST_ENTRIES, prefix + "accessory_slots"));
        specs.add(EditorFieldSpec.list("effect_name_actions", "editor.field.effect_name_actions",
                LIST_ENTRIES, prefix + "name_actions"));
        specs.add(EditorFieldSpec.list("effect_lore_actions", "editor.field.effect_lore_actions",
                LIST_ENTRIES, prefix + "lore_actions"));
        return specs;
    }

    private static List<EditorFieldSpec> actions() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.list("actions_list", "editor.field.actions_list", LIST_ENTRIES, "actions"));
        return specs;
    }

    public static boolean setMenu(String menuId) {
        return SET_LIST.equals(menuId) || SET_EDITOR.equals(menuId) || SET_THRESHOLD.equals(menuId);
    }
}
