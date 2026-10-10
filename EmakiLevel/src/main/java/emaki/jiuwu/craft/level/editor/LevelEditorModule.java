package emaki.jiuwu.craft.level.editor;

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
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.config.RequirementConfig;
import emaki.jiuwu.craft.level.config.SourceRuleConfig;

public final class LevelEditorModule implements EditorModule {

    public static final String ROOT_TYPES = "types";
    public static final String ROOT_SOURCES = "sources";
    public static final String ROOT_REQUIREMENTS = "requirements";

    private static final String OPEN_RULE_PREFIX = "open_rule:";
    private static final String OPEN_GROUP_PREFIX = "open_group:";
    private static final String FIELD_RULE_RULES = "rule_rules";

    private static final List<EditorBrowserRoot> ROOTS = List.of(
            new EditorBrowserRoot() {
                @Override
                public String id() {
                    return ROOT_TYPES;
                }

                @Override
                public String directory() {
                    return ROOT_TYPES;
                }

                @Override
                public String labelKey() {
                    return "editor.root.types";
                }

                @Override
                public Material icon() {
                    return Material.ENCHANTED_BOOK;
                }

                @Override
                public BiFunction<File, String, String> fileId() {
                    return LevelEditorModule::definitionFileId;
                }
            },
            new EditorBrowserRoot() {
                @Override
                public String id() {
                    return ROOT_SOURCES;
                }

                @Override
                public String directory() {
                    return ROOT_SOURCES;
                }

                @Override
                public String labelKey() {
                    return "editor.root.sources";
                }

                @Override
                public Material icon() {
                    return Material.EXPERIENCE_BOTTLE;
                }

                @Override
                public BiFunction<File, String, String> fileId() {
                    return LevelEditorModule::definitionFileId;
                }
            },
            new EditorBrowserRoot() {
                @Override
                public String id() {
                    return ROOT_REQUIREMENTS;
                }

                @Override
                public String directory() {
                    return "";
                }

                @Override
                public String fileName() {
                    return "requirements.yml";
                }

                @Override
                public String labelKey() {
                    return "editor.root.requirements";
                }

                @Override
                public Material icon() {
                    return Material.PAPER;
                }

                @Override
                public BiFunction<File, String, String> fileId() {
                    return (file, directory) -> ROOT_REQUIREMENTS;
                }
            });

    private final EmakiLevelPlugin plugin;
    private EditorService service;

