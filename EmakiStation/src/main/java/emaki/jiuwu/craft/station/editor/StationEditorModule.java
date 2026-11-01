package emaki.jiuwu.craft.station.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
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
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader.LoadedYamlEntry;
import emaki.jiuwu.craft.station.EmakiStationPlugin;
import emaki.jiuwu.craft.station.config.PurchaseSettings;
import emaki.jiuwu.craft.station.definition.StationLoader;
import emaki.jiuwu.craft.station.dismantle.DismantleRecipeLoader;
import emaki.jiuwu.craft.station.dismantle.DismantleStationLoader;
import emaki.jiuwu.craft.station.recipe.RecipeCost;
import emaki.jiuwu.craft.station.recipe.RecipeLoader;

public final class StationEditorModule implements EditorModule {

    public static final String ROOT_STATIONS = "stations";
    public static final String ROOT_STATIONS_DISMANTLE = "stations_dismantle";
    public static final String ROOT_RECIPES = "recipes";
    public static final String ROOT_RECIPES_DISMANTLE = "recipes_dismantle";
    public static final String ROOT_QUEUE_COSTS = "queue_costs";

    public static final String TYPE_PREVIEW = "preview";

    private final EmakiStationPlugin plugin;
    private EditorService service;

    public StationEditorModule(EmakiStationPlugin plugin) {
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
        return "emakistation";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        return List.of(
                directoryRoot(ROOT_STATIONS),
                directoryRoot(ROOT_STATIONS_DISMANTLE),
                directoryRoot(ROOT_RECIPES),
                directoryRoot(ROOT_RECIPES_DISMANTLE),
                queueCostsRoot());
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return StationEditorMenus.meta(menuId);
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return StationEditorMenus.fields(session, menuId);
    }

    @Override
    public String homeMenuId() {
        return StationEditorMenus.HOME;
    }

    @Override
    public String titleKey(String menuId) {
        return StationEditorMenus.titleKey(menuId);
    }

    @Override
    public EditorValidator validator(EditorBrowserRoot root, Path file) {
        return validatorFor(plugin, root == null ? null : root.id());
    }

    @Override
    public void reload() {
        if (plugin != null) {
            plugin.reloadContent();
        }
    }

    @Override
    public Map<String, Object> skeleton(EditorBrowserRoot root, String id) {
        return root == null ? null : skeletonFor(root.id(), id);
    }

    public EditorSession openById(Player player, String rawId) {
        String id = Texts.normalizeId(rawId);
        if (service() == null || id.isEmpty()) {
            return null;
        }
        for (EditorBrowserRoot root : roots()) {
            File file = findFile(root, id);
            if (file == null) {
                continue;
            }
            return service().open(player, file.toPath(), id, validator(root, file.toPath()), null);
        }
        return null;
    }

    public List<String> knownIds() {
        List<String> ids = new ArrayList<>();
        collectIds(entriesOf(ROOT_STATIONS), ids);
        collectIds(entriesOf(ROOT_STATIONS_DISMANTLE), ids);
        collectIds(entriesOf(ROOT_RECIPES), ids);
        collectIds(entriesOf(ROOT_RECIPES_DISMANTLE), ids);
        if (queueCostsFile() != null && queueCostsFile().isFile()) {
            ids.add(ROOT_QUEUE_COSTS);
        }
        ids.sort(String::compareTo);
        return ids;
    }

    public static String docType(Path path) {
        if (path == null) {
            return "";
        }
        Path parent = path.getParent();
        if (parent != null && parent.getFileName() != null) {
            String directory = parent.getFileName().toString();
            if (ROOT_STATIONS.equals(directory)
                    || ROOT_STATIONS_DISMANTLE.equals(directory)
                    || ROOT_RECIPES.equals(directory)
                    || ROOT_RECIPES_DISMANTLE.equals(directory)) {
                return directory;
            }
        }
        return ROOT_QUEUE_COSTS;
    }

    public static EditorValidator validatorFor(EmakiStationPlugin plugin, String rootId) {
        if (rootId == null || rootId.isEmpty()) {
            return EditorValidator.yamlOnly();
        }
        if (ROOT_QUEUE_COSTS.equals(rootId)) {
            return (candidate, docId, source) -> {
                YamlSection section = parseSection(candidate.text());
                return section == null ? "unparseable_yaml" : queueCostProblem(section);
            };
        }
        return (candidate, docId, source) -> EditorValidator.allOf(
                EditorValidator.yamlOnly(),
                EditorValidator.idStable(docId),
                parseCheck(plugin, rootId),
                contentCheck(plugin, rootId)).validate(candidate, docId, source);
    }

