package emaki.jiuwu.craft.codex.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.advancement.loader.AdvancementPageLoader;
import emaki.jiuwu.craft.codex.codex.loader.CodexCategoryLoader;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.editor.EditorBrowserRoot;
import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorModule;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.EditorValidator;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader.LoadedYamlEntry;

public final class CodexEditorModule implements EditorModule {

    public static final String ROOT_CODEX = "codex";
    public static final String ROOT_ADVANCEMENTS = "advancements";

    public static final String MENU_HOME = "home";
    public static final String MENU_CATEGORY_ENTRIES = "category_entries";
    public static final String MENU_PAGE_ADVANCEMENTS = "page_advancements";
    public static final String MENU_LIST_ENTRIES = EditorService.MENU_LIST_ENTRIES;

    private static final String TYPE_PREVIEW = "preview";
    private static final String FALLBACK_ICON = "minecraft-paper";
    private static final String DEFAULT_BACKGROUND =
            "minecraft:textures/gui/advancements/backgrounds/stone.png";

    private final EmakiCodexPlugin plugin;
    private EditorService service;

    public CodexEditorModule(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    public void bind(EditorService service) {
        this.service = service;
    }

    public EditorService service() {
        return service;
    }

    @Override
    public String rootCommand() {
        return "emakicodex";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        return List.of(
                root(ROOT_CODEX, "editor.root.codex"),
                root(ROOT_ADVANCEMENTS, "editor.root.advancements"));
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return switch (menuId == null ? "" : menuId) {
            case MENU_HOME -> new EditorMenuMeta(MENU_HOME, "editor.menu.home", MENU_HOME,
                    new String[0], true);
            case MENU_CATEGORY_ENTRIES -> new EditorMenuMeta(MENU_CATEGORY_ENTRIES,
                    "editor.menu." + MENU_CATEGORY_ENTRIES, MENU_HOME,
                    new String[]{ "entries" }, true);
            case MENU_PAGE_ADVANCEMENTS -> new EditorMenuMeta(MENU_PAGE_ADVANCEMENTS,
                    "editor.menu." + MENU_PAGE_ADVANCEMENTS, MENU_HOME,
                    new String[]{ "advancements" }, true);
            case MENU_LIST_ENTRIES -> new EditorMenuMeta(MENU_LIST_ENTRIES,
                    "editor.menu." + MENU_LIST_ENTRIES, MENU_HOME, null, true);
            default -> null;
        };
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        if (MENU_HOME.equals(menuId)) {
            return homeFields(session);
        }
        return List.of();
    }

    @Override
    public String homeMenuId() {
        return MENU_HOME;
    }

    @Override
    public String titleKey(String menuId) {
        return menuId == null || menuId.isEmpty() ? "editor.menu.home" : "editor.menu." + menuId;
    }

    @Override
    public EditorValidator validator(EditorBrowserRoot root, Path file) {
        return validatorFor(plugin, root.id(), resolveFileId(file.toFile(), root.id()));
    }

    @Override
    public void reload() {
        plugin.reloadPluginState();
    }

    @Override
    public Material fieldIcon(EditorField field) {
        return switch (field.id()) {
            case "category_id", "page_id" -> Material.BOOK;
            case "category_title", "page_title" -> Material.NAME_TAG;
            case "category_icon" -> Material.PAINTING;
            case "category_order" -> Material.COMPARATOR;
            case "page_background" -> Material.FILLED_MAP;
            case "page_root" -> Material.OAK_SAPLING;
            case "nav_entries" -> Material.WRITABLE_BOOK;
            case "nav_advancements" -> Material.ENCHANTED_BOOK;
            default -> null;
        };
    }

    @Override
    public ItemStack renderCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot) {
        if (!TYPE_PREVIEW.equals(Texts.lower(slot.definition().type()))) {
            return null;
        }
        return renderPreview(session, slot);
    }

    @Override
    public Map<String, Object> skeleton(EditorBrowserRoot root, String id) {
        return skeletonFor(root.id(), id);
    }

    public static EditorValidator validatorFor(EmakiCodexPlugin plugin, String rootId, String expectedId) {
        EditorValidator parseCheck = parseCheck(plugin, rootId);
        return (candidate, docId, source) -> EditorValidator.allOf(
                EditorValidator.yamlOnly(),
                idStable(rootId, expectedId),
                parseCheck).validate(candidate, docId, source);
    }

