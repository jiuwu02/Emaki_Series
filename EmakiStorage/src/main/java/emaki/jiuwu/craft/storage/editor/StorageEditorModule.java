package emaki.jiuwu.craft.storage.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.editor.EditorBrowserRoot;
import emaki.jiuwu.craft.corelib.editor.EditorDocument;
import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorFeedback;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorModule;
import emaki.jiuwu.craft.corelib.editor.EditorRenderer;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.EditorValidator;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.config.AppConfig;

public final class StorageEditorModule implements EditorModule {

    public static final String ROOT_ID = "unlock_costs";
    public static final String DEFAULT_COST_FILE = "unlock_costs.yml";

    private static final String MENU_HOME = "home";
    private static final String MENU_TIERS = "tiers";
    private static final String MENU_FALLBACK = "fallback";
    private static final String MENU_BATCH = "batch";
    private static final String MENU_OPTION_EDIT = "option_edit";
    private static final String MENU_LIST_ENTRIES = EditorService.MENU_LIST_ENTRIES;

    private static final String FIELD_TIERS_LIST = "tiers";
    private static final String FIELD_OPTIONS_LIST = "batch_options";
    private static final String LIST_PATH_TIERS = "tiers";
    private static final String LIST_PATH_OPTIONS = "batch.options";
    private static final String CONTEXT_ENTRY_INDEX = "entry_index";
    private static final String TYPE_PREVIEW = "preview";

    private static final List<String> CURRENCY_TYPES = List.of("vault", "excellent");
    private static final String[] ROOT_LEVEL = new String[0];
    private static final Map<String, EditorMenuMeta> MENUS = buildMenus();

    private final EmakiStoragePlugin plugin;
    private EditorService service;

    public StorageEditorModule(EmakiStoragePlugin plugin) {
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
        return "emakistorage";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        return List.of(new CostFileRoot());
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return menuId == null ? null : MENUS.get(menuId);
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        if (menuId == null) {
            return List.of();
        }
        return switch (menuId) {
            case MENU_HOME -> home();
            case MENU_TIERS -> tierFields(session);
            case MENU_FALLBACK -> fallbackFields(session);
            case MENU_BATCH -> batchFields();
            case MENU_OPTION_EDIT -> List.of(optionField(session));
            default -> List.of();
        };
    }

    @Override
    public String homeMenuId() {
        return MENU_HOME;
    }

    @Override
    public String titleKey(String menuId) {
        return "editor.menu." + menuId;
    }

    @Override
    public EditorValidator validator(EditorBrowserRoot root, Path file) {
        return UnlockCostsValidator.validator();
    }

    @Override
    public void reload() {
        plugin.reloadPluginState();
    }

    @Override
    public Material fieldIcon(EditorField field) {
        return switch (field.id()) {
            case "nav_fallback" -> Material.GOLD_BLOCK;
            case "nav_batch" -> Material.PAPER;
            case "tier_count_range" -> Material.BOW;
            case "tier_currency_type", "fallback_currency_type" -> Material.GOLD_NUGGET;
            case "tier_currency_amount", "fallback_currency_amount" -> Material.EMERALD;
            case "tier_currency_id", "fallback_currency_id" -> Material.NAME_TAG;
            case "tier_item_source", "fallback_item_source" -> Material.DIAMOND;
            case "tier_item_amount", "fallback_item_amount" -> Material.HOPPER;
            case "fallback_max_amount" -> Material.SHIELD;
            case "batch_enabled" -> Material.REDSTONE_TORCH;
            case "batch_options" -> Material.COMPASS;
            default -> null;
        };
    }

