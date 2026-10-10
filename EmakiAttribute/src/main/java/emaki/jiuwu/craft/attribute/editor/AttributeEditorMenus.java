package emaki.jiuwu.craft.attribute.editor;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorRenderer;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;

public final class AttributeEditorMenus {

    public static final String HOME = "home";
    public static final String CONDITION = "condition";
    public static final String CONDITION_ENTRIES = "condition_entries";
    public static final String DAMAGE_STAGE = "damage_stage";
    public static final String DAMAGE_RECOVERY = "damage_recovery";
    public static final String LIST_ENTRIES = EditorService.MENU_LIST_ENTRIES;

    public static final String ENTRY_INDEX_KEY = "entry_index";

    public static final List<String> VALUE_KINDS =
            List.of("FLAT", "PERCENT", "CHANCE", "REGEN", "RESOURCE", "DERIVED");
    public static final List<String> CONDITION_TYPES = List.of("all_of", "any_of");
    public static final List<String> RULE_ENTRY_TYPES =
            List.of("pdc_meta", "pdc_attribute", "lore_regex", "source_id");
    public static final List<String> STAGE_KINDS = List.of("FLAT_PERCENT", "CUSTOM");
    public static final List<String> STAGE_SOURCES = List.of("ATTACKER", "TARGET", "CONTEXT");
    public static final List<String> STAGE_MODES = List.of("ADD", "SUBTRACT");

    private AttributeEditorMenus() {
    }

    public static EditorMenuMeta meta(String menuId) {
        if (menuId == null) {
            return null;
        }
        return switch (menuId) {
            case HOME -> new EditorMenuMeta(menuId, titleKey(menuId), null, new String[0], true);
            case CONDITION -> menu(menuId, "condition");
            case DAMAGE_RECOVERY -> menu(menuId, "recovery");
            case CONDITION_ENTRIES, DAMAGE_STAGE, LIST_ENTRIES ->
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
            case CONDITION_ENTRIES -> conditionEntry(session, entryIndex(session));
            case DAMAGE_STAGE -> damageStage(session, entryIndex(session));
            case DAMAGE_RECOVERY -> damageRecovery();
            default -> List.of();
        };
    }

    private static List<EditorFieldSpec> home(EditorSession session) {
        return switch (AttributeEditorModule.docType(session.document().path())) {
            case AttributeEditorModule.ROOT_CONDITIONS -> conditionHome();
            case AttributeEditorModule.ROOT_DAMAGE_TYPES -> damageTypeHome();
            case AttributeEditorModule.ROOT_LORE_FORMATS -> loreFormatHome();
            case AttributeEditorModule.ROOT_PRESETS -> presetHome();
            case AttributeEditorModule.ROOT_ATTRIBUTE_BALANCE -> balanceHome();
            default -> attributeHome();
        };
    }

