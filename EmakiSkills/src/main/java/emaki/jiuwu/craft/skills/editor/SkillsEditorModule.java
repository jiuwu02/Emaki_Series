package emaki.jiuwu.craft.skills.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.loader.LocalResourceDefinitionLoader;
import emaki.jiuwu.craft.skills.loader.SkillDefinitionLoader;

public final class SkillsEditorModule implements EditorModule {

    public static final String ROOT_SKILLS = "skills";
    public static final String ROOT_RESOURCES = "resources";

    private static final String TYPE_PREVIEW = "preview";
    private static final String FALLBACK_ICON = "minecraft-paper";

    private final EmakiSkillsPlugin plugin;
    private EditorService service;

    public SkillsEditorModule(EmakiSkillsPlugin plugin) {
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
        return "emakiskills";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        return List.of(
                root(ROOT_SKILLS, "editor.root.skills"),
                root(ROOT_RESOURCES, "editor.root.resources"));
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return SkillsEditorMenus.meta(menuId);
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return SkillsEditorMenus.fields(session, menuId);
    }

    @Override
    public String homeMenuId() {
        return SkillsEditorMenus.HOME;
    }

    @Override
    public String titleKey(String menuId) {
        return SkillsEditorMenus.titleKey(menuId);
    }

    @Override
    public EditorValidator validator(EditorBrowserRoot root, Path file) {
        return validatorFor(plugin, root.id(), resolveFileId(file.toFile(), root.id()));
    }

    @Override
    public void reload() {
        plugin.reloadPluginStateAsync(false, null);
    }

    @Override
    public Map<String, Object> skeleton(EditorBrowserRoot root, String id) {
        return skeletonFor(root.id(), id);
    }

    @Override
    public ItemStack renderCustomSlot(EditorSession session, String menuId,
            GuiTemplate.ResolvedSlot slot) {
        if (!TYPE_PREVIEW.equals(Texts.lower(slot.definition().type()))) {
            return null;
        }
        return renderPreview(session, slot.definition());
    }

    public EditorSession openById(Player player, String rawId) {
        String id = Texts.normalizeId(rawId);
        if (service == null || id.isEmpty()) {
            return null;
        }
        for (EditorBrowserRoot root : roots()) {
            File file = findFile(root, id);
            if (file == null) {
                continue;
            }
            return service.open(player, file.toPath(), id, validator(root, file.toPath()), null);
        }
        return null;
    }

