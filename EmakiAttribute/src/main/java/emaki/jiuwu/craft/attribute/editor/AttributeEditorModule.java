package emaki.jiuwu.craft.attribute.editor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiFunction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.loader.EditorValidationSupport;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
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

public final class AttributeEditorModule implements EditorModule {

    public static final String ROOT_ATTRIBUTES = "attributes";
    public static final String ROOT_CONDITIONS = "conditions";
    public static final String ROOT_DAMAGE_TYPES = "damage_types";
    public static final String ROOT_LORE_FORMATS = "lore_formats";
    public static final String ROOT_PRESETS = "presets";
    public static final String ROOT_ATTRIBUTE_BALANCE = "attribute_balance";

    public static final String TYPE_PREVIEW = "preview";

    private static final List<EditorBrowserRoot> ROOTS = List.of(
            directoryRoot(ROOT_ATTRIBUTES, "id", Material.DIAMOND),
            directoryRoot(ROOT_CONDITIONS, "source_id", Material.COMPARATOR),
            directoryRoot(ROOT_DAMAGE_TYPES, "id", Material.REDSTONE),
            directoryRoot(ROOT_LORE_FORMATS, "id", Material.BOOK),
            directoryRoot(ROOT_PRESETS, "id", Material.ENCHANTED_BOOK),
            balanceRoot());

    private final EmakiAttributePlugin plugin;
    private EditorService service;

    public AttributeEditorModule(EmakiAttributePlugin plugin) {
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
        return "emakiattribute";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        return ROOTS;
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return AttributeEditorMenus.meta(menuId);
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return AttributeEditorMenus.fields(session, menuId);
    }

    @Override
    public String homeMenuId() {
        return AttributeEditorMenus.HOME;
    }

    @Override
    public String titleKey(String menuId) {
        return AttributeEditorMenus.titleKey(menuId);
    }

    @Override
    public EditorValidator validator(EditorBrowserRoot root, Path file) {
        return validatorFor(plugin, root == null ? ROOT_ATTRIBUTES : root.id());
    }

    @Override
    public void reload() {
        if (plugin != null) {
            plugin.reloadPluginStateAsync(true, null);
        }
    }

    @Override
    public Map<String, Object> skeleton(EditorBrowserRoot root, String id) {
        if (root == null) {
            return null;
        }
        return switch (root.id()) {
            case ROOT_CONDITIONS -> conditionSkeleton(id);
            case ROOT_DAMAGE_TYPES -> damageTypeSkeleton(id);
            case ROOT_LORE_FORMATS -> loreFormatSkeleton(id);
            case ROOT_PRESETS -> presetSkeleton(id);
            case ROOT_ATTRIBUTE_BALANCE -> null;
            default -> attributeSkeleton(id);
        };
    }

    @Override
    public Material fieldIcon(EditorField field) {
        String specific = specificIcon(field.id());
        return specific == null ? null : Material.matchMaterial(specific);
    }

