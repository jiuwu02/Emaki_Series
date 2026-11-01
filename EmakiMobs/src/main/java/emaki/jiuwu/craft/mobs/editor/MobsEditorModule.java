package emaki.jiuwu.craft.mobs.editor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
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
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.mobs.EmakiMobsPlugin;
import emaki.jiuwu.craft.mobs.loader.MobDefinitionYamlLoader;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.loot.LootTableDefinition;
import emaki.jiuwu.craft.mobs.loot.LootTableYamlLoader;
import emaki.jiuwu.craft.mobs.selector.SelectorMode;
import emaki.jiuwu.craft.mobs.spawner.SpawnTrigger;

public final class MobsEditorModule implements EditorModule {

    public static final String ROOT_MOBS = "mobs";
    public static final String ROOT_LOOT_TABLES = "loot_tables";
    public static final String ROOT_SPAWN_RULES = "spawn_rules";
    public static final String ROOT_TARGET_SELECTORS = "target_selectors";

    private static final String TYPE_PREVIEW = "preview";

    private static final List<EditorBrowserRoot> ROOTS = List.of(
            new EditorBrowserRoot() {
                @Override
                public String id() {
                    return ROOT_MOBS;
                }

                @Override
                public String directory() {
                    return ROOT_MOBS;
                }

                @Override
                public String labelKey() {
                    return "editor.root.mobs";
                }

                @Override
                public Material icon() {
                    return Material.ZOMBIE_HEAD;
                }

                @Override
                public BiFunction<File, String, String> fileId() {
                    return MobsEditorModule::mobFileId;
                }
            },
            new EditorBrowserRoot() {
                @Override
                public String id() {
                    return ROOT_LOOT_TABLES;
                }

                @Override
                public String directory() {
                    return ROOT_LOOT_TABLES;
                }

                @Override
                public String labelKey() {
                    return "editor.root.loot_tables";
                }

                @Override
                public Material icon() {
                    return Material.CHEST;
                }

                @Override
                public BiFunction<File, String, String> fileId() {
                    return MobsEditorModule::lootFileId;
                }
            },
            new EditorBrowserRoot() {
                @Override
                public String id() {
                    return ROOT_SPAWN_RULES;
                }

                @Override
                public String directory() {
                    return ROOT_SPAWN_RULES;
                }

                @Override
                public String labelKey() {
                    return "editor.root.spawn_rules";
                }

                @Override
                public Material icon() {
                    return Material.GRASS_BLOCK;
                }

                @Override
                public BiFunction<File, String, String> fileId() {
                    return MobsEditorModule::spawnFileId;
                }
            },
            new EditorBrowserRoot() {
                @Override
                public String id() {
                    return ROOT_TARGET_SELECTORS;
                }

                @Override
                public String directory() {
                    return "";
                }

                @Override
                public String fileName() {
                    return "target_selectors.yml";
                }

                @Override
                public String labelKey() {
                    return "editor.root.target_selectors";
                }

                @Override
                public Material icon() {
                    return Material.SPYGLASS;
                }

                @Override
                public BiFunction<File, String, String> fileId() {
                    return (file, directory) -> ROOT_TARGET_SELECTORS;
                }
            });

    private final EmakiMobsPlugin plugin;
    private EditorService service;

    public MobsEditorModule(EmakiMobsPlugin plugin) {
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
        return "emakimobs";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        return ROOTS;
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return MobsEditorMenus.meta(menuId);
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return MobsEditorMenus.fields(session, menuId);
    }

    @Override
    public String homeMenuId() {
        return MobsEditorMenus.HOME;
    }

    @Override
    public String titleKey(String menuId) {
        return MobsEditorMenus.titleKey(menuId);
    }

    @Override
    public EditorValidator validator(EditorBrowserRoot root, Path file) {
        return validatorFor(plugin, root == null ? ROOT_MOBS : root.id());
    }

    @Override
    public void reload() {
        plugin.reloadContent();
    }

    @Override
    public List<EditorField> extraFields(EditorSession session, String menuId) {
        if (!MobsEditorMenus.SELECTORS.equals(menuId)) {
            return List.of();
        }
        return MobsEditorMenus.selectorEntries(session);
    }

    @Override
    public boolean handleFieldOverride(EditorSession session, EditorField field, GuiClickContext click) {
        String id = field.id();
        if (id == null || !id.startsWith(MobsEditorMenus.SELECTOR_OPEN_PREFIX)) {
            return false;
        }
        if (click == null || click.isLeftClick()) {
            String name = id.substring(MobsEditorMenus.SELECTOR_OPEN_PREFIX.length());
            session.putContext(MobsEditorMenus.CONTEXT_SELECTOR, name);
            if (service != null) {
                service.openMenu(session, MobsEditorMenus.SELECTOR_PREFIX + name);
            }
        }
        return true;
    }