    public LevelEditorModule(EmakiLevelPlugin plugin) {
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
        Object rewards = session.draft().value("upgrade", "rewards", "items");
        if (rewards instanceof List<?> items && !items.isEmpty()
                && items.get(0) instanceof Map<?, ?> reward
                && reward.get("item_source") instanceof String rewardSource && !rewardSource.isBlank()) {
            source = rewardSource;
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
        return "emakilevel";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        return ROOTS;
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return LevelEditorMenus.meta(menuId);
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return LevelEditorMenus.fields(session, menuId);
    }

    @Override
    public String homeMenuId() {
        return LevelEditorMenus.HOME;
    }

    @Override
    public String titleKey(String menuId) {
        return LevelEditorMenus.titleKey(menuId);
    }

    @Override
    public EditorValidator validator(EditorBrowserRoot root, Path file) {
        return validatorFor(root == null ? ROOT_TYPES : root.id());
    }

    public static EditorValidator validatorFor(String rootId) {
        return switch (rootId == null ? "" : rootId) {
            case ROOT_TYPES -> typeValidator();
            case ROOT_SOURCES -> sourceValidator();
            default -> requirementValidator();
        };
    }

    @Override
    public void reload() {
        plugin.reloadPluginState();
    }

    @Override
    public List<EditorField> extraFields(EditorSession session, String menuId) {
        if (!LevelEditorMenus.HOME.equals(menuId)) {
            return List.of();
        }
        LevelEditorMenus.DocKind kind = LevelEditorMenus.docKind(session);
        String sourceKey;
        String fieldPrefix;
        String labelKey;
        if (kind == LevelEditorMenus.DocKind.SOURCE) {
            sourceKey = "sources";
            fieldPrefix = OPEN_RULE_PREFIX;
            labelKey = "editor.field.source_rule";
        } else if (kind == LevelEditorMenus.DocKind.REQUIREMENTS) {
            sourceKey = "groups";
            fieldPrefix = OPEN_GROUP_PREFIX;
            labelKey = "editor.field.requirement_group";
        } else {
            return List.of();
        }
        Object node = session.draft().value(sourceKey);
        if (!(node instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<EditorField> fields = new ArrayList<>();
        for (Object key : map.keySet()) {
            String name = Texts.toStringSafe(key);
            if (name.isEmpty()) {
                continue;
            }
            fields.add(new EditorField(fieldPrefix + name, EditorField.Kind.COMMAND, labelKey,
                    name, null, null, List.of(), List.of(), -1, true));
        }
        return fields;
    }

    @Override
    public boolean handleFieldOverride(EditorSession session, EditorField field, GuiClickContext click) {
        String id = field.id();
        if (id != null && id.startsWith(OPEN_RULE_PREFIX)) {
            if (click == null || click.isLeftClick()) {
                service.openMenu(session,
                        LevelEditorMenus.SOURCE_RULE_PREFIX + id.substring(OPEN_RULE_PREFIX.length()));
            }
            return true;
        }
        if (id != null && id.startsWith(OPEN_GROUP_PREFIX)) {
            if (click == null || click.isLeftClick()) {
                service.openMenu(session,
                        LevelEditorMenus.REQUIREMENT_GROUP_PREFIX + id.substring(OPEN_GROUP_PREFIX.length()));
            }
            return true;
        }
        if (FIELD_RULE_RULES.equals(id) && session.currentMenuId() != null
                && session.currentMenuId().startsWith(LevelEditorMenus.SOURCE_RULE_PREFIX)) {
            session.putContext(LevelEditorMenus.CONTEXT_RULE_NAME,
                    session.currentMenuId().substring(LevelEditorMenus.SOURCE_RULE_PREFIX.length()));
        }
        return false;
    }

    @Override
    public Map<String, Object> skeleton(EditorBrowserRoot root, String id) {
        if (root == null || ROOT_REQUIREMENTS.equals(root.id())) {
            return null;
        }
        if (ROOT_SOURCES.equals(root.id())) {
            return sourceSkeleton(id);
        }
        return typeSkeleton(id);
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

    private Map<String, Object> typeSkeleton(String id) {
        Map<String, Object> requirement = new LinkedHashMap<>();
        requirement.put("group", id);
        requirement.put("formula", "");
        Map<String, Object> costEconomy = new LinkedHashMap<>();
        costEconomy.put("enabled", false);
        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("enabled", false);
        cost.put("economy", costEconomy);
        Map<String, Object> rewards = new LinkedHashMap<>();
        rewards.put("items", List.of());
        Map<String, Object> actions = new LinkedHashMap<>();
        actions.put("success", List.of());
        actions.put("failure", List.of());
        actions.put("gain", List.of());
        Map<String, Object> upgrade = new LinkedHashMap<>();
        upgrade.put("enabled", true);
        upgrade.put("auto_upgrade", true);
        upgrade.put("manual_upgrade", true);
        upgrade.put("cost", cost);
        upgrade.put("rewards", rewards);
        upgrade.put("actions", actions);
        Map<String, Object> pdc = new LinkedHashMap<>();
        pdc.put("enabled", true);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("enabled", false);
        attributes.put("values", new LinkedHashMap<>());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", id);
        root.put("enabled", true);
        root.put("display_name", id);
        root.put("description", List.of());
        root.put("primary", false);
        root.put("start_level", 1);
        root.put("max_level", plugin.appConfig().defaultMaxLevel());
        root.put("requirement", requirement);
        root.put("upgrade", upgrade);
        root.put("pdc", pdc);
        root.put("attributes", attributes);
        return root;
    }

    private Map<String, Object> sourceSkeleton(String id) {
        Map<String, Object> antiAbuse = new LinkedHashMap<>();
        antiAbuse.put("exp_cooldown_ticks", 0);
        antiAbuse.put("ignore_near_spawner", false);
        antiAbuse.put("spawner_scan_radius", 8);
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("enabled", true);
        rule.put("type", plugin.appConfig().primaryType());
        rule.put("trigger", "entity_kill");
        rule.put("include_players", false);
        rule.put("anti_abuse", antiAbuse);
        rule.put("rules", List.of());
        Map<String, Object> grantActions = new LinkedHashMap<>();
        grantActions.put("gain", List.of());
        rule.put("actions", grantActions);
        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("example", rule);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", id);
        root.put("enabled", true);
        root.put("display_name", id);
        root.put("sources", sources);
        return root;
    }

    private static String definitionFileId(File file, String directory) {
        String stem = Texts.normalizeId(fileStem(file.getName()));
        try {
            YamlSection section = YamlFiles.load(file);
            Object raw = section == null ? null : section.get("id");
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

    private static EditorValidator typeValidator() {
        return EditorValidator.allOf(EditorValidator.yamlOnly(), (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                LevelTypeConfig config = LevelTypeConfig.parse(section, fileStem(source.getFileName().toString()),
                        1, 100);
                if (Texts.isBlank(config.id())) {
                    return "definition_parse_failed";
                }
                if (!isMap(section, "requirement") || !isMap(section, "upgrade")
                        || !isMap(section, "pdc") || !isMap(section, "attributes")) {
                    return "definition_parse_failed";
                }
                if (!isNumber(section, "start_level") || !isNumber(section, "max_level")) {
                    return "definition_parse_failed";
                }
                return idProblem(candidate, docId, source);
            } catch (RuntimeException failure) {
                return "definition_parse_failed";
            }
        });
    }

    private static EditorValidator sourceValidator() {
        return EditorValidator.allOf(EditorValidator.yamlOnly(), (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                Object sources = section.get("sources");
                if (!(sources instanceof Map<?, ?> map)) {
                    return "definition_parse_failed";
                }
                if (!isBooleanLike(section, "enabled")) {
                    return "definition_parse_failed";
                }
                for (Map.Entry<?, ?> ruleEntry : map.entrySet()) {
                    if (!(ruleEntry.getValue() instanceof Map<?, ?> rule)) {
                        return "definition_parse_failed";
                    }
                    SourceRuleConfig.parse(Texts.normalizeId(Texts.toStringSafe(ruleEntry.getKey())), rule);
                }
                return idProblem(candidate, docId, source);
            } catch (RuntimeException failure) {
                return "definition_parse_failed";
            }
        });
    }

    private static EditorValidator requirementValidator() {
        return EditorValidator.allOf(EditorValidator.yamlOnly(), (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                if (!isMap(section, "global") || !isMap(section, "groups")) {
                    return "definition_parse_failed";
                }
                Object groupsRaw = section.get("groups");
                if (groupsRaw instanceof Map<?, ?> groups) {
                    for (Object value : groups.values()) {
                        if (!(value instanceof Map<?, ?> group)) {
                            return "definition_parse_failed";
                        }
                        Object formula = group.get("formula");
                        if (formula != null && !(formula instanceof String)
                                && !(formula instanceof Number)) {
                            return "definition_parse_failed";
                        }
                        Object values = group.get("values");
                        if (values != null && !(values instanceof Map<?, ?>)) {
                            return "definition_parse_failed";
                        }
                    }
                }
                RequirementConfig.parse(section);
                return null;
            } catch (RuntimeException failure) {
                return "definition_parse_failed";
            }
        });
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

    private static boolean isMap(YamlSection section, String path) {
        Object value = section.get(path);
        return value == null || value instanceof Map<?, ?>;
    }

    private static boolean isNumber(YamlSection section, String path) {
        Object value = section.get(path);
        if (value == null || value instanceof Number) {
            return true;
        }
        try {
            Double.parseDouble(Texts.toStringSafe(value));
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static boolean isBooleanLike(YamlSection section, String path) {
        Object value = section.get(path);
        if (value == null || value instanceof Boolean) {
            return true;
        }
        String text = Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT);
        return "true".equals(text) || "false".equals(text);
    }
}