    public static Map<String, Object> skeletonFor(String rootId, String id) {
        return switch (rootId) {
            case ROOT_CODEX -> categorySkeleton(id);
            case ROOT_ADVANCEMENTS -> pageSkeleton(id);
            default -> null;
        };
    }

    public EditorSession openById(Player player, String rawId) {
        String id = Texts.normalizeId(rawId);
        if (service == null || id.isEmpty()) {
            return null;
        }
        for (EditorBrowserRoot root : roots()) {
            File file = findFile(root.id(), id);
            if (file == null) {
                continue;
            }
            return service.open(player, file.toPath(), id, validator(root, file.toPath()), null);
        }
        return null;
    }

    public List<String> knownIds() {
        List<String> ids = new ArrayList<>();
        collectIds(plugin.codexCategoryLoader().entries(), ids);
        collectIds(plugin.advancementPageLoader().entries(), ids);
        ids.sort(String::compareTo);
        return ids;
    }

    private static List<EditorFieldSpec> homeFields(EditorSession session) {
        return isAdvancementPage(session) ? pageHome() : categoryHome();
    }

    private static List<EditorFieldSpec> categoryHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("category_id", "editor.field.category_id", "category_id"));
        specs.add(EditorFieldSpec.text("category_title", "editor.field.title", "title"));
        specs.add(EditorFieldSpec.text("category_icon", "editor.field.icon", "icon"));
        specs.add(EditorFieldSpec.number("category_order", "editor.field.order", "order"));
        specs.add(navigate("nav_entries", "editor.field.nav_entries", MENU_CATEGORY_ENTRIES, "entries"));
        return specs;
    }

    private static List<EditorFieldSpec> pageHome() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("page_id", "editor.field.page_id", "page_id"));
        specs.add(EditorFieldSpec.text("page_title", "editor.field.title", "title"));
        specs.add(EditorFieldSpec.text("page_background", "editor.field.background", "background"));
        specs.add(EditorFieldSpec.text("page_root", "editor.field.root", "root"));
        specs.add(navigate("nav_advancements", "editor.field.nav_advancements",
                MENU_PAGE_ADVANCEMENTS, "advancements"));
        return specs;
    }

    private static EditorFieldSpec navigate(String id, String labelKey, String targetMenu, String path) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey, new String[]{ path },
                List.of(), targetMenu, null, null);
    }

    private static boolean isAdvancementPage(EditorSession session) {
        Path parent = session.document().path().getParent();
        return parent != null && parent.getFileName() != null
                && ROOT_ADVANCEMENTS.equals(parent.getFileName().toString());
    }

    private static EditorValidator idStable(String rootId, String expectedId) {
        String key = idKey(rootId);
        return (candidate, docId, source) -> {
            Object raw = candidate.value(key);
            String actual = Texts.normalizeId(raw == null ? "" : Texts.toStringSafe(raw));
            if (actual.isEmpty()) {
                return "definition_parse_failed";
            }
            if (expectedId == null || expectedId.isEmpty()) {
                return null;
            }
            return expectedId.equals(actual) ? null : "id_changed";
        };
    }

    private static EditorValidator parseCheck(EmakiCodexPlugin plugin, String rootId) {
        return (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                File file = source == null ? null : source.toFile();
                Object parsed = switch (rootId) {
                    case ROOT_CODEX -> new CodexCategoryLoader(plugin).parseDefinition(file, section);
                    case ROOT_ADVANCEMENTS -> new AdvancementPageLoader(plugin).parseDefinition(file, section);
                    default -> null;
                };
                return parsed == null ? "definition_parse_failed" : null;
            } catch (RuntimeException failure) {
                return "definition_parse_failed";
            }
        };
    }

    private static String idKey(String rootId) {
        return ROOT_ADVANCEMENTS.equals(rootId) ? "page_id" : "category_id";
    }

    private static Map<String, Object> categorySkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("category_id", id);
        skeleton.put("title", "<white>" + id + "</white>");
        skeleton.put("icon", "minecraft-book");
        skeleton.put("order", 100);
        skeleton.put("entries", new LinkedHashMap<>());
        return skeleton;
    }

    private static Map<String, Object> pageSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("page_id", id);
        skeleton.put("title", "<white>" + id + "</white>");
        skeleton.put("background", DEFAULT_BACKGROUND);
        skeleton.put("root", "root");
        Map<String, Object> rootNode = new LinkedHashMap<>();
        rootNode.put("icon", "minecraft-book");
        rootNode.put("title", "<white>root</white>");
        rootNode.put("description", "");
        rootNode.put("frame", "task");
        rootNode.put("x", 0.0D);
        rootNode.put("y", 0.0D);
        rootNode.put("toast", false);
        rootNode.put("announce", false);
        rootNode.put("hidden", false);
        Map<String, Object> advancements = new LinkedHashMap<>();
        advancements.put("root", rootNode);
        skeleton.put("advancements", advancements);
        return skeleton;
    }

    private ItemStack renderPreview(EditorSession session, GuiTemplate.ResolvedSlot slot) {
        YamlTextDocument draft = session.draft();
        String fileName = fileName(session);
        String source = isAdvancementPage(session) ? pageIcon(draft)
                : Texts.toStringSafe(draft.value("icon"));
        if (Texts.isBlank(source)) {
            source = FALLBACK_ICON;
        }
        String name = Texts.toStringSafe(draft.value("title"));
        if (Texts.isBlank(name)) {
            name = "<white>" + fileName + "</white>";
        }
        List<String> lore = List.of("<dark_gray>" + fileName + "</dark_gray>");
        try {
            return buildPreview(slot, source, name, lore);
        } catch (RuntimeException failure) {
            try {
                return buildPreview(slot, FALLBACK_ICON, name, lore);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private ItemStack buildPreview(GuiTemplate.ResolvedSlot slot, String source, String name,
            List<String> lore) {
        return GuiItemBuilder.build(slot.definition(), source, name, lore, Map.of(),
                plugin.coreLib().configuredItemService());
    }

    private static String pageIcon(YamlTextDocument draft) {
        String rootId = Texts.toStringSafe(draft.value("root")).trim();
        if (rootId.isEmpty()) {
            return "";
        }
        return Texts.toStringSafe(draft.value("advancements", rootId, "icon"));
    }

    private static String fileName(EditorSession session) {
        Path path = session.document().path();
        return path.getFileName() == null ? "" : path.getFileName().toString();
    }

    private File findFile(String rootId, String id) {
        return switch (rootId) {
            case ROOT_CODEX -> findEntry(plugin.codexCategoryLoader().entries(), id);
            case ROOT_ADVANCEMENTS -> findEntry(plugin.advancementPageLoader().entries(), id);
            default -> null;
        };
    }

    private static File findEntry(Map<String, ? extends LoadedYamlEntry<?>> entries, String id) {
        for (Map.Entry<String, ? extends LoadedYamlEntry<?>> entry : entries.entrySet()) {
            if (id.equals(Texts.normalizeId(entry.getKey()))) {
                return entry.getValue().file();
            }
        }
        return null;
    }

    private static void collectIds(Map<String, ? extends LoadedYamlEntry<?>> entries, List<String> sink) {
        for (String key : entries.keySet()) {
            String id = Texts.normalizeId(key);
            if (!id.isEmpty() && !sink.contains(id)) {
                sink.add(id);
            }
        }
    }

    private static EditorBrowserRoot root(String rootId, String labelKey) {
        return new EditorBrowserRoot() {
            @Override
            public String id() {
                return rootId;
            }

            @Override
            public String directory() {
                return rootId;
            }

            @Override
            public String labelKey() {
                return labelKey;
            }

            @Override
            public boolean supportsCreate() {
                return true;
            }

            @Override
            public boolean supportsDelete() {
                return true;
            }

            @Override
            public BiFunction<File, String, String> fileId() {
                return (file, directory) -> resolveFileId(file, rootId);
            }
        };
    }

    private static String resolveFileId(File file, String rootId) {
        String key = idKey(rootId);
        try {
            YamlSection section = YamlFiles.load(file);
            Object raw = section == null ? null : section.asMap().get(key);
            String id = Texts.normalizeId(raw == null ? "" : Texts.toStringSafe(raw));
            if (!id.isEmpty()) {
                return id;
            }
        } catch (RuntimeException failure) {
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