    @Override
    public ItemStack renderCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot) {
        if (slot == null || slot.definition() == null
                || !TYPE_PREVIEW.equals(Texts.lower(slot.definition().type()))) {
            return null;
        }
        return renderPreview(session);
    }

    @Override
    public Map<String, Object> skeleton(EditorBrowserRoot root, String id) {
        if (root == null) {
            return null;
        }
        return switch (root.id()) {
            case ROOT_LOOT_TABLES -> lootSkeleton(id);
            case ROOT_SPAWN_RULES -> spawnSkeleton(id);
            case ROOT_MOBS -> mobSkeleton(id);
            default -> null;
        };
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

    public static EditorValidator validatorFor(JavaPlugin plugin, String rootId) {
        return switch (rootId == null ? "" : rootId) {
            case ROOT_LOOT_TABLES -> lootTableValidator(plugin);
            case ROOT_SPAWN_RULES -> spawnRuleValidator();
            case ROOT_TARGET_SELECTORS -> targetSelectorValidator();
            default -> mobValidator(plugin);
        };
    }

    private static EditorValidator mobValidator(JavaPlugin plugin) {
        MobDefinitionYamlLoader probe = new MobDefinitionYamlLoader(plugin);
        return EditorValidator.allOf(EditorValidator.yamlOnly(), (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                MobSpec spec = probe.parseDefinition(source.toFile(), section);
                if (spec == null) {
                    return "definition_parse_failed";
                }
                return idProblem(candidate, docId, source);
            } catch (RuntimeException failure) {
                return "definition_parse_failed";
            }
        });
    }

    private static EditorValidator lootTableValidator(JavaPlugin plugin) {
        LootTableYamlLoader probe = new LootTableYamlLoader(plugin);
        return EditorValidator.allOf(EditorValidator.yamlOnly(), (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                LootTableDefinition definition = probe.parseDefinition(source.toFile(), section);
                if (definition == null) {
                    return "definition_parse_failed";
                }
                return idProblem(candidate, docId, source);
            } catch (RuntimeException failure) {
                return "definition_parse_failed";
            }
        });
    }

    private static EditorValidator spawnRuleValidator() {
        return EditorValidator.allOf(EditorValidator.yamlOnly(), (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                Object rules = section.get("rules");
                if (!(rules instanceof List<?> list)) {
                    return "definition_parse_failed";
                }
                for (Object entry : list) {
                    if (!validSpawnRule(entry)) {
                        return "definition_parse_failed";
                    }
                }
                return idProblem(candidate, docId, source);
            } catch (RuntimeException failure) {
                return "definition_parse_failed";
            }
        });
    }

    private static boolean validSpawnRule(Object entry) {
        if (!(entry instanceof Map<?, ?> rule)) {
            return false;
        }
        Object mobId = rule.get("mob_id");
        if (!(mobId instanceof String id) || id.isBlank()) {
            return false;
        }
        Object type = rule.get("type");
        if (!(type instanceof String ruleType)) {
            return false;
        }
        if ("natural".equals(ruleType)) {
            return true;
        }
        if (!"autonomous".equals(ruleType)) {
            return false;
        }
        Object trigger = rule.get("trigger");
        return trigger instanceof String triggerName && SpawnTrigger.fromString(triggerName) != null;
    }

    private static EditorValidator targetSelectorValidator() {
        return EditorValidator.allOf(EditorValidator.yamlOnly(), (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                Object selectors = section.get("selectors");
                if (!(selectors instanceof Map<?, ?> selectorMap)) {
                    return "definition_parse_failed";
                }
                Set<String> tableIds = equipmentTableIds(section);
                for (Object value : selectorMap.values()) {
                    if (!validSelector(value, tableIds)) {
                        return "definition_parse_failed";
                    }
                }
                if (!optionalNumber(section, "snapshot_interval_ticks")
                        || !optionalNumber(section, "snapshot_players_per_tick")) {
                    return "definition_parse_failed";
                }
                return idProblem(candidate, docId, source);
            } catch (RuntimeException failure) {
                return "definition_parse_failed";
            }
        });
    }

    private static boolean validSelector(Object value, Set<String> tableIds) {
        if (!(value instanceof Map<?, ?> selector)) {
            return false;
        }
        Object mode = selector.get("mode");
        if (mode != null && selectorMode(mode) == null) {
            return false;
        }
        Object range = selector.get("range");
        if (range != null && !(range instanceof Number)) {
            return false;
        }
        Object filter = selector.get("filter");
        if (filter != null && !(filter instanceof List<?>)) {
            return false;
        }
        Object score = selector.get("score");
        if (score == null) {
            return true;
        }
        if (!(score instanceof List<?> terms)) {
            return false;
        }
        for (Object term : terms) {
            if (!validScoreTerm(term, tableIds)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validScoreTerm(Object term, Set<String> tableIds) {
        if (!(term instanceof Map<?, ?> entry)) {
            return false;
        }
        Object type = entry.get("type");
        if (!(type instanceof String rawType)) {
            return false;
        }
        String scoreType = Texts.toStringSafe(rawType).trim().toLowerCase(Locale.ROOT);
        switch (scoreType) {
            case "threat", "distance", "health" -> {
            }
            case "equipment" -> {
                Object table = entry.get("table");
                if (!(table instanceof String rawTable)
                        || !tableIds.contains(Texts.toStringSafe(rawTable).trim().toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
            case "expression" -> {
                Object expression = entry.get("value");
                if (!(expression instanceof String rawExpression) || rawExpression.isBlank()) {
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
        Object factor = entry.get("factor");
        return factor == null || factor instanceof Number;
    }

    private static Set<String> equipmentTableIds(YamlSection section) {
        Object tables = section.get("equipment_tables");
        if (!(tables instanceof Map<?, ?> map)) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (Object key : map.keySet()) {
            ids.add(Texts.toStringSafe(key).trim().toLowerCase(Locale.ROOT));
        }
        return ids;
    }

    private static SelectorMode selectorMode(Object raw) {
        try {
            return SelectorMode.valueOf(Texts.toStringSafe(raw).trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static boolean optionalNumber(YamlSection section, String key) {
        Object value = section.get(key);
        return value == null || value instanceof Number;
    }

    private static String idProblem(YamlTextDocument candidate, String docId, Path source) {
        Object raw = candidate.value("id");
        String actual = Texts.normalizeId(raw == null ? "" : Texts.toStringSafe(raw));
        String stemId = Texts.normalizeId(fileStem(source.getFileName().toString()));
        String expected = docId == null || docId.isEmpty() ? stemId : docId;
        if (actual.isEmpty()) {
            return expected.equals(stemId) ? null : "id_changed";
        }
        return actual.equals(expected) ? null : "id_changed";
    }

    private ItemStack renderPreview(EditorSession session) {
        YamlTextDocument draft = session.draft();
        String fileName = String.valueOf(session.document().path().getFileName());
        String label = Texts.toStringSafe(draft.valueLenient("display_name"));
        if (label.isBlank()) {
            label = Texts.toStringSafe(draft.valueLenient("id"));
        }
        if (label.isBlank()) {
            label = fileName;
        }
        String type = Texts.toStringSafe(draft.valueLenient("type"));
        if (type.isBlank()) {
            type = Texts.toStringSafe(draft.valueLenient("id"));
        }
        Material material = null;
        if (!type.isBlank()) {
            material = Material.matchMaterial(type.trim().toUpperCase(Locale.ROOT) + "_SPAWN_EGG");
        }
        String source = material == null
                ? "minecraft-paper"
                : "minecraft-" + material.name().toLowerCase(Locale.ROOT);
        ConfiguredItemDefinition definition = new ConfiguredItemDefinition(source, 1, Map.of(
                "minecraft:custom_name", ItemComponentPatch.set(label),
                "minecraft:lore", ItemComponentPatch.set(List.of(fileName))));
        return GuiItemBuilder.build(definition, Map.of(),
                EmakiCoreLibPlugin.lookup().configuredItemService());
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
                (dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
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

    private static Map<String, Object> mobSkeleton(String id) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", id);
        root.put("type", entityTypeName(id));
        root.put("display_name", id);
        root.put("components", new LinkedHashMap<>());
        root.put("experience", 0);
        return root;
    }

    private static Map<String, Object> lootSkeleton(String id) {
        Map<String, Object> entries = new LinkedHashMap<>();
        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("rolls", 1);
        pool.put("entries", entries);
        Map<String, Object> pools = new LinkedHashMap<>();
        pools.put("main", pool);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mob_id", id);
        root.put("pools", pools);
        return root;
    }

    private static Map<String, Object> spawnSkeleton(String id) {
        Map<String, Object> count = new LinkedHashMap<>();
        count.put("min", 1);
        count.put("max", 1);
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("mob_id", id);
        rule.put("type", "natural");
        rule.put("biomes", List.of("plains"));
        rule.put("y_range", List.of(-64, 100));
        rule.put("light_level_max", 7);
        rule.put("replacement_chance", 1.0);
        rule.put("max_nearby", 0);
        rule.put("count", count);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("rules", List.of(rule));
        return root;
    }

    private static String entityTypeName(String id) {
        try {
            EntityType.valueOf(id.toUpperCase(Locale.ROOT));
            return id.toLowerCase(Locale.ROOT);
        } catch (RuntimeException failure) {
            return "zombie";
        }
    }

    private static String mobFileId(File file, String directory) {
        return contentFileId(file, "id");
    }

    private static String lootFileId(File file, String directory) {
        return contentFileId(file, "mob_id");
    }

    private static String spawnFileId(File file, String directory) {
        return Texts.normalizeId(fileStem(file.getName()));
    }

    private static String contentFileId(File file, String key) {
        String stem = Texts.normalizeId(fileStem(file.getName()));
        try {
            YamlSection section = YamlFiles.load(file);
            Object raw = section == null ? null : section.get(key);
            String id = Texts.normalizeId(raw == null ? "" : Texts.toStringSafe(raw));
            if (!id.isEmpty()) {
                return id;
            }
        } catch (RuntimeException ignored) {
        }
        return stem;
    }

    private static String fileStem(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
