package emaki.jiuwu.craft.accessory.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.EmakiAccessoryPlugin;
import emaki.jiuwu.craft.accessory.model.AccessoryPage;
import emaki.jiuwu.craft.accessory.model.AccessorySetDefinition;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.editor.EditorBrowserRoot;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorModule;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.EditorValidator;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader.LoadedYamlEntry;

public final class AccessoryEditorModule implements EditorModule {

    private static final String PARTS_FILE = "parts.yml";

    private final EmakiAccessoryPlugin plugin;
    private final SingleFileRoot partsRoot;
    private final DirectoryRoot setsRoot;
    private final DirectoryRoot pagesRoot;
    private final List<EditorBrowserRoot> roots;
    private EditorService service;

    public AccessoryEditorModule(EmakiAccessoryPlugin plugin) {
        this.plugin = plugin;
        this.partsRoot = new SingleFileRoot(AccessoryEditorMenus.ROOT_PARTS, "", PARTS_FILE,
                "editor.root.parts", Material.ARMOR_STAND);
        this.setsRoot = new DirectoryRoot(AccessoryEditorMenus.ROOT_SETS, "sets",
                "editor.root.sets", Material.CHEST, "id");
        this.pagesRoot = new DirectoryRoot(AccessoryEditorMenus.ROOT_PAGES, "pages",
                "editor.root.pages", Material.WRITABLE_BOOK, "page_id");
        this.roots = List.of(partsRoot, setsRoot, pagesRoot);
    }

    public void bind(EditorService service) {
        this.service = service;
    }

    private static final String TYPE_PREVIEW = "preview";

    @Override
    public ItemStack renderCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot) {
        if (!TYPE_PREVIEW.equals(Texts.lower(slot.definition().type()))) {
            return null;
        }
        String source = "minecraft-paper";
        Object icon = session.draft().value("icon");
        if (icon instanceof String iconSource && !iconSource.isBlank()) {
            source = iconSource;
        } else {
            Object parts = session.draft().value("parts");
            if (parts instanceof Map<?, ?> partMap) {
                for (Object value : partMap.values()) {
                    if (value instanceof Map<?, ?> part && part.get("icon") instanceof String partIcon
                            && !partIcon.isBlank()) {
                        source = partIcon;
                        break;
                    }
                }
            }
        }
        String name = Texts.toStringSafe(session.draft().value("display_name"));
        if (name.isBlank()) {
            name = session.document().docKey();
        }
        ConfiguredItemDefinition definition = new ConfiguredItemDefinition(source, 1, Map.of(
                "minecraft:custom_name", ItemComponentPatch.set("<white>" + name + "</white>"),
                "minecraft:lore", ItemComponentPatch.set(List.of(
                        "<dark_gray>" + session.document().path() + "</dark_gray>"))));
        return GuiItemBuilder.build(definition, Map.of(), plugin.coreLib().configuredItemService());
    }

    @Override
    public String rootCommand() {
        return "emakiaccessory";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        return roots;
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return AccessoryEditorMenus.meta(menuId);
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return AccessoryEditorMenus.fields(menuId, session);
    }

    @Override
    public String homeMenuId() {
        return AccessoryEditorMenus.HOME;
    }

    @Override
    public String titleKey(String menuId) {
        return AccessoryEditorMenus.titleKey(menuId);
    }

    @Override
    public EditorValidator validator(EditorBrowserRoot root, Path file) {
        if (root == null) {
            return EditorValidator.yamlOnly();
        }
        if (AccessoryEditorMenus.ROOT_PARTS.equals(root.id())) {
            return AccessoryEditorValidators.parts();
        }
        if (AccessoryEditorMenus.ROOT_SETS.equals(root.id())) {
            return AccessoryEditorValidators.sets(plugin.setLoader());
        }
        if (AccessoryEditorMenus.ROOT_PAGES.equals(root.id())) {
            return AccessoryEditorValidators.pages(plugin.pageLoader(), file.toFile());
        }
        return EditorValidator.yamlOnly();
    }

    @Override
    public void reload() {
        plugin.reloadContent();
    }

    @Override
    public Map<String, Object> skeleton(EditorBrowserRoot root, String id) {
        return root == null ? null : AccessoryEditorMenus.skeleton(root.id(), id);
    }

    public boolean openById(Player player, String rawId) {
        String id = Texts.normalizeId(rawId);
        if (service == null || id.isEmpty()) {
            return false;
        }
        Resolved resolved = resolve(id);
        if (resolved == null) {
            return false;
        }
        return service.open(player, resolved.file(), id,
                validator(resolved.root(), resolved.file()), null) != null;
    }

    private record Resolved(EditorBrowserRoot root, Path file) {
    }

    private Resolved resolve(String id) {
        if (partsRoot.id().equals(id)) {
            File partsFile = plugin.partLoader().file();
            return partsFile.isFile() ? new Resolved(partsRoot, partsFile.toPath()) : null;
        }
        LoadedYamlEntry<AccessorySetDefinition> set = plugin.setLoader().entries().get(id);
        if (set != null) {
            return new Resolved(setsRoot, set.file().toPath());
        }
        LoadedYamlEntry<AccessoryPage> page = plugin.pageLoader().entries().get(id);
        if (page != null) {
            return new Resolved(pagesRoot, page.file().toPath());
        }
        for (DirectoryRoot root : List.of(setsRoot, pagesRoot)) {
            Path candidate = plugin.getDataFolder().toPath()
                    .resolve(root.directory())
                    .resolve(id + ".yml");
            if (candidate.toFile().isFile()) {
                return new Resolved(root, candidate);
            }
        }
        return null;
    }

    private static final class SingleFileRoot implements EditorBrowserRoot {

        private final String id;
        private final String directory;
        private final String fileName;
        private final String labelKey;
        private final Material icon;

        private SingleFileRoot(String id, String directory, String fileName, String labelKey,
                Material icon) {
            this.id = id;
            this.directory = directory;
            this.fileName = fileName;
            this.labelKey = labelKey;
            this.icon = icon;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String directory() {
            return directory;
        }

        @Override
        public String fileName() {
            return fileName;
        }

        @Override
        public String labelKey() {
            return labelKey;
        }

        @Override
        public Material icon() {
            return icon;
        }

        @Override
        public BiFunction<File, String, String> fileId() {
            return (file, label) -> id;
        }
    }

    private static final class DirectoryRoot implements EditorBrowserRoot {

        private final String id;
        private final String directory;
        private final String labelKey;
        private final Material icon;
        private final String contentIdKey;

        private DirectoryRoot(String id, String directory, String labelKey, Material icon,
                String contentIdKey) {
            this.id = id;
            this.directory = directory;
            this.labelKey = labelKey;
            this.icon = icon;
            this.contentIdKey = contentIdKey;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String directory() {
            return directory;
        }

        @Override
        public String labelKey() {
            return labelKey;
        }

        @Override
        public Material icon() {
            return icon;
        }

        @Override
        public BiFunction<File, String, String> fileId() {
            return (file, label) -> {
                String contentId = contentId(file, contentIdKey);
                return contentId.isEmpty() ? fileStem(file) : contentId;
            };
        }

        private static String contentId(File file, String key) {
            try {
                YamlSection section = YamlFiles.load(file);
                if (section == null) {
                    return "";
                }
                return Texts.normalizeId(Texts.toStringSafe(section.getString(key, "")));
            } catch (RuntimeException failure) {
                return "";
            }
        }

        private static String fileStem(File file) {
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
    }
}