    @Override
    public ItemStack renderCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot) {
        if (!TYPE_PREVIEW.equals(Texts.lower(slot.definition().type()))) {
            return null;
        }
        String fileName = costFileName();
        Path file = plugin.dataPath(fileName);
        ConfiguredItemDefinition definition = new ConfiguredItemDefinition("minecraft-paper", 1, Map.of(
                "minecraft:custom_name", ItemComponentPatch.set("<white>" + fileName + "</white>"),
                "minecraft:lore", ItemComponentPatch.set(List.of(
                        "<dark_gray>" + file + "</dark_gray>"))));
        return GuiItemBuilder.build(definition, Map.of(), plugin.coreLib().configuredItemService());
    }

    @Override
    public boolean handleCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot,
            GuiClickContext click) {
        String type = Texts.lower(slot.definition().type());
        if (EditorService.TYPE_CONFIRM.equals(type) && MENU_LIST_ENTRIES.equals(menuId)) {
            String listPath = session.context(EditorService.CONTEXT_LIST_PATH);
            if (LIST_PATH_TIERS.equals(listPath)) {
                promptTierCreate(session, null);
                return true;
            }
            if (LIST_PATH_OPTIONS.equals(listPath)) {
                promptOptionAppend(session, null);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleFieldOverride(EditorSession session, EditorField field, GuiClickContext click) {
        if (click == null || click.isRightClick() || click.isShiftClick()) {
            return false;
        }
        if (FIELD_TIERS_LIST.equals(field.id())) {
            promptTierCreate(session, field);
            return true;
        }
        if (FIELD_OPTIONS_LIST.equals(field.id())) {
            promptOptionAppend(session, field);
            return true;
        }
        return false;
    }

    private static Map<String, EditorMenuMeta> buildMenus() {
        Map<String, EditorMenuMeta> menus = new LinkedHashMap<>();
        menus.put(MENU_HOME, new EditorMenuMeta(MENU_HOME, "editor.menu.home", MENU_HOME, ROOT_LEVEL, false));
        menus.put(MENU_TIERS, new EditorMenuMeta(MENU_TIERS, "editor.menu.tiers", MENU_HOME, null, true));
        menus.put(MENU_FALLBACK, new EditorMenuMeta(MENU_FALLBACK, "editor.menu.fallback",
                MENU_HOME, new String[] { "fallback" }, false));
        menus.put(MENU_BATCH, new EditorMenuMeta(MENU_BATCH, "editor.menu.batch",
                MENU_HOME, new String[] { "batch" }, false));
        menus.put(MENU_OPTION_EDIT, new EditorMenuMeta(MENU_OPTION_EDIT, "editor.menu.option_edit",
                MENU_BATCH, null, false));
        menus.put(MENU_LIST_ENTRIES, new EditorMenuMeta(MENU_LIST_ENTRIES, "editor.menu.list_entries",
                MENU_HOME, null, true));
        return menus;
    }

    private static List<EditorFieldSpec> home() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.list(FIELD_TIERS_LIST, "editor.field.tiers",
                MENU_TIERS, LIST_PATH_TIERS));
        specs.add(new EditorFieldSpec("nav_fallback", EditorField.Kind.NAVIGATE,
                "editor.field.nav_fallback", new String[] { "fallback" }, List.of(),
                MENU_FALLBACK, null, null));
        specs.add(new EditorFieldSpec("nav_batch", EditorField.Kind.NAVIGATE,
                "editor.field.nav_batch", new String[] { "batch" }, List.of(),
                MENU_BATCH, null, null));
        return specs;
    }

    private static List<EditorFieldSpec> tierFields(EditorSession session) {
        String index = Integer.toString(entryIndex(session));
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.text("tier_count_range", "editor.field.count_range",
                LIST_PATH_TIERS, index, "count_range"));
        curated.add(EditorFieldSpec.cycle("tier_currency_type", "editor.field.currency_type",
                CURRENCY_TYPES, LIST_PATH_TIERS, index, "currency", "type"));
        curated.add(amountSpec(session, "tier_currency_amount", "editor.field.currency_amount",
                LIST_PATH_TIERS, index, "currency", "amount"));
        curated.add(EditorFieldSpec.text("tier_currency_id", "editor.field.currency_id",
                LIST_PATH_TIERS, index, "currency", "currency"));
        curated.add(EditorFieldSpec.text("tier_item_source", "editor.field.item_source",
                LIST_PATH_TIERS, index, "item", "source"));
        curated.add(EditorFieldSpec.number("tier_item_amount", "editor.field.item_amount",
                LIST_PATH_TIERS, index, "item", "amount"));
        List<EditorFieldSpec> combined = new ArrayList<>(curated);
        combined.addAll(EditorRenderer.structuralSpecs(session.draft(),
                new String[] { LIST_PATH_TIERS, index }, curated));
        return combined;
    }

    private static List<EditorFieldSpec> fallbackFields(EditorSession session) {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("fallback_currency_type", "editor.field.currency_type",
                CURRENCY_TYPES, "fallback", "currency", "type"));
        specs.add(amountSpec(session, "fallback_currency_amount", "editor.field.currency_amount",
                "fallback", "currency", "amount"));
        specs.add(EditorFieldSpec.text("fallback_currency_id", "editor.field.currency_id",
                "fallback", "currency", "currency"));
        specs.add(EditorFieldSpec.text("fallback_item_source", "editor.field.item_source",
                "fallback", "item", "source"));
        specs.add(EditorFieldSpec.number("fallback_item_amount", "editor.field.item_amount",
                "fallback", "item", "amount"));
        specs.add(EditorFieldSpec.number("fallback_max_amount", "editor.field.max_amount",
                "fallback", "max_amount"));
        return specs;
    }

    private static List<EditorFieldSpec> batchFields() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("batch_enabled", "editor.field.batch_enabled",
                "batch", "enabled"));
        specs.add(EditorFieldSpec.list(FIELD_OPTIONS_LIST, "editor.field.options",
                MENU_OPTION_EDIT, LIST_PATH_OPTIONS));
        return specs;
    }

    private static EditorFieldSpec optionField(EditorSession session) {
        return EditorFieldSpec.number("option_value", "editor.field.option_value",
                "batch", "options", Integer.toString(entryIndex(session)));
    }

    private static EditorFieldSpec amountSpec(EditorSession session, String id, String labelKey,
            String... path) {
        Object current = session.draft().valueLenient(path);
        if (current == null || current instanceof Number) {
            return EditorFieldSpec.number(id, labelKey, path);
        }
        return EditorFieldSpec.text(id, labelKey, path);
    }

    private static int entryIndex(EditorSession session) {
        try {
            return Math.max(0, Integer.parseInt(session.context(CONTEXT_ENTRY_INDEX)));
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    private void promptTierCreate(EditorSession session, EditorField field) {
        String label = plugin.messageService().message("editor.field.tiers");
        service.input().promptText(
                session.player(),
                label,
                plugin.messageService().message("editor.tier.create_hint"),
                label,
                "",
                plugin.messageService().message("editor.prompt.chat", Map.of("label", label)),
                text -> {
                    String range = normalizeRange(text);
                    if (range == null) {
                        plugin.messageService().send(session.player(), "editor.tier.bad_range");
                        EditorFeedback.play(session.player(), false);
                        return;
                    }
                    int index = session.draft().sequenceSize(LIST_PATH_TIERS);
                    Map<String, Object> currency = new LinkedHashMap<>();
                    currency.put("type", "vault");
                    currency.put("amount", 0);
                    Map<String, Object> tier = new LinkedHashMap<>();
                    tier.put("count_range", range);
                    tier.put("currency", currency);
                    EditorDocument.SaveResult result = service.mutate(session, field, label, false,
                            candidate -> candidate.appendListItem(tier, LIST_PATH_TIERS));
                    if (!result.saved()) {
                        return;
                    }
                    session.putContext(CONTEXT_ENTRY_INDEX, Integer.toString(index));
                    service.openMenu(session, MENU_TIERS);
                });
    }

    private void promptOptionAppend(EditorSession session, EditorField field) {
        String label = plugin.messageService().message("editor.field.options");
        service.input().promptText(
                session.player(),
                label,
                plugin.messageService().message("editor.option.create_hint"),
                label,
                "",
                plugin.messageService().message("editor.prompt.chat", Map.of("label", label)),
                text -> {
                    int option = parsePositiveInt(text);
                    if (option <= 0) {
                        plugin.messageService().send(session.player(), "editor.option.bad_value");
                        EditorFeedback.play(session.player(), false);
                        return;
                    }
                    service.mutate(session, field, label, false,
                            candidate -> candidate.appendListItem(option, "batch", "options"));
                    service.refresh(session);
                });
    }

    private static String normalizeRange(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        int dash = normalized.indexOf('-');
        try {
            if (dash < 0) {
                return Integer.parseInt(normalized) > 0 ? normalized : null;
            }
            int min = Integer.parseInt(normalized.substring(0, dash).trim());
            int max = Integer.parseInt(normalized.substring(dash + 1).trim());
            return min > 0 && max >= min ? normalized : null;
        } catch (NumberFormatException failure) {
            return null;
        }
    }

    private static int parsePositiveInt(String text) {
        try {
            int value = Integer.parseInt(text == null ? "" : text.trim());
            return value > 0 ? value : 0;
        } catch (NumberFormatException failure) {
            return 0;
        }
    }

    private String costFileName() {
        AppConfig config = plugin.appConfig();
        if (config == null) {
            return DEFAULT_COST_FILE;
        }
        String fileName = config.unlock().costFile();
        return fileName == null || fileName.isBlank() ? DEFAULT_COST_FILE : fileName;
    }

    private final class CostFileRoot implements EditorBrowserRoot {

        @Override
        public String id() {
            return ROOT_ID;
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
            return "editor.root.unlock_costs";
        }

        @Override
        public BiFunction<File, String, String> fileId() {
            return (file, directory) -> ROOT_ID;
        }
    }
}
