package emaki.jiuwu.craft.accessory.editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorRenderer;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;

public final class AccessoryEditorMenus {

    public static final String HOME = "home";
    public static final String PARTS = "parts";
    public static final String LIST_ENTRIES = "list_entries";

    public static final String ROOT_PARTS = "parts";
    public static final String ROOT_SETS = "sets";
    public static final String ROOT_PAGES = "pages";

    private static final String[] EMPTY_PATH = new String[0];
    private static final String[] PARTS_PATH = { "parts" };
    private static final String[] PIECES_PATH = { "pieces" };
    private static final String[] THRESHOLDS_PATH = { "thresholds" };

    private static final Map<String, EditorMenuMeta> MENUS = buildMenus();

    private AccessoryEditorMenus() {
    }

    public static EditorMenuMeta meta(String menuId) {
        return MENUS.get(menuId);
    }

    public static String titleKey(String menuId) {
        return "editor.menu." + menuId;
    }

    public static List<EditorFieldSpec> fields(String menuId, EditorSession session) {
        if (!HOME.equals(menuId)) {
            return List.of();
        }
        YamlTextDocument draft = session.draft();
        List<EditorFieldSpec> curated = homeFields(docType(session), draft);
        List<EditorFieldSpec> combined = new ArrayList<>(curated);
        combined.addAll(EditorRenderer.structuralSpecs(draft, EMPTY_PATH, curated));
        return combined;
    }

    public static Map<String, Object> skeleton(String rootId, String id) {
        if (ROOT_SETS.equals(rootId)) {
            Map<String, Object> piece = new LinkedHashMap<>();
            piece.put("item", "example_item");
            piece.put("slot", "");
            piece.put("display", "");
            Map<String, Object> pieces = new LinkedHashMap<>();
            pieces.put("main", piece);
            Map<String, Object> skeleton = new LinkedHashMap<>();
            skeleton.put("id", id);
            skeleton.put("display_name", "");
            skeleton.put("pieces", pieces);
            return skeleton;
        }
        if (ROOT_PAGES.equals(rootId)) {
            Map<String, Object> skeleton = new LinkedHashMap<>();
            skeleton.put("page_id", id);
            skeleton.put("display_name", "");
            skeleton.put("order", 10);
            skeleton.put("gui_template", "accessory_gui");
            skeleton.put("permission", "");
            skeleton.put("parts", List.of("necklace", "ring", "belt", "charm"));
            return skeleton;
        }
        return null;
    }

    private static Map<String, EditorMenuMeta> buildMenus() {
        Map<String, EditorMenuMeta> menus = new LinkedHashMap<>();
        menus.put(HOME, new EditorMenuMeta(HOME, "editor.menu.home", HOME, null, true));
        menus.put(PARTS, new EditorMenuMeta(PARTS, "editor.menu.parts", HOME, PARTS_PATH, true));
        menus.put(LIST_ENTRIES,
                new EditorMenuMeta(LIST_ENTRIES, "editor.menu.list_entries", HOME, null, true));
        return menus;
    }

    private static String docType(EditorSession session) {
        Path parent = session.document().path().getParent();
        String name = parent == null || parent.getFileName() == null
                ? ""
                : parent.getFileName().toString();
        if (ROOT_SETS.equals(name)) {
            return ROOT_SETS;
        }
        if (ROOT_PAGES.equals(name)) {
            return ROOT_PAGES;
        }
        return ROOT_PARTS;
    }

    private static List<EditorFieldSpec> homeFields(String type, YamlTextDocument draft) {
        if (ROOT_SETS.equals(type)) {
            return setHome();
        }
        if (ROOT_PAGES.equals(type)) {
            return pageHome();
        }
        return partsHome();
    }

    private static List<EditorFieldSpec> partsHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(navigate("nav_parts", "editor.field.nav_parts", PARTS_PATH, PARTS));
        return specs;
    }

    private static List<EditorFieldSpec> setHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("set_display_name",
                "editor.field.set_display_name", "display_name"));
        specs.add(navigate("set_pieces", "editor.field.set_pieces", PIECES_PATH, "#pieces"));
        specs.add(navigate("set_thresholds", "editor.field.set_thresholds",
                THRESHOLDS_PATH, "#thresholds"));
        return specs;
    }

    private static List<EditorFieldSpec> pageHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("page_id", "editor.field.page_id", "page_id"));
        specs.add(EditorFieldSpec.text("page_display_name",
                "editor.field.page_display_name", "display_name"));
        specs.add(EditorFieldSpec.number("page_order", "editor.field.page_order", "order"));
        specs.add(EditorFieldSpec.text("page_gui_template",
                "editor.field.page_gui_template", "gui_template"));
        specs.add(EditorFieldSpec.text("page_permission",
                "editor.field.page_permission", "permission"));
        specs.add(EditorFieldSpec.list("page_parts", "editor.field.page_parts",
                EditorService.MENU_LIST_ENTRIES, "parts"));
        return specs;
    }

    private static EditorFieldSpec navigate(String id, String labelKey, String[] path,
            String target) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey, path,
                List.of(), target, null, null);
    }
}
