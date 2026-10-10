package emaki.jiuwu.craft.cooking.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.loader.RecipeParseProbe;
import emaki.jiuwu.craft.cooking.model.StationType;

public final class CookingEditorModule implements EditorModule {

    public static final String ROOT_CHOPPING_BOARD = "chopping_board";
    public static final String ROOT_WOK = "wok";
    public static final String ROOT_GRINDER = "grinder";
    public static final String ROOT_STEAMER = "steamer";
    public static final String ROOT_OVEN = "oven";
    public static final String ROOT_JUICER = "juicer";
    public static final String ROOT_FERMENTATION_BARREL = "fermentation_barrel";
    public static final String ROOT_NUTRITION = "nutrition";
    public static final String ROOT_ITEM_ADJUSTMENTS = "item_adjustments";

    public static final List<String> RECIPE_ROOTS = List.of(
            ROOT_CHOPPING_BOARD,
            ROOT_WOK,
            ROOT_GRINDER,
            ROOT_STEAMER,
            ROOT_OVEN,
            ROOT_JUICER,
            ROOT_FERMENTATION_BARREL);

    public static final String TYPE_PREVIEW = "preview";

    private static final List<String> NUTRITION_VALUE_KEYS = List.of("min", "max", "default");

    private final EmakiCookingPlugin plugin;
    private EditorService service;

    public CookingEditorModule(EmakiCookingPlugin plugin) {
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
        return "emakicooking";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        List<EditorBrowserRoot> roots = new ArrayList<>();
        for (String station : RECIPE_ROOTS) {
            roots.add(directoryRoot(station, "recipes/" + station, true));
        }
        roots.add(directoryRoot(ROOT_NUTRITION, ROOT_NUTRITION, true));
        roots.add(directoryRoot(ROOT_ITEM_ADJUSTMENTS, ROOT_ITEM_ADJUSTMENTS, false));
        return List.copyOf(roots);
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return CookingEditorMenus.meta(menuId);
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return CookingEditorMenus.fields(session, menuId);
    }

    @Override
    public String homeMenuId() {
        return CookingEditorMenus.HOME;
    }

    @Override
    public String titleKey(String menuId) {
        return CookingEditorMenus.titleKey(menuId);
    }

    @Override
    public EditorValidator validator(EditorBrowserRoot root, Path file) {
        return validatorFor(plugin, root == null ? null : root.id());
    }

    @Override
    public void reload() {
        if (plugin != null) {
            plugin.reloadPluginState();
        }
    }

    @Override
    public Map<String, Object> skeleton(EditorBrowserRoot root, String id) {
        return root == null ? null : skeletonFor(root.id(), id);
    }