    public static Map<String, Object> skeletonFor(String rootId, String id) {
        return switch (rootId) {
            case ROOT_STATIONS -> stationSkeleton(id);
            case ROOT_STATIONS_DISMANTLE -> dismantleStationSkeleton(id);
            case ROOT_RECIPES -> recipeSkeleton(id);
            case ROOT_RECIPES_DISMANTLE -> dismantleRecipeSkeleton(id);
            default -> null;
        };
    }

    private static EditorValidator parseCheck(EmakiStationPlugin plugin, String rootId) {
        return (candidate, docId, source) -> {
            YamlSection section = parseSection(candidate.text());
            if (section == null) {
                return "unparseable_yaml";
            }
            File file = source == null ? null : source.toFile();
            Object parsed = switch (rootId) {
                case ROOT_STATIONS -> new StationLoader(plugin, null).parseDefinition(file, section);
                case ROOT_STATIONS_DISMANTLE -> new DismantleStationLoader(plugin)
                        .parseDefinition(file, section);
                case ROOT_RECIPES -> new RecipeLoader(plugin, Integer.MAX_VALUE)
                        .parseDefinition(file, section);
                case ROOT_RECIPES_DISMANTLE -> new DismantleRecipeLoader(plugin)
                        .parseDefinition(file, section);
                default -> null;
            };
            return parsed == null ? "definition_parse_failed" : null;
        };
    }

    private static EditorValidator contentCheck(EmakiStationPlugin plugin, String rootId) {
        return (candidate, docId, source) -> {
            YamlSection section = parseSection(candidate.text());
            if (section == null) {
                return null;
            }
            return switch (rootId) {
                case ROOT_STATIONS -> layoutProblem(plugin, section,
                        "layout", "preview_layout", "queue_layout");
                case ROOT_STATIONS_DISMANTLE -> layoutProblem(plugin, section, "layout");
                case ROOT_RECIPES, ROOT_RECIPES_DISMANTLE -> costProblem(section);
                default -> null;
            };
        };
    }

    private static String layoutProblem(EmakiStationPlugin plugin, YamlSection section, String... keys) {
        if (plugin == null || plugin.layoutLoader() == null) {
            return null;
        }
        for (String key : keys) {
            Object raw = section.get(key);
            if (!(raw instanceof String text)) {
                continue;
            }
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            if (plugin.layoutLoader().get(normalized) == null) {
                return "missing_layout";
            }
        }
        return null;
    }

    private static String costProblem(YamlSection section) {
        Object cost = section.get("cost");
        if (cost == null) {
            return null;
        }
        if (!(cost instanceof Map<?, ?> costMap) || costMap.isEmpty()) {
            return "cost_currency_invalid";
        }
        Object currency = costMap.get("currency");
        if (!(currency instanceof Map<?, ?> currencyMap)) {
            return "cost_currency_invalid";
        }
        Object type = currencyMap.get("type");
        if (!(type instanceof String token)
                || token.isBlank()
                || RecipeCost.fromToken(token, 1L) == null) {
            return "cost_currency_invalid";
        }
        return positiveAmount(currencyMap.get("amount")) ? null : "cost_currency_invalid";
    }

    private static String queueCostProblem(YamlSection section) {
        Object tiers = section.get("tiers");
        if (tiers != null) {
            if (!(tiers instanceof List<?> tierList)) {
                return "queue_costs_invalid";
            }
            for (Object rawTier : tierList) {
                if (!(rawTier instanceof Map<?, ?> tier)) {
                    return "queue_costs_invalid";
                }
                if (!validRange(Texts.toStringSafe(tier.get("count_range")))) {
                    return "queue_costs_invalid";
                }
                Object currency = tier.get("currency");
                Object item = tier.get("item");
                if (currency == null && item == null) {
                    return "queue_costs_invalid";
                }
                if (currency != null && !validCurrency(currency)) {
                    return "queue_costs_invalid";
                }
                if (item != null && !validItem(item)) {
                    return "queue_costs_invalid";
                }
            }
        }
        Object fallback = section.get("fallback");
        if (fallback != null) {
            if (!(fallback instanceof Map<?, ?> fallbackMap)) {
                return "queue_costs_invalid";
            }
            Object currency = fallbackMap.get("currency");
            Object item = fallbackMap.get("item");
            if (currency == null && item == null) {
                return "queue_costs_invalid";
            }
            if (currency != null && !validCurrency(currency)) {
                return "queue_costs_invalid";
            }
            if (item != null && !validItem(item)) {
                return "queue_costs_invalid";
            }
            Object maxAmount = fallbackMap.get("max_amount");
            if (!(maxAmount instanceof Number number) || number.doubleValue() <= 0.0D) {
                return "queue_costs_invalid";
            }
        }
        Object batch = section.get("batch");
        if (batch != null && !(batch instanceof Map<?, ?>)) {
            return "queue_costs_invalid";
        }
        return null;
    }