    public List<String> knownIds() {
        List<String> ids = new ArrayList<>();
        for (EditorBrowserRoot root : roots()) {
            for (File file : rootFiles(root)) {
                String id = resolveFileId(file, root.id());
                if (Texts.isNotBlank(id) && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    public static EditorValidator validatorFor(EmakiSkillsPlugin plugin, String rootId,
            String expectedId) {
        EditorValidator parseCheck = parseCheck(plugin, rootId);
        return (candidate, docId, source) -> EditorValidator.allOf(
                EditorValidator.yamlOnly(),
                idStable(expectedId),
                parseCheck).validate(candidate, docId, source);
    }

    public static Map<String, Object> skeletonFor(String rootId, String id) {
        return switch (rootId) {
            case ROOT_SKILLS -> skillSkeleton(id);
            case ROOT_RESOURCES -> resourceSkeleton(id);
            default -> null;
        };
    }

    private static EditorValidator idStable(String expectedId) {
        return (candidate, docId, source) -> {
            Object raw = candidate.value("id");
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

    private static EditorValidator parseCheck(EmakiSkillsPlugin plugin, String rootId) {
        return (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                if (ROOT_SKILLS.equals(rootId) && !section.getBoolean("enabled", true)) {
                    return null;
                }
                File file = source == null ? null : source.toFile();
                Object parsed = switch (rootId) {
                    case ROOT_SKILLS -> new SkillDefinitionLoader(plugin).parseDefinition(file, section);
                    case ROOT_RESOURCES ->
                        new LocalResourceDefinitionLoader(plugin).parseDefinition(file, section);
                    default -> null;
                };
                return parsed == null ? "definition_parse_failed" : null;
            } catch (RuntimeException failure) {
                return "definition_parse_failed";
            }
        };
    }

    private File findFile(EditorBrowserRoot root, String id) {
        for (File file : rootFiles(root)) {
            if (id.equals(resolveFileId(file, root.id()))) {
                return file;
            }
        }
        return null;
    }

    private List<File> rootFiles(EditorBrowserRoot root) {
        if (root.directory() == null || root.directory().isBlank()) {
            return List.of();
        }
        File[] children = new File(plugin.getDataFolder(), root.directory()).listFiles();
        if (children == null) {
            return List.of();
        }
        List<File> files = new ArrayList<>();
        for (File child : children) {
            if (child == null || !child.isFile()) {
                continue;
            }
            String name = child.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                files.add(child);
            }
        }
        files.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return files;
    }

    private static String resolveFileId(File file, String rootId) {
        try {
            YamlSection section = YamlFiles.load(file);
            Object raw = section == null ? null : section.asMap().get("id");
            String id = Texts.normalizeId(raw == null ? "" : Texts.toStringSafe(raw));
            if (Texts.isNotBlank(id)) {
                return id;
            }
        } catch (RuntimeException failure) {
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static EditorBrowserRoot root(String directory, String labelKey) {
        return new EditorBrowserRoot() {
            @Override
            public String id() {
                return directory;
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
            public boolean supportsCreate() {
                return true;
            }

            @Override
            public boolean supportsDelete() {
                return true;
            }

            @Override
            public BiFunction<File, String, String> fileId() {
                return (file, rootDirectory) -> resolveFileId(file, directory);
            }
        };
    }

    private static Map<String, Object> skillSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("id", id);
        skeleton.put("display_name", "<white>" + id + "</white>");
        skeleton.put("description", List.of());
        skeleton.put("icon_material", "paper");
        skeleton.put("trigger_type", "active");
        skeleton.put("cooldown_ticks", 0);
        skeleton.put("global_cooldown_ticks", 0);
        skeleton.put("lore_aliases", List.of());
        skeleton.put("tags", List.of());
        skeleton.put("tab_tags", List.of());
        skeleton.put("conflicting_skills", List.of());
        skeleton.put("required_skills", List.of());
        skeleton.put("show_in_slots", true);
        skeleton.put("ui_category", "default");
        skeleton.put("sort_order", 0);
        skeleton.put("enabled", true);
        skeleton.put("resource_costs", List.of());
        skeleton.put("variables", new LinkedHashMap<>());
        Map<String, Object> script = new LinkedHashMap<>();
        script.put("enabled", false);
        script.put("mode", "native");
        script.put("stop_on_failure", false);
        script.put("actions", new LinkedHashMap<>());
        skeleton.put("script", script);
        Map<String, Object> upgrade = new LinkedHashMap<>();
        upgrade.put("enabled", false);
        upgrade.put("max_level", 1);
        upgrade.put("gui_template", "upgrade_gui");
        upgrade.put("failure_penalty", "none");
        skeleton.put("upgrade", upgrade);
        return skeleton;
    }

    private static Map<String, Object> resourceSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("id", id);
        skeleton.put("display_name", "<white>" + id + "</white>");
        skeleton.put("max", 100.0D);
        skeleton.put("default_current", 100.0D);
        skeleton.put("regen_amount", 1.0D);
        skeleton.put("regen_interval_ticks", 20);
        skeleton.put("clamp_min", 0.0D);
        skeleton.put("clamp_max", 100.0D);
        return skeleton;
    }

    private ItemStack renderPreview(EditorSession session, GuiSlot definition) {
        YamlTextDocument draft = session.draft(EditorSession.SCOPE_DEFAULT);
        String icon = Texts.lower(Texts.toStringSafe(draft.value("icon_material")).trim());
        String displayName = Texts.toStringSafe(draft.value("display_name")).trim();
        String fileName = fileName(session);
        Material material = icon.isEmpty() ? null : Material.matchMaterial(icon.toUpperCase(Locale.ROOT));
        boolean iconResolved = material != null && material.isItem();
        String source = iconResolved
                ? "minecraft-" + material.name().toLowerCase(Locale.ROOT)
                : FALLBACK_ICON;
        String name = displayName.isEmpty() ? fileName : displayName;
        List<String> lore = iconResolved
                ? List.of(fileName)
                : displayName.isEmpty() ? List.of(fileName) : List.of(fileName, displayName);
        try {
            return GuiItemBuilder.build(definition, source, name, lore, Map.of(),
                    plugin.coreLib().configuredItemService());
        } catch (RuntimeException failure) {
            return GuiItemBuilder.build(definition, FALLBACK_ICON, name, lore, Map.of(),
                    plugin.coreLib().configuredItemService());
        }
    }

    private static String fileName(EditorSession session) {
        Path path = session.document().path();
        return path.getFileName() == null ? "" : path.getFileName().toString();
    }
}
