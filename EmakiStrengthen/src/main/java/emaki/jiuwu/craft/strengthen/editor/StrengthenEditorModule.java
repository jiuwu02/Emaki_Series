package emaki.jiuwu.craft.strengthen.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader.LoadedYamlEntry;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipeParser;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipeParser;

public final class StrengthenEditorModule implements EditorModule {

    public static final String ROOT_RECIPES = "recipes";
    public static final String ROOT_ENHANCEMENT_RECIPES = "enhancement_recipes";

    public static final String TYPE_PREVIEW = "preview";

    private static final String STAR_PREFIX = "star.";

    private final EmakiStrengthenPlugin plugin;
    private EditorService service;

    public StrengthenEditorModule(EmakiStrengthenPlugin plugin) {
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
        return "emakistrengthen";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        return List.of(directoryRoot(ROOT_RECIPES), directoryRoot(ROOT_ENHANCEMENT_RECIPES));
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return StrengthenEditorMenus.meta(menuId);
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return StrengthenEditorMenus.fields(session, menuId);
    }

    @Override
    public String homeMenuId() {
        return StrengthenEditorMenus.HOME;
    }

    @Override
    public String titleKey(String menuId) {
        return StrengthenEditorMenus.titleKey(menuId);
    }

    @Override
    public EditorValidator validator(EditorBrowserRoot root, Path file) {
        return validatorFor(root == null ? null : root.id());
    }

    @Override
    public void reload() {
        if (plugin != null) {
            plugin.reloadPluginStateAsync(true);
        }
    }

    @Override
    public List<EditorField> extraFields(EditorSession session, String menuId) {
        if (StrengthenEditorMenus.STARS.equals(menuId)) {
            return StrengthenEditorMenus.starEntries(session);
        }
        return List.of();
    }

    @Override
    public Material fieldIcon(EditorField field) {
        String id = field.id() == null ? "" : field.id();
        if (id.startsWith(STAR_PREFIX)) {
            return Material.NETHER_STAR;
        }
        return switch (id) {
            case "home_schema_version", "limit_max_star", "limit_max_temper" -> Material.CLOCK;
            case "home_display_name", "star_name" -> Material.NAME_TAG;
            case "home_gui_template" -> Material.CHEST;
            case "home_economy", "economy_enabled", "economy_currencies" -> Material.GOLD_INGOT;
            case "home_limits" -> Material.REDSTONE;
            case "home_success_rates" -> Material.COMPARATOR;
            case "home_matcher", "matcher_type", "matcher_operator" -> Material.COMPARATOR;
            case "home_stars" -> Material.NETHER_STAR;
            case "home_branch_tree" -> Material.OAK_SAPLING;
            case "home_source_patterns", "home_slot_groups" -> Material.PAPER;
            case "home_name_actions" -> Material.NAME_TAG;
            case "home_lore_actions" -> Material.WRITABLE_BOOK;
            case "home_mode" -> Material.EMERALD;
            case "home_target", "target_provider" -> Material.ARMOR_STAND;
            case "home_materials", "star_materials", "material_item_sources" -> Material.IRON_INGOT;
            case "home_costs", "cost_provider" -> Material.GOLD_NUGGET;
            case "home_pity", "pity_group" -> Material.REPEATING_COMMAND_BLOCK;
            case "home_pity_tracks" -> Material.COMMAND_BLOCK;
            case "home_condition", "condition_type" -> Material.LEVER;
            case "home_actions", "star_actions", "actions_on_success" -> Material.REDSTONE;
            default -> null;
        };
    }

    @Override
    public boolean handleFieldOverride(EditorSession session, EditorField field, GuiClickContext click) {
        String id = field.id() == null ? "" : field.id();
        if (!id.startsWith(STAR_PREFIX)) {
            return false;
        }
        String starKey = id.substring(STAR_PREFIX.length());
        if (starKey.isEmpty() || service == null) {
            return true;
        }
        session.putContext(StrengthenEditorMenus.STAR_PATH_KEY, "stars." + starKey);
        service.openMenu(session, StrengthenEditorMenus.STAR_ENTRY);
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
        collectDirectoryIds(ROOT_RECIPES, ids);
        if (plugin != null && plugin.enhancementRecipeLoader() != null) {
            collectEntryIds(plugin.enhancementRecipeLoader().entries(), ids);
        }
        ids.sort(String::compareTo);
        return ids;
    }

    public static EditorValidator validatorFor(String rootId) {
        return (candidate, docId, source) -> EditorValidator.allOf(
                EditorValidator.yamlOnly(),
                EditorValidator.idStable(docId),
                structureCheck(rootId)).validate(candidate, docId, source);
    }