    private static List<EditorFieldSpec> attributeHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("attr_display_name", "editor.field.display_name", "display_name"));
        specs.add(EditorFieldSpec.cycle("attr_value_kind", "editor.field.value_kind",
                VALUE_KINDS, "value_kind"));
        specs.add(EditorFieldSpec.text("attr_target_type", "editor.field.target_type", "target_type"));
        specs.add(EditorFieldSpec.text("attr_target_id", "editor.field.target_id", "target_id"));
        specs.add(EditorFieldSpec.number("attr_default_value", "editor.field.default_value", "default_value"));
        specs.add(EditorFieldSpec.number("attr_min_value", "editor.field.min_value", "min_value"));
        specs.add(EditorFieldSpec.number("attr_max_value", "editor.field.max_value", "max_value"));
        specs.add(EditorFieldSpec.toggle("attr_allow_negative", "editor.field.allow_negative",
                "allow_negative"));
        specs.add(EditorFieldSpec.number("attr_priority", "editor.field.priority", "priority"));
        specs.add(EditorFieldSpec.text("attr_lore_format_id", "editor.field.lore_format_id",
                "lore_format_id"));
        specs.add(EditorFieldSpec.number("attr_attribute_power", "editor.field.attribute_power",
                "attribute_power"));
        specs.add(EditorFieldSpec.toggle("attr_parent_attribute", "editor.field.parent_attribute",
                "parent_attribute"));
        specs.add(navigate("attr_child_bonuses", "editor.field.child_bonuses",
                "#child_bonuses", "child_bonuses"));
        specs.add(EditorFieldSpec.text("attr_description", "editor.field.description", "description"));
        return specs;
    }

    private static List<EditorFieldSpec> conditionHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("rule_enabled", "editor.field.enabled", "enabled"));
        specs.add(EditorFieldSpec.text("rule_source_id", "editor.field.source_id", "source_id"));
        specs.add(navigate("rule_condition_nav", "editor.field.condition", CONDITION, "condition"));
        return specs;
    }

    private static List<EditorFieldSpec> damageTypeHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("damage_display_name", "editor.field.display_name", "display_name"));
        specs.add(EditorFieldSpec.list("damage_aliases", "editor.field.aliases",
                LIST_ENTRIES, "aliases"));
        specs.add(EditorFieldSpec.list("damage_allowed_events", "editor.field.allowed_events",
                LIST_ENTRIES, "allowed_events"));
        specs.add(EditorFieldSpec.toggle("damage_hard_lock", "editor.field.hard_lock", "hard_lock"));
        specs.add(EditorFieldSpec.list("damage_stages", "editor.field.stages",
                DAMAGE_STAGE, "stages"));
        specs.add(navigate("damage_recovery_nav", "editor.field.recovery",
                DAMAGE_RECOVERY, "recovery"));
        specs.add(EditorFieldSpec.text("damage_attacker_message", "editor.field.attacker_message",
                "attacker_message"));
        specs.add(EditorFieldSpec.text("damage_target_message", "editor.field.target_message",
                "target_message"));
        specs.add(EditorFieldSpec.text("damage_description", "editor.field.description", "description"));
        return specs;
    }

    private static List<EditorFieldSpec> loreFormatHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("format_pattern", "editor.field.format", "format"));
        specs.add(EditorFieldSpec.number("format_precision", "editor.field.precision", "precision"));
        specs.add(EditorFieldSpec.number("format_read_priority", "editor.field.read_priority",
                "read_priority"));
        specs.add(EditorFieldSpec.list("format_read_patterns", "editor.field.read_patterns",
                LIST_ENTRIES, "read_patterns"));
        return specs;
    }

    private static List<EditorFieldSpec> presetHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("preset_display_name", "editor.field.display_name", "display_name"));
        specs.add(EditorFieldSpec.number("preset_priority", "editor.field.priority", "priority"));
        specs.add(navigate("preset_values_nav", "editor.field.values", "#values", "values"));
        specs.add(EditorFieldSpec.text("preset_description", "editor.field.description", "description"));
        return specs;
    }

    private static List<EditorFieldSpec> balanceHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(navigate("balance_attributes_nav", "editor.field.balance_attributes",
                "#attributes", "attributes"));
        specs.add(navigate("balance_scores_nav", "editor.field.balance_scores",
                "#scores", "scores"));
        return specs;
    }

    private static List<EditorFieldSpec> condition() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("cond_type", "editor.field.cond_type",
                CONDITION_TYPES, "condition", "type"));
        specs.add(EditorFieldSpec.toggle("cond_invalid", "editor.field.condition_invalid",
                "condition", "invalid_as_failure"));
        specs.add(EditorFieldSpec.list("cond_entries", "editor.field.cond_entries",
                CONDITION_ENTRIES, "condition.entries"));
        return specs;
    }

    private static List<EditorFieldSpec> conditionEntry(EditorSession session, int index) {
        String position = String.valueOf(index);
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.cycle("cond_entry_type", "editor.field.cond_entry_type",
                RULE_ENTRY_TYPES, "condition", "entries", position, "type"));
        curated.add(EditorFieldSpec.text("cond_entry_key", "editor.field.cond_entry_key",
                "condition", "entries", position, "key"));
        curated.add(EditorFieldSpec.text("cond_entry_expression", "editor.field.cond_entry_expression",
                "condition", "entries", position, "condition"));
        curated.add(EditorFieldSpec.text("cond_entry_pattern", "editor.field.cond_entry_pattern",
                "condition", "entries", position, "pattern"));
        curated.add(EditorFieldSpec.toggle("cond_entry_require_match", "editor.field.cond_entry_require_match",
                "condition", "entries", position, "require_match"));
        return withStructural(session, curated, "condition", "entries", position);
    }

    private static List<EditorFieldSpec> damageStage(EditorSession session, int index) {
        String position = String.valueOf(index);
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.text("stage_id", "editor.field.stage_id",
                "stages", position, "id"));
        curated.add(EditorFieldSpec.cycle("stage_kind", "editor.field.stage_kind",
                STAGE_KINDS, "stages", position, "kind"));
        curated.add(EditorFieldSpec.cycle("stage_source", "editor.field.stage_source",
                STAGE_SOURCES, "stages", position, "source"));
        curated.add(EditorFieldSpec.cycle("stage_mode", "editor.field.stage_mode",
                STAGE_MODES, "stages", position, "mode"));
        curated.add(EditorFieldSpec.list("stage_flat_attributes", "editor.field.flat_attributes",
                LIST_ENTRIES, "stages." + position + ".flat_attributes"));
        curated.add(EditorFieldSpec.list("stage_percent_attributes", "editor.field.percent_attributes",
                LIST_ENTRIES, "stages." + position + ".percent_attributes"));
        return withStructural(session, curated, "stages", position);
    }

    private static List<EditorFieldSpec> damageRecovery() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("recovery_source", "editor.field.recovery_source",
                STAGE_SOURCES, "recovery", "source"));
        specs.add(EditorFieldSpec.cycle("recovery_resistance_source", "editor.field.recovery_resistance_source",
                STAGE_SOURCES, "recovery", "resistance_source"));
        specs.add(EditorFieldSpec.list("recovery_flat_attributes", "editor.field.flat_attributes",
                LIST_ENTRIES, "recovery.flat_attributes"));
        specs.add(EditorFieldSpec.list("recovery_percent_attributes", "editor.field.percent_attributes",
                LIST_ENTRIES, "recovery.percent_attributes"));
        specs.add(EditorFieldSpec.list("recovery_resistance_attributes", "editor.field.resistance_attributes",
                LIST_ENTRIES, "recovery.resistance_attributes"));
        return specs;
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

    private static EditorFieldSpec navigate(String id, String labelKey, String targetMenu, String cover) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey, new String[]{cover},
                List.of(), targetMenu, null, null);
    }
}