    @Override
    public ItemStack renderCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot) {
        if (slot == null || slot.definition() == null) {
            return null;
        }
        if (!TYPE_PREVIEW.equals(Texts.lower(slot.definition().type()))) {
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
        for (EditorBrowserRoot root : roots()) {
            for (File file : listYaml(root)) {
                String id = Texts.normalizeId(root.fileId().apply(file, root.directory()));
                if (!id.isEmpty() && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    public static String docType(Path path) {
        if (path == null) {
            return "";
        }
        Path parent = path.getParent();
        if (parent == null || parent.getFileName() == null) {
            return "";
        }
        return parent.getFileName().toString();
    }

    public static EditorValidator validatorFor(EmakiCookingPlugin plugin, String rootId) {
        if (rootId == null || rootId.isEmpty()) {
            return EditorValidator.yamlOnly();
        }
        if (ROOT_ITEM_ADJUSTMENTS.equals(rootId)) {
            return (candidate, docId, source) -> EditorValidator.allOf(
                    EditorValidator.yamlOnly(),
                    (text, id, path) -> adjustmentProblem(loadSection(text)))
                    .validate(candidate, docId, source);
        }
        if (ROOT_NUTRITION.equals(rootId)) {
            return (candidate, docId, source) -> EditorValidator.allOf(
                    EditorValidator.yamlOnly(),
                    EditorValidator.idStable(docId),
                    (text, id, path) -> nutritionProblem(loadSection(text)))
                    .validate(candidate, docId, source);
        }
        StationType stationType = stationTypeOf(rootId);
        if (stationType == null) {
            return EditorValidator.yamlOnly();
        }
        return (candidate, docId, source) -> EditorValidator.allOf(
                EditorValidator.yamlOnly(),
                EditorValidator.idStable(docId),
                recipeCheck(plugin, stationType, rootId))
                .validate(candidate, docId, source);
    }

    public static Map<String, Object> skeletonFor(String rootId, String id) {
        if (ROOT_NUTRITION.equals(rootId)) {
            return nutritionSkeleton(id);
        }
        if (ROOT_ITEM_ADJUSTMENTS.equals(rootId)) {
            return adjustmentSkeleton(id);
        }
        if (RECIPE_ROOTS.contains(rootId)) {
            return recipeSkeleton(rootId, id);
        }
        return null;
    }

    private static EditorValidator recipeCheck(EmakiCookingPlugin plugin, StationType stationType,
            String rootId) {
        return (candidate, docId, source) -> {
            YamlSection section = loadSection(candidate);
            if (section == null) {
                return "unparseable_yaml";
            }
            File file = source == null ? new File(rootId + ".yml") : source.toFile();
            if (RecipeParseProbe.parse(plugin, stationType, file, section) == null) {
                return "definition_parse_failed";
            }
            return entryShapeProblem(section);
        };
    }

    private static String entryShapeProblem(YamlSection section) {
        String problem = mapEntriesProblem(section.get("ingredients"));
        if (problem != null) {
            return problem;
        }
        problem = mapEntriesProblem(section.get("inputs"));
        if (problem != null) {
            return problem;
        }
        Object result = section.get("result");
        if (result instanceof Map<?, ?> branches) {
            for (Object value : branches.values()) {
                if (value instanceof Map<?, ?> branch) {
                    problem = mapEntriesProblem(branch.get("outputs"));
                    if (problem != null) {
                        return problem;
                    }
                }
            }
        }
        return null;
    }

    private static String mapEntriesProblem(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List<?> list)) {
            return "entries_invalid";
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?>)) {
                return "entry_not_a_map";
            }
        }
        return null;
    }

    private static String nutritionProblem(YamlSection section) {
        if (section == null) {
            return "unparseable_yaml";
        }
        for (String key : NUTRITION_VALUE_KEYS) {
            Object raw = section.get(key);
            if (raw != null && !(raw instanceof Number)) {
                return "nutrition_value_invalid";
            }
        }
        return null;
    }

    private static String adjustmentProblem(YamlSection section) {
        if (section == null) {
            return "unparseable_yaml";
        }
        Object sources = section.get("item_sources");
        if (!(sources instanceof List<?> list) || list.isEmpty()) {
            return "item_sources_required";
        }
        Object adjustment = section.get("adjustment");
        if (adjustment != null && !(adjustment instanceof Map<?, ?>)) {
            return "adjustment_invalid";
        }
        Object stations = section.get("stations");
        if (stations == null) {
            return null;
        }
        if (!(stations instanceof Map<?, ?> stationMap)) {
            return "stations_invalid";
        }
        for (Object station : stationMap.values()) {
            if (!(station instanceof Map<?, ?>)) {
                return "stations_invalid";
            }
        }
        return null;
    }

    private static YamlSection loadSection(YamlTextDocument candidate) {
        try {
            return YamlFiles.load(candidate.text());
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static StationType stationTypeOf(String rootId) {
        for (StationType stationType : StationType.values()) {
            if (stationType.folderName().equals(rootId)) {
                return stationType;
            }
        }
        return null;
    }

    private File findFile(EditorBrowserRoot root, String id) {
        for (File file : listYaml(root)) {
            if (id.equals(Texts.normalizeId(root.fileId().apply(file, root.directory())))) {
                return file;
            }
        }
        return null;
    }

    private List<File> listYaml(EditorBrowserRoot root) {
        if (plugin == null || root.directory() == null || root.directory().isBlank()) {
            return List.of();
        }
        File directory = new File(plugin.getDataFolder(), root.directory());
        File[] children = directory.listFiles();
        if (children == null) {
            return List.of();
        }
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        List<File> files = new ArrayList<>();
        for (File child : children) {
            if (child.isFile() && isYaml(child.getName())) {
                files.add(child);
            }
        }
        return files;
    }

    private static boolean isYaml(String name) {
        String lowered = name.toLowerCase(Locale.ROOT);
        return lowered.endsWith(".yml") || lowered.endsWith(".yaml");
    }

    private ItemStack renderPreview(EditorSession session) {
        String token = previewToken(session);
        if (plugin != null && token != null && !token.isBlank()) {
            try {
                ItemStack created = plugin.coreLib().itemSourceService()
                        .createItem(ItemSourceUtil.parse(token), 1);
                if (created != null) {
                    return created;
                }
            } catch (RuntimeException failure) {
            }
        }
        return fallbackPreview(session);
    }

    private static String previewToken(EditorSession session) {
        String type = docType(session.document().path());
        YamlTextDocument draft = session.draft();
        if (ROOT_ITEM_ADJUSTMENTS.equals(type)) {
            Object sources = draft.value("item_sources");
            if (sources instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof String token) {
                return token;
            }
            return null;
        }
        if (RECIPE_ROOTS.contains(type)) {
            return firstSource(draft.value("result", "success", "outputs"));
        }
        return null;
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
        return GuiItemBuilder.build(definition, Map.of(),
                plugin == null ? null : plugin.coreLib().configuredItemService());
    }

    private static String firstSource(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        if (!(list.get(0) instanceof Map<?, ?> entry)) {
            return null;
        }
        Object source = entry.get("item_source");
        if (source instanceof String token && !token.isBlank()) {
            return token;
        }
        Object legacy = entry.get("item_sources");
        if (legacy instanceof List<?> sources && !sources.isEmpty()
                && sources.get(0) instanceof String token && !token.isBlank()) {
            return token;
        }
        return null;
    }

    private static EditorBrowserRoot directoryRoot(String id, String directory, boolean contentId) {
        return new EditorBrowserRoot() {
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
                return "editor.root." + id;
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
                return contentId ? CookingEditorModule::contentFileId : CookingEditorModule::stemFileId;
            }
        };
    }

    private static String contentFileId(File file, String directory) {
        try {
            Object raw = YamlFiles.load(file).asMap().get("id");
            String id = Texts.normalizeId(raw == null ? "" : Texts.toStringSafe(raw));
            if (!id.isEmpty()) {
                return id;
            }
        } catch (RuntimeException failure) {
        }
        return stemFileId(file, directory);
    }

    private static String stemFileId(File file, String directory) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static Map<String, Object> recipeSkeleton(String station, String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("id", id);
        skeleton.put("display_name", "<white>" + id + "</white>");
        switch (station) {
            case ROOT_CHOPPING_BOARD -> {
                Map<String, Object> input = new LinkedHashMap<>();
                input.put("item_sources", List.of("minecraft-carrot"));
                input.put("amount", 1);
                skeleton.put("input", input);
                skeleton.put("cuts_required", 3);
            }
            case ROOT_WOK -> {
                Map<String, Object> ingredient = new LinkedHashMap<>();
                ingredient.put("item_sources", List.of("minecraft-carrot"));
                ingredient.put("amount", 1);
                ingredient.put("stir_rule", "1-3");
                skeleton.put("ingredients", new ArrayList<>(List.of(ingredient)));
                skeleton.put("heat_level", 1);
                Map<String, Object> stirTotal = new LinkedHashMap<>();
                stirTotal.put("min", 1);
                stirTotal.put("max", 3);
                skeleton.put("stir_total", stirTotal);
                skeleton.put("fault_tolerance", 0);
            }
            case ROOT_GRINDER -> {
                Map<String, Object> input = new LinkedHashMap<>();
                input.put("item_sources", List.of("minecraft-bone"));
                skeleton.put("input", input);
                skeleton.put("grind_time_seconds", 6);
            }
            case ROOT_STEAMER -> {
                Map<String, Object> input = new LinkedHashMap<>();
                input.put("item_sources", List.of("minecraft-cod"));
                skeleton.put("input", input);
                skeleton.put("required_steam", 40);
            }
            case ROOT_OVEN -> {
                Map<String, Object> input = new LinkedHashMap<>();
                input.put("item_sources", List.of("minecraft-potato"));
                skeleton.put("input", input);
                skeleton.put("bake_time_seconds", 20);
            }
            case ROOT_JUICER -> {
                Map<String, Object> input = new LinkedHashMap<>();
                input.put("item_sources", List.of("minecraft-apple"));
                skeleton.put("input", input);
                skeleton.put("presses_required", 5);
            }
            case ROOT_FERMENTATION_BARREL -> {
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("slot_id", "main_slot");
                slot.put("count_key", "main");
                slot.put("item_sources", List.of("minecraft-apple"));
                slot.put("amount", 1);
                skeleton.put("inputs", new ArrayList<>(List.of(slot)));
                skeleton.put("fermentation_time_seconds", 300);
            }
            default -> {
            }
        }
        skeleton.put("permission", "");
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("type", "all_of");
        condition.put("entries", new ArrayList<>());
        skeleton.put("condition", condition);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("item_source", "minecraft-bread");
        output.put("amount", 1);
        Map<String, Object> success = new LinkedHashMap<>();
        success.put("outputs", new ArrayList<>(List.of(output)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        skeleton.put("result", result);
        return skeleton;
    }

    private static Map<String, Object> nutritionSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("id", id);
        skeleton.put("display_name", "<white>" + id + "</white>");
        skeleton.put("min", 0);
        skeleton.put("max", 100);
        skeleton.put("default", 0);
        return skeleton;
    }

    private static Map<String, Object> adjustmentSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("item_sources", new ArrayList<>(List.of(id)));
        Map<String, Object> offset = new LinkedHashMap<>();
        offset.put("x", 0.0D);
        offset.put("y", 1.0D);
        offset.put("z", 0.0D);
        Map<String, Object> rotation = new LinkedHashMap<>();
        rotation.put("x", 0.0D);
        rotation.put("y", 0.0D);
        rotation.put("z", 0.0D);
        Map<String, Object> scale = new LinkedHashMap<>();
        scale.put("x", 1.0D);
        scale.put("y", 1.0D);
        scale.put("z", 1.0D);
        Map<String, Object> adjustment = new LinkedHashMap<>();
        adjustment.put("offset", offset);
        adjustment.put("rotation", rotation);
        adjustment.put("scale", scale);
        skeleton.put("adjustment", adjustment);
        Map<String, Object> station = new LinkedHashMap<>();
        station.put("offset", new LinkedHashMap<>(offset));
        station.put("rotation", new LinkedHashMap<>(rotation));
        station.put("scale", new LinkedHashMap<>(scale));
        Map<String, Object> stations = new LinkedHashMap<>();
        stations.put(ROOT_CHOPPING_BOARD, station);
        skeleton.put("stations", stations);
        return skeleton;
    }
}