    private static boolean validRange(String range) {
        if (range == null || range.isBlank()) {
            return false;
        }
        String normalized = range.trim().toLowerCase(Locale.ROOT);
        int dash = normalized.indexOf('-');
        try {
            if (dash < 0) {
                return Integer.parseInt(normalized) > 0;
            }
            int min = Integer.parseInt(normalized.substring(0, dash).trim());
            int max = Integer.parseInt(normalized.substring(dash + 1).trim());
            return min > 0 && max >= min;
        } catch (NumberFormatException failure) {
            return false;
        }
    }

    private static boolean validCurrency(Object raw) {
        if (!(raw instanceof Map<?, ?> currency)) {
            return false;
        }
        Object type = currency.get("type");
        String token = type == null ? RecipeCost.VAULT.toLowerCase(Locale.ROOT)
                : Texts.toStringSafe(type);
        if (token.isBlank() || RecipeCost.fromToken(token, 1L) == null) {
            return false;
        }
        Object amount = currency.get("amount");
        if (amount instanceof Number number) {
            return number.doubleValue() > 0.0D;
        }
        return amount instanceof String expression && !expression.isBlank();
    }

    private static boolean validItem(Object raw) {
        if (!(raw instanceof Map<?, ?> item)) {
            return false;
        }
        Object source = item.get("source");
        if (!(source instanceof String token) || token.isBlank()) {
            return false;
        }
        Object amount = item.get("amount");
        if (amount == null) {
            return true;
        }
        return amount instanceof Number number && number.intValue() > 0;
    }

