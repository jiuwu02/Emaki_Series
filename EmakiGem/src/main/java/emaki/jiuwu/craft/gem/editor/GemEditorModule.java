package emaki.jiuwu.craft.gem.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.loader.GemItemLoader;
import emaki.jiuwu.craft.gem.loader.GemLoader;
import emaki.jiuwu.craft.gem.loader.GemResonanceLoader;

public final class GemEditorModule implements EditorModule {

    public static final String ROOT_GEMS = "gems";
    public static final String ROOT_ITEMS = "items";
    public static final String ROOT_RESONANCES = "resonances";

    private final EmakiGemPlugin plugin;
    private EditorService service;

    public GemEditorModule(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    public void bind(EditorService service) {
        this.service = service;
    }

    public EditorService service() {
        return service;
    }

    private static final String TYPE_PREVIEW = "preview";

    @Override
    public ItemStack renderCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot) {
        if (!TYPE_PREVIEW.equals(Texts.lower(slot.definition().type()))) {
            return null;
        }
        String source = "minecraft-paper";
        Object base = session.draft().value("base_item_source");
        if (base instanceof String baseSource && !baseSource.isBlank()) {
            source = baseSource;
        } else {
            Object sources = session.draft().value("item_sources");
            if (sources instanceof List<?> sourceList && !sourceList.isEmpty()
                    && sourceList.get(0) instanceof String firstSource && !firstSource.isBlank()) {
                source = firstSource;
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
        return "emakigem";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        return List.of(
                root(ROOT_GEMS, "editor.root.gems"),
                root(ROOT_ITEMS, "editor.root.items"),
                root(ROOT_RESONANCES, "editor.root.resonances"));
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return GemEditorMenus.meta(menuId);
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return GemEditorMenus.fields(session, menuId);
    }

    @Override
    public String homeMenuId() {
        return GemEditorMenus.HOME;
    }

    @Override
    public String titleKey(String menuId) {
        return GemEditorMenus.titleKey(menuId);
    }

    @Override
    public EditorValidator validator(EditorBrowserRoot root, Path file) {
        return validatorFor(plugin, root.id());
    }

    @Override
    public void reload() {
        plugin.reloadPluginStateAsync(true);
    }

    @Override
    public Map<String, Object> skeleton(EditorBrowserRoot root, String id) {
        return switch (root.id()) {
            case ROOT_GEMS -> gemSkeleton(id);
            case ROOT_ITEMS -> itemSkeleton(id);
            case ROOT_RESONANCES -> resonanceSkeleton(id);
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
        collectIds(plugin.gemLoader().entries(), ids);
        collectIds(plugin.gemItemLoader().entries(), ids);
        if (plugin.resonanceLoader() != null) {
            collectIds(plugin.resonanceLoader().entries(), ids);
        }
        ids.sort(String::compareTo);
        return ids;
    }

    public static EditorValidator validatorFor(EmakiGemPlugin plugin, String rootId) {
        EditorValidator parseCheck = parseCheck(plugin, rootId);
        return (candidate, docId, source) -> EditorValidator.allOf(
                EditorValidator.yamlOnly(),
                EditorValidator.idStable(docId),
                parseCheck).validate(candidate, docId, source);
    }

    private static EditorValidator parseCheck(EmakiGemPlugin plugin, String rootId) {
        return (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                File file = source == null ? null : source.toFile();
                Object parsed = switch (rootId) {
                    case ROOT_GEMS -> new GemLoader(plugin).parseDefinition(file, section);
                    case ROOT_ITEMS -> new GemItemLoader(plugin).parseDefinition(file, section);
                    case ROOT_RESONANCES -> new GemResonanceLoader(plugin).parseDefinition(file, section);
                    default -> null;
                };
                return parsed == null ? "definition_parse_failed" : null;
            } catch (RuntimeException failure) {
                return "definition_parse_failed";
            }
        };
    }

    private File findFile(String rootId, String id) {
        return switch (rootId) {
            case ROOT_GEMS -> findEntry(plugin.gemLoader().entries(), id);
            case ROOT_ITEMS -> findEntry(plugin.gemItemLoader().entries(), id);
            case ROOT_RESONANCES -> plugin.resonanceLoader() == null
                    ? null
                    : findEntry(plugin.resonanceLoader().entries(), id);
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
                return GemEditorModule::resolveFileId;
            }
        };
    }

    private static String resolveFileId(File file, String directory) {
        try {
            Object raw = YamlFiles.load(file).asMap().get("id");
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

    private static Map<String, Object> gemSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("id", id);
        skeleton.put("display_name", "<white>" + id + "</white>");
        skeleton.put("gem_type", "universal");
        skeleton.put("level", 1);
        skeleton.put("base_item_source", "minecraft-stone");
        skeleton.put("item_sources", List.of("minecraft-stone"));
        skeleton.put("socket_compatibility", List.of("attack", "defense", "utility", "universal"));
        skeleton.put("effects", List.of());
        Map<String, Object> stages = new LinkedHashMap<>();
        stages.put("enabled", false);
        stages.put("max_level", 1);
        stages.put("levels", new LinkedHashMap<>());
        skeleton.put("stages", stages);
        skeleton.put("actions", new LinkedHashMap<>());
        return skeleton;
    }

    private static Map<String, Object> itemSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("id", id);
        skeleton.put("item_sources", List.of("minecraft-stone"));
        skeleton.put("slot_groups", List.of());
        Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("index", 0);
        slot.put("type", "universal");
        slot.put("display_name", "<white>socket</white>");
        skeleton.put("slots", List.of(slot));
        skeleton.put("default_open_slots", List.of(0));
        skeleton.put("allowed_gem_types", List.of());
        skeleton.put("max_same_type", 0);
        skeleton.put("max_same_id", 1);
        return skeleton;
    }

    private static Map<String, Object> resonanceSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("id", id);
        skeleton.put("display_name", "<white>" + id + "</white>");
        skeleton.put("priority", 0);
        skeleton.put("exclusive_group", "");
        Map<String, Object> patternEntry = new LinkedHashMap<>();
        patternEntry.put("type", "universal");
        patternEntry.put("min_level", 1);
        Map<String, Object> chain = new LinkedHashMap<>();
        chain.put("mode", "unordered");
        chain.put("pattern", List.of(patternEntry));
        skeleton.put("chain", chain);
        skeleton.put("effects", new LinkedHashMap<>());
        return skeleton;
    }
}