    private static EditorValidator structureCheck(String rootId) {
        return (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                if (ROOT_ENHANCEMENT_RECIPES.equals(rootId)) {
                    return EnhancementRecipeParser.parse(section) == null
                            ? "definition_parse_failed"
                            : null;
                }
                if (StrengthenRecipeParser.parse(section) == null) {
                    return "definition_parse_failed";
                }
                return recipeStructureProblem(section.asMap());
            } catch (RuntimeException failure) {
                return "definition_parse_failed";
            }
        };
    }

    private static String recipeStructureProblem(Map<String, Object> root) {
        Object matcher = root.get("matcher");
        if (matcher != null && !(matcher instanceof Map<?, ?>)) {
            return "definition_parse_failed";
        }
        Object successRates = root.get("success_rates");
        if (successRates != null) {
            if (!(successRates instanceof Map<?, ?> rates)) {
                return "definition_parse_failed";
            }
            for (Object value : rates.values()) {
                if (!(value instanceof Number)) {
                    return "definition_parse_failed";
                }
            }
        }
        Object stars = root.get("stars");
        if (stars != null && starMapProblem(stars) != null) {
            return "definition_parse_failed";
        }
        Object branchTree = root.get("branch_tree");
        if (branchTree != null && branchTreeProblem(branchTree) != null) {
            return "definition_parse_failed";
        }
        return null;
    }

    private static String starMapProblem(Object stars) {
        if (!(stars instanceof Map<?, ?> map)) {
            return "invalid_stars";
        }
        for (Object stage : map.values()) {
            if (!(stage instanceof Map<?, ?>)) {
                return "invalid_stars";
            }
        }
        return null;
    }

    private static String branchTreeProblem(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            return "invalid_branch_tree";
        }
        Object stars = map.get("stars");
        if (stars != null && starMapProblem(stars) != null) {
            return "invalid_branch_tree";
        }
        Object children = map.get("children");
        if (children == null) {
            return null;
        }
        if (!(children instanceof Map<?, ?> childMap)) {
            return "invalid_branch_tree";
        }
        for (Object child : childMap.values()) {
            if (branchTreeProblem(child) != null) {
                return "invalid_branch_tree";
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> skeleton(EditorBrowserRoot root, String id) {
        if (root == null) {
            return null;
        }
        return switch (root.id()) {
            case ROOT_RECIPES -> recipeSkeleton(id);
            case ROOT_ENHANCEMENT_RECIPES -> enhancementSkeleton(id);
            default -> null;
        };
    }

    private File findFile(EditorBrowserRoot root, String id) {
        if (root.fileName() != null) {
            File file = new File(plugin.getDataFolder(), root.fileName());
            return file.isFile() && id.equals(root.fileId().apply(file, root.directory())) ? file : null;
        }
        if (ROOT_ENHANCEMENT_RECIPES.equals(root.id())) {
            if (plugin.enhancementRecipeLoader() == null) {
                return null;
            }
            return findEntry(plugin.enhancementRecipeLoader().entries(), id);
        }
        File directory = plugin.dataPath(ROOT_RECIPES).toFile();
        File[] files = directory.listFiles((dir, name) -> {
            String lower = Texts.lower(name);
            return lower.endsWith(".yml") || lower.endsWith(".yaml");
        });
        if (files == null) {
            return null;
        }
        Arrays.sort(files);
        for (File file : files) {
            if (id.equals(resolveFileId(file, ROOT_RECIPES))) {
                return file;
            }
        }
        return null;
    }

    private void collectDirectoryIds(String rootId, List<String> sink) {
        if (plugin == null) {
            return;
        }
        File directory = plugin.dataPath(rootId).toFile();
        File[] files = directory.listFiles((dir, name) -> {
            String lower = Texts.lower(name);
            return lower.endsWith(".yml") || lower.endsWith(".yaml");
        });
        if (files == null) {
            return;
        }
        Arrays.sort(files);
        for (File file : files) {
            String id = resolveFileId(file, rootId);
            if (!id.isEmpty() && !sink.contains(id)) {
                sink.add(id);
            }
        }
    }

    private static void collectEntryIds(Map<String, ? extends LoadedYamlEntry<?>> entries, List<String> sink) {
        for (String key : entries.keySet()) {
            String id = Texts.normalizeId(key);
            if (!id.isEmpty() && !sink.contains(id)) {
                sink.add(id);
            }
        }
    }

    private static File findEntry(Map<String, ? extends LoadedYamlEntry<?>> entries, String id) {
        for (Map.Entry<String, ? extends LoadedYamlEntry<?>> entry : entries.entrySet()) {
            if (id.equals(Texts.normalizeId(entry.getKey()))) {
                return entry.getValue().file();
            }
        }
        return null;
    }

    private static EditorBrowserRoot directoryRoot(String directory) {
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
            public boolean supportsCreate() {
                return true;
            }

            @Override
            public boolean supportsDelete() {
                return true;
            }

            @Override
            public BiFunction<File, String, String> fileId() {
                return StrengthenEditorModule::resolveFileId;
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

    private ItemStack renderPreview(EditorSession session) {
        if (plugin != null) {
            try {
                String itemType = Texts
                        .toStringSafe(session.draft().value("target", "filter", "item_type"))
                        .trim();
                if (!itemType.isEmpty()) {
                    Material material = Material.matchMaterial(itemType.toUpperCase(Locale.ROOT));
                    if (material != null && material.isItem()) {
                        return GuiItemBuilder.build(new ConfiguredItemDefinition(
                                "minecraft-" + material.name().toLowerCase(Locale.ROOT), 1, Map.of()),
                                Map.of(), plugin.coreLib().configuredItemService());
                    }
                }
                String token = sourceToken(session);
                if (token != null && !token.isBlank()) {
                    ItemStack created = plugin.coreItemSourceService()
                            .createItem(ItemSourceUtil.parse(token), 1);
                    if (created != null) {
                        return created;
                    }
                }
            } catch (RuntimeException failure) {
            }
        }
        return fallbackPreview(session);
    }

    private ItemStack fallbackPreview(EditorSession session) {
        String file = session.document().path().getFileName() == null
                ? ""
                : session.document().path().getFileName().toString();
        String name = session.document().docId();
        if (name.isBlank()) {
            name = file;
        }
        ConfiguredItemDefinition definition = new ConfiguredItemDefinition("minecraft-paper", 1, Map.of(
                "minecraft:custom_name", ItemComponentPatch.set("<white>" + name + "</white>"),
                "minecraft:lore", ItemComponentPatch.set(List.of("<dark_gray>" + file + "</dark_gray>"))));
        return GuiItemBuilder.build(definition, Map.of(), plugin == null
                ? null
                : plugin.coreLib().configuredItemService());
    }

    private static String sourceToken(EditorSession session) {
        Object patterns = session.draft().value("source_patterns");
        if (patterns instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof String token && isPlainSource(token)) {
                    return token;
                }
            }
        }
        return null;
    }

    private static boolean isPlainSource(String token) {
        return !token.isBlank() && token.matches("[a-z0-9_:.\\-]+");
    }

    private static Map<String, Object> recipeSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("schema_version", 2);
        skeleton.put("id", id);
        skeleton.put("display_name", "<white>" + id + "</white>");
        skeleton.put("gui_template", "strengthen_gui");
        Map<String, Object> economy = new LinkedHashMap<>();
        economy.put("enabled", false);
        economy.put("currencies", new ArrayList<>());
        skeleton.put("economy", economy);
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("max_star", 5);
        limits.put("max_temper", 4);
        limits.put("temper_chance_bonus_per_level", 5.0D);
        limits.put("success_chance_cap", 90.0D);
        skeleton.put("limits", limits);
        Map<String, Object> successRates = new LinkedHashMap<>();
        successRates.put("1", 100.0D);
        skeleton.put("success_rates", successRates);
        Map<String, Object> matcher = new LinkedHashMap<>();
        matcher.put("type", "component");
        matcher.put("component", "custom_name");
        matcher.put("operator", "exists");
        skeleton.put("matcher", matcher);
        skeleton.put("slot_groups", new ArrayList<>(List.of("generic")));
        skeleton.put("source_patterns", new ArrayList<>(List.of("^emakiitem-" + id + "$")));
        skeleton.put("name_actions", new ArrayList<>());
        skeleton.put("lore_actions", new ArrayList<>());
        skeleton.put("stars", new LinkedHashMap<>());
        return skeleton;
    }

    private static Map<String, Object> enhancementSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("schema_version", 2);
        skeleton.put("id", id);
        skeleton.put("mode", "equipment");
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("provider", "equipment");
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("item_type", "SWORD");
        target.put("filter", filter);
        skeleton.put("target", target);
        skeleton.put("materials", new ArrayList<>());
        skeleton.put("costs", new ArrayList<>());
        skeleton.put("chance", 1.0D);
        Map<String, Object> pity = new LinkedHashMap<>();
        pity.put("scope", "player");
        pity.put("group", id);
        pity.put("threshold", 3);
        pity.put("effect", "force_success");
        Map<String, Object> decay = new LinkedHashMap<>();
        decay.put("type", "reset");
        decay.put("value", 0);
        pity.put("decay", decay);
        skeleton.put("pity", pity);
        Map<String, Object> actions = new LinkedHashMap<>();
        actions.put("on_success", new ArrayList<>());
        actions.put("on_failure", new ArrayList<>());
        skeleton.put("actions", actions);
        return skeleton;
    }
}