    private static boolean positiveAmount(Object amount) {
        if (amount instanceof Number number) {
            return number.doubleValue() > 0.0D;
        }
        if (amount instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim()) > 0L;
            } catch (NumberFormatException failure) {
                return false;
            }
        }
        return false;
    }

    private static YamlSection parseSection(String text) {
        try {
            return YamlFiles.load(text);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private File findFile(EditorBrowserRoot root, String id) {
        if (plugin == null) {
            return null;
        }
        if (root.fileName() != null) {
            File file = new File(plugin.getDataFolder(), root.fileName());
            return file.isFile() && id.equals(root.fileId().apply(file, root.directory())) ? file : null;
        }
        return findEntry(entriesOf(root.id()), id);
    }

    private Map<String, ? extends LoadedYamlEntry<?>> entriesOf(String rootId) {
        if (plugin == null) {
            return Map.of();
        }
        return switch (rootId) {
            case ROOT_STATIONS -> plugin.stationLoader() == null
                    ? Map.of()
                    : plugin.stationLoader().entries();
            case ROOT_STATIONS_DISMANTLE -> plugin.dismantleStationLoader() == null
                    ? Map.of()
                    : plugin.dismantleStationLoader().entries();
            case ROOT_RECIPES -> plugin.recipeLoader() == null
                    ? Map.of()
                    : plugin.recipeLoader().entries();
            case ROOT_RECIPES_DISMANTLE -> plugin.dismantleRecipeLoader() == null
                    ? Map.of()
                    : plugin.dismantleRecipeLoader().entries();
            default -> Map.of();
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

    private static String previewToken(EditorSession session) {
        String type = docType(session.document().path());
        YamlTextDocument draft = session.draft();
        if (ROOT_RECIPES.equals(type)) {
            return firstSource(draft.value("result", "outputs"));
        }
        if (ROOT_RECIPES_DISMANTLE.equals(type)) {
            return firstSource(draft.value("pool"));
        }
        return null;
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

    private EditorBrowserRoot directoryRoot(String directory) {
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
                return StationEditorModule::resolveFileId;
            }
        };
    }

    private EditorBrowserRoot queueCostsRoot() {
        return new EditorBrowserRoot() {
            @Override
            public String id() {
                return ROOT_QUEUE_COSTS;
            }

            @Override
            public String directory() {
                return "";
            }

            @Override
            public String fileName() {
                return costFileName();
            }

            @Override
            public String labelKey() {
                return "editor.root." + ROOT_QUEUE_COSTS;
            }

            @Override
            public BiFunction<File, String, String> fileId() {
                return (file, directory) -> ROOT_QUEUE_COSTS;
            }
        };
    }

    private String costFileName() {
        if (plugin == null || plugin.appConfig() == null
                || plugin.appConfig().purchaseSettings() == null) {
            return PurchaseSettings.DEFAULT_COST_FILE;
        }
        return plugin.appConfig().purchaseSettings().normalized().costFile();
    }

    private File queueCostsFile() {
        return plugin == null ? null : new File(plugin.getDataFolder(), costFileName());
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

    private static Map<String, Object> stationSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("id", id);
        skeleton.put("display_name", "<white>" + id + "</white>");
        skeleton.put("layout", "station_catalog");
        skeleton.put("preview_layout", "station_preview");
        skeleton.put("queue_layout", "station_queue");
        skeleton.put("permission", "");
        Map<String, Object> queue = new LinkedHashMap<>();
        queue.put("base_length", 3);
        queue.put("max_length", 16);
        queue.put("progress_mode", "offline");
        queue.put("cancel_refund_rate", 1.0D);
        queue.put("speed_multiplier", 1.0D);
        queue.put("allow_purchase", false);
        skeleton.put("queue", queue);
        Map<String, Object> channels = new LinkedHashMap<>();
        channels.put("backpack", true);
        channels.put("storage", true);
        channels.put("default", "backpack");
        skeleton.put("channels", channels);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("default", "storage_first");
        output.put("player_switchable", true);
        skeleton.put("output", output);
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("invalid_as_failure", true);
        skeleton.put("condition", condition);
        return skeleton;
    }

    private static Map<String, Object> dismantleStationSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("id", id);
        skeleton.put("display_name", "<white>" + id + "</white>");
        skeleton.put("layout", "station_dismantle");
        skeleton.put("permission", "");
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("default", "storage_first");
        skeleton.put("output", output);
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("invalid_as_failure", true);
        skeleton.put("condition", condition);
        return skeleton;
    }

    private static Map<String, Object> recipeSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("id", id);
        skeleton.put("display_name", "<white>" + id + "</white>");
        skeleton.put("station_ids", new ArrayList<>());
        skeleton.put("tags", new ArrayList<>());
        skeleton.put("permission", "");
        skeleton.put("visible", true);
        skeleton.put("duration_seconds", 30);
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("material_id", id + "_material");
        material.put("requirement_id", id + "_material");
        material.put("count_key", id + "_material");
        material.put("item_sources", List.of("minecraft-stone"));
        material.put("amount", 1);
        material.put("consume", true);
        skeleton.put("materials", new ArrayList<>(List.of(material)));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("item_source", "minecraft-stone");
        output.put("amount", 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outputs", new ArrayList<>(List.of(output)));
        skeleton.put("result", result);
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("invalid_as_failure", true);
        skeleton.put("condition", condition);
        Map<String, Object> displayCondition = new LinkedHashMap<>();
        displayCondition.put("invalid_as_failure", false);
        skeleton.put("display_condition", displayCondition);
        return skeleton;
    }

    private static Map<String, Object> dismantleRecipeSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("id", id);
        skeleton.put("display_name", "<white>" + id + "</white>");
        skeleton.put("station_id", "");
        skeleton.put("tags", new ArrayList<>());
        skeleton.put("permission", "");
        skeleton.put("item_sources", new ArrayList<>(List.of("minecraft-cobblestone")));
        Map<String, Object> rolls = new LinkedHashMap<>();
        rolls.put("min", 1);
        rolls.put("max", 1);
        skeleton.put("rolls", rolls);
        Map<String, Object> poolEntry = new LinkedHashMap<>();
        poolEntry.put("item_source", "minecraft-stone");
        poolEntry.put("amount", 1);
        poolEntry.put("weight", 1.0D);
        skeleton.put("pool", new ArrayList<>(List.of(poolEntry)));
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("invalid_as_failure", true);
        skeleton.put("condition", condition);
        return skeleton;
    }
}