    @Override
    public ItemStack renderCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot) {
        if (slot == null || slot.definition() == null
                || !TYPE_PREVIEW.equals(Texts.lower(slot.definition().type()))) {
            return null;
        }
        return renderPreview(session);
    }

    public EditorSession openById(Player player, String rawId) {
        String id = Texts.normalizeId(rawId == null ? "" : rawId.trim());
        if (id.isEmpty() || service == null) {
            return null;
        }
        for (EditorBrowserRoot root : roots()) {
            Path file = resolveFile(root, id);
            if (file != null) {
                return service.open(player, file, id, validator(root, file), null);
            }
        }
        return null;
    }

    public List<String> completeIds() {
        TreeSet<String> ids = new TreeSet<>();
        for (EditorBrowserRoot root : roots()) {
            for (File file : listDefinitionFiles(root)) {
                String id = root.fileId().apply(file, root.directory());
                if (id != null && !id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return new ArrayList<>(ids);
    }

    public static String docType(Path path) {
        if (path == null) {
            return ROOT_ATTRIBUTE_BALANCE;
        }
        Path parent = path.getParent();
        if (parent != null && parent.getFileName() != null) {
            String directory = parent.getFileName().toString();
            if (ROOT_ATTRIBUTES.equals(directory)
                    || ROOT_CONDITIONS.equals(directory)
                    || ROOT_DAMAGE_TYPES.equals(directory)
                    || ROOT_LORE_FORMATS.equals(directory)
                    || ROOT_PRESETS.equals(directory)) {
                return directory;
            }
        }
        return ROOT_ATTRIBUTE_BALANCE;
    }

    public static EditorValidator validatorFor(EmakiAttributePlugin plugin, String rootId) {
        String root = rootId == null || rootId.isEmpty() ? ROOT_ATTRIBUTES : rootId;
        return EditorValidator.allOf(
                EditorValidator.yamlOnly(),
                (candidate, docId, source) -> idProblem(candidate, root, source),
                (candidate, docId, source) -> contentProblem(plugin, root, source, candidate));
    }

    private static String idProblem(YamlTextDocument candidate, String root, Path source) {
        if (ROOT_ATTRIBUTE_BALANCE.equals(root)) {
            return null;
        }
        String expected = expectedId(root, source);
        String actual = contentId(candidate, root);
        if (actual.isEmpty()) {
            actual = fileStem(source == null ? "" : String.valueOf(source.getFileName()));
        }
        return expected.equals(actual) ? null : "id_changed";
    }

    private static String expectedId(String root, Path source) {
        if (source != null && Files.isRegularFile(source)) {
            try {
                YamlSection section = YamlFiles.load(source.toFile());
                String key = idKey(root);
                Object raw = section == null ? null : section.get(key);
                String id = Texts.normalizeId(raw == null ? "" : Texts.toStringSafe(raw));
                if (!id.isEmpty()) {
                    return id;
                }
            } catch (RuntimeException failure) {
            }
            return fileStem(String.valueOf(source.getFileName()));
        }
        return source == null ? "" : fileStem(String.valueOf(source.getFileName()));
    }

    private static String contentId(YamlTextDocument candidate, String root) {
        Object raw = candidate.value(idKey(root));
        return Texts.normalizeId(raw == null ? "" : Texts.toStringSafe(raw));
    }

    private static String idKey(String root) {
        return ROOT_CONDITIONS.equals(root) ? "source_id" : "id";
    }

    private static String contentProblem(EmakiAttributePlugin plugin, String root, Path source,
            YamlTextDocument candidate) {
        try {
            YamlSection section = YamlFiles.load(candidate.text());
            if (section == null) {
                return "unparseable_yaml";
            }
            File file = source == null ? Path.of("definition.yml").toFile() : source.toFile();
            boolean ok = switch (root) {
                case ROOT_CONDITIONS -> EditorValidationSupport.conditionOk(plugin, file, section);
                case ROOT_DAMAGE_TYPES -> EditorValidationSupport.damageTypeOk(plugin, file, section);
                case ROOT_LORE_FORMATS -> EditorValidationSupport.loreFormatOk(plugin, file, section);
                case ROOT_PRESETS -> EditorValidationSupport.presetOk(plugin, file, section);
                case ROOT_ATTRIBUTE_BALANCE -> EditorValidationSupport.balanceOk(plugin, file, section);
                default -> EditorValidationSupport.attributeOk(plugin, file, section);
            };
            if (!ok) {
                return "definition_parse_failed";
            }
            if (ROOT_ATTRIBUTE_BALANCE.equals(root) && hasUnknownBalanceAttribute(plugin, section)) {
                return "editor.validator.balance_unknown_attribute";
            }
            return null;
        } catch (RuntimeException failure) {
            return "definition_parse_failed";
        }
    }

    private static boolean hasUnknownBalanceAttribute(EmakiAttributePlugin plugin, YamlSection section) {
        if (plugin == null || plugin.attributeRegistry() == null
                || !plugin.attributeRegistry().loaded()) {
            return false;
        }
        for (String key : List.of("attributes", "scores")) {
            for (String rawId : ConfigNodes.entries(section.get(key)).keySet()) {
                String id = Texts.normalizeId(rawId);
                if (!id.isEmpty() && plugin.attributeRegistry().resolve(id) == null) {
                    return true;
                }
            }
        }
        return false;
    }

    private ItemStack renderPreview(EditorSession session) {
        YamlTextDocument draft = session.draft();
        String fileName = String.valueOf(session.document().path().getFileName());
        String id = Texts.normalizeId(Texts.toStringSafe(draft.valueLenient("id")));
        if (id.isEmpty()) {
            id = session.document().docId();
        }
        if (id.isEmpty()) {
            id = fileName;
        }
        String displayName = Texts.toStringSafe(draft.valueLenient("display_name")).trim();
        List<String> lore = new ArrayList<>();
        if (!displayName.isBlank()) {
            lore.add("<dark_gray>" + displayName + "</dark_gray>");
        }
        lore.add("<gray>" + fileName + "</gray>");
        String source = previewSource(draft);
        ConfiguredItemDefinition definition = new ConfiguredItemDefinition(source, 1, Map.of(
                "minecraft:custom_name", ItemComponentPatch.set("<white>" + id + "</white>"),
                "minecraft:lore", ItemComponentPatch.set(lore)));
        return GuiItemBuilder.build(definition, Map.of(),
                plugin == null ? null : EmakiCoreLibPlugin.lookup().configuredItemService());
    }

    private static String previewSource(YamlTextDocument draft) {
        String token = Texts.toStringSafe(draft.valueLenient("icon")).trim();
        if (!token.isEmpty()) {
            String name = token.startsWith("minecraft-") ? token.substring("minecraft-".length()) : token;
            Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
            if (material != null) {
                return "minecraft-" + material.name().toLowerCase(Locale.ROOT);
            }
        }
        return "minecraft-paper";
    }

    private Path resolveFile(EditorBrowserRoot root, String id) {
        for (File file : listDefinitionFiles(root)) {
            String fileId = root.fileId().apply(file, root.directory());
            if (id.equals(fileId)) {
                return file.toPath();
            }
        }
        return null;
    }

    private List<File> listDefinitionFiles(EditorBrowserRoot root) {
        Path directory = resolveDirectory(root);
        if (root.fileName() != null) {
            Path file = directory.resolve(root.fileName());
            if (Files.isRegularFile(file)) {
                return List.of(file.toFile());
            }
            return List.of();
        }
        File[] files = directory.toFile().listFiles(
                (dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml")
                        || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
        if (files == null) {
            return List.of();
        }
        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return sorted;
    }

    private Path resolveDirectory(EditorBrowserRoot root) {
        String directory = root.directory();
        if (directory == null || directory.isBlank()) {
            return plugin.getDataFolder().toPath();
        }
        return plugin.getDataFolder().toPath().resolve(directory);
    }

    private static EditorBrowserRoot directoryRoot(String directory, String idKey, Material icon) {
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
                return "editor.root." + directory;
            }

            @Override
            public Material icon() {
                return icon;
            }

            @Override
            public BiFunction<File, String, String> fileId() {
                return (file, dir) -> contentFileId(file, idKey);
            }
        };
    }

    private static EditorBrowserRoot balanceRoot() {
        return new EditorBrowserRoot() {
            @Override
            public String id() {
                return ROOT_ATTRIBUTE_BALANCE;
            }

            @Override
            public String directory() {
                return "";
            }

            @Override
            public String fileName() {
                return "attribute_balance.yml";
            }

            @Override
            public String labelKey() {
                return "editor.root." + ROOT_ATTRIBUTE_BALANCE;
            }

            @Override
            public Material icon() {
                return Material.GOLD_INGOT;
            }

            @Override
            public BiFunction<File, String, String> fileId() {
                return (file, directory) -> ROOT_ATTRIBUTE_BALANCE;
            }
        };
    }

    private static String contentFileId(File file, String idKey) {
        try {
            YamlSection section = YamlFiles.load(file);
            Object raw = section == null ? null : section.get(idKey);
            String id = Texts.normalizeId(raw == null ? "" : Texts.toStringSafe(raw));
            if (!id.isEmpty()) {
                return id;
            }
        } catch (RuntimeException failure) {
        }
        return fileStem(file.getName());
    }

    private static String fileStem(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String specificIcon(String fieldId) {
        return switch (fieldId) {
            case "attr_display_name", "damage_display_name", "preset_display_name" -> "NAME_TAG";
            case "attr_value_kind", "cond_type", "stage_kind" -> "COMPARATOR";
            case "attr_priority", "preset_priority", "format_precision", "format_read_priority" -> "CLOCK";
            case "attr_child_bonuses", "preset_values_nav" -> "LADDER";
            case "rule_enabled", "damage_hard_lock" -> "REDSTONE_TORCH";
            case "damage_stages", "cond_entries" -> "CHEST";
            case "damage_recovery_nav", "recovery_source", "recovery_resistance_source" -> "GOLDEN_APPLE";
            case "format_pattern", "format_read_patterns" -> "WRITABLE_BOOK";
            case "balance_attributes_nav" -> "BOOKSHELF";
            case "balance_scores_nav" -> "GOLD_INGOT";
            case "attr_description", "damage_description", "preset_description" -> "FEATHER";
            default -> null;
        };
    }

    private static Map<String, Object> attributeSkeleton(String id) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", id);
        root.put("display_name", "<white>" + id + "</white>");
        root.put("value_kind", "FLAT");
        root.put("target_type", "GENERIC");
        root.put("default_value", 0.0D);
        root.put("allow_negative", true);
        root.put("priority", 0);
        root.put("lore_format_id", "default_flat");
        root.put("attribute_power", 1.0D);
        root.put("description", "");
        return root;
    }

    private static Map<String, Object> conditionSkeleton(String id) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("type", "all_of");
        condition.put("invalid_as_failure", true);
        condition.put("entries", new ArrayList<>());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("enabled", true);
        root.put("source_id", id);
        root.put("condition", condition);
        return root;
    }

    private static Map<String, Object> damageTypeSkeleton(String id) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", id);
        root.put("display_name", "<white>" + id + "</white>");
        root.put("aliases", new ArrayList<>());
        root.put("allowed_events", new ArrayList<>());
        root.put("hard_lock", false);
        root.put("stages", new ArrayList<>());
        root.put("description", "");
        return root;
    }

    private static Map<String, Object> loreFormatSkeleton(String id) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", id);
        root.put("format", "%name% %sign%%value%");
        root.put("precision", 2);
        root.put("read_priority", 50);
        root.put("read_patterns", new ArrayList<>());
        return root;
    }

    private static Map<String, Object> presetSkeleton(String id) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", id);
        root.put("display_name", "<white>" + id + "</white>");
        root.put("priority", 0);
        root.put("values", new LinkedHashMap<>());
        root.put("description", "");
        return root;
    }
}
