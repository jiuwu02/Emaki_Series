package emaki.jiuwu.craft.gem.editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorRenderer;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;

public final class GemEditorMenus {

    public static final String HOME = "home";
    public static final String GEM_STAGES = "gem_stages";
    public static final String GEM_INLAY_COST = "gem_inlay_cost";
    public static final String GEM_EXTRACT_COST = "gem_extract_cost";
    public static final String ITEM_SLOT_ENTRY = "item_slot_entry";
    public static final String ITEM_OBTAIN = "item_obtain";
    public static final String RES_CHAIN = "res_chain";
    public static final String RES_EFFECTS = "res_effects";
    public static final String LIST_ENTRIES = EditorService.MENU_LIST_ENTRIES;

    public static final String ENTRY_INDEX_KEY = "entry_index";

    public static final List<String> GEM_TYPES = List.of("attack", "defense", "utility", "universal");
    public static final List<String> CHAIN_MODES = List.of("unordered", "ordered");

    private GemEditorMenus() {
    }

    public static EditorMenuMeta meta(String menuId) {
        return switch (menuId) {
            case GEM_STAGES -> new EditorMenuMeta(menuId, "editor.menu." + menuId, HOME,
                    new String[]{ "stages" }, true);
            case GEM_INLAY_COST -> new EditorMenuMeta(menuId, "editor.menu." + menuId, HOME,
                    new String[]{ "inlay_cost" }, true);
            case GEM_EXTRACT_COST -> new EditorMenuMeta(menuId, "editor.menu." + menuId, HOME,
                    new String[]{ "extract_cost" }, true);
            case ITEM_OBTAIN -> new EditorMenuMeta(menuId, "editor.menu." + menuId, HOME,
                    new String[]{ "obtain" }, true);
            case RES_CHAIN -> new EditorMenuMeta(menuId, "editor.menu." + menuId, HOME,
                    new String[]{ "chain" }, true);
            case RES_EFFECTS -> new EditorMenuMeta(menuId, "editor.menu." + menuId, HOME,
                    new String[]{ "effects" }, true);
            case ITEM_SLOT_ENTRY, LIST_ENTRIES -> new EditorMenuMeta(menuId, "editor.menu." + menuId, HOME,
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
            case HOME -> home(session);
            case GEM_STAGES -> gemStages();
            case RES_CHAIN -> resonanceChain();
            case ITEM_SLOT_ENTRY -> slotEntry(entryIndex(session));
            default -> List.of();
        };
    }

    private static List<EditorFieldSpec> home(EditorSession session) {
        List<EditorFieldSpec> curated = switch (docType(session)) {
            case "gems" -> gemHome();
            case "items" -> itemHome();
            case "resonances" -> resonanceHome();
            default -> List.of();
        };
        YamlTextDocument draft = session.draft(EditorSession.SCOPE_DEFAULT);
        List<EditorFieldSpec> combined = new ArrayList<>(curated);
        combined.addAll(EditorRenderer.structuralSpecs(draft, new String[0], curated));
        return combined;
    }

    private static String docType(EditorSession session) {
        Path parent = session.document().path().getParent();
        return parent == null || parent.getFileName() == null ? "" : parent.getFileName().toString();
    }

    private static int entryIndex(EditorSession session) {
        try {
            return Math.max(0, Integer.parseInt(session.context(ENTRY_INDEX_KEY)));
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    private static List<EditorFieldSpec> gemHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("gem_display_name", "editor.field.display_name", "display_name"));
        specs.add(EditorFieldSpec.cycle("gem_type", "editor.field.gem_type", GEM_TYPES, "gem_type"));
        specs.add(EditorFieldSpec.number("gem_level", "editor.field.level", "level"));
        specs.add(EditorFieldSpec.text("gem_base_item_source", "editor.field.base_item_source",
                "base_item_source"));
        specs.add(EditorFieldSpec.list("gem_effects", "editor.field.effects", LIST_ENTRIES, "effects"));
        specs.add(EditorFieldSpec.list("gem_socket_compatibility", "editor.field.socket_compatibility",
                LIST_ENTRIES, "socket_compatibility"));
        specs.add(navigate("gem_inlay_cost", "editor.field.inlay_cost", GEM_INLAY_COST, "inlay_cost"));
        specs.add(navigate("gem_extract_cost", "editor.field.extract_cost", GEM_EXTRACT_COST, "extract_cost"));
        specs.add(navigate("gem_stages", "editor.field.stages", GEM_STAGES, "stages"));
        return specs;
    }

    private static List<EditorFieldSpec> gemStages() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("gem_stages_enabled", "editor.field.stages_enabled",
                "stages", "enabled"));
        specs.add(EditorFieldSpec.number("gem_stages_max_level", "editor.field.stages_max_level",
                "stages", "max_level"));
        specs.add(EditorFieldSpec.list("gem_stages_levels", "editor.field.stages_levels",
                LIST_ENTRIES, "stages.levels"));
        return specs;
    }

    private static List<EditorFieldSpec> itemHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.list("item_item_sources", "editor.field.item_sources",
                LIST_ENTRIES, "item_sources"));
        specs.add(EditorFieldSpec.list("item_slots", "editor.field.slots", ITEM_SLOT_ENTRY, "slots"));
        specs.add(EditorFieldSpec.list("item_slot_groups", "editor.field.slot_groups",
                LIST_ENTRIES, "slot_groups"));
        specs.add(EditorFieldSpec.list("item_default_open_slots", "editor.field.default_open_slots",
                LIST_ENTRIES, "default_open_slots"));
        specs.add(EditorFieldSpec.list("item_allowed_gem_types", "editor.field.allowed_gem_types",
                LIST_ENTRIES, "allowed_gem_types"));
        specs.add(EditorFieldSpec.number("item_max_same_type", "editor.field.max_same_type",
                "max_same_type"));
        specs.add(EditorFieldSpec.number("item_max_same_id", "editor.field.max_same_id", "max_same_id"));
        specs.add(navigate("item_obtain", "editor.field.obtain", ITEM_OBTAIN, "obtain"));
        return specs;
    }

    private static List<EditorFieldSpec> slotEntry(int index) {
        String prefix = "slots." + index + ".";
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.number("slot_index", "editor.field.slot_index", prefix + "index"));
        specs.add(EditorFieldSpec.cycle("slot_type", "editor.field.slot_type", GEM_TYPES,
                prefix + "type"));
        specs.add(EditorFieldSpec.text("slot_display_name", "editor.field.slot_display_name",
                prefix + "display_name"));
        return specs;
    }

    private static List<EditorFieldSpec> resonanceHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("res_display_name", "editor.field.display_name", "display_name"));
        specs.add(EditorFieldSpec.number("res_priority", "editor.field.priority", "priority"));
        specs.add(EditorFieldSpec.text("res_exclusive_group", "editor.field.exclusive_group",
                "exclusive_group"));
        specs.add(navigate("res_chain", "editor.field.chain", RES_CHAIN, "chain"));
        specs.add(navigate("res_effects", "editor.field.effects", RES_EFFECTS, "effects"));
        return specs;
    }

    private static List<EditorFieldSpec> resonanceChain() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("chain_mode", "editor.field.chain_mode", CHAIN_MODES,
                "chain", "mode"));
        specs.add(EditorFieldSpec.list("chain_pattern", "editor.field.chain_pattern",
                LIST_ENTRIES, "chain.pattern"));
        return specs;
    }

    private static EditorFieldSpec navigate(String id, String labelKey, String targetMenu, String... path) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey, path, List.of(),
                targetMenu, null, null);
    }
}
