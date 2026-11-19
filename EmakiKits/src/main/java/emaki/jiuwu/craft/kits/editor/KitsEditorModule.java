package emaki.jiuwu.craft.kits.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
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
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.EditorValidator;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.kits.EmakiKitsPlugin;
import emaki.jiuwu.craft.kits.config.KitIds;

public final class KitsEditorModule implements EditorModule {

    public static final String ROOT_ID = "kits";
    public static final String DIRECTORY = "kits";

    private static final String MENU_HOME = "home";
    private static final String MENU_JOIN = "join";
    private static final String MENU_INVITE = "invite";
    private static final String MENU_CYCLE = "cycle";
    private static final String MENU_CDK = "cdk";
    private static final String MENU_CHOICE = "choice";
    private static final String MENU_OPTION_EDIT = "option_edit";
    private static final String MENU_PURCHASE = "purchase";
    private static final String MENU_OPEN = "open";
    private static final String MENU_MILESTONE_EDIT = "milestone_edit";
    private static final String MENU_LIMIT = "limit";
    private static final String MENU_REWARDS = "rewards";
    private static final String MENU_REWARD_ENTRY = "reward_entry";
    private static final String MENU_LIST_ENTRIES = EditorService.MENU_LIST_ENTRIES;

    private static final String LP_OPTIONS = "choice.options";
    private static final String LP_MILESTONES = "open.milestones";
    private static final String LP_FIXED = "rewards.fixed";
    private static final String LP_RANDOM = "rewards.random";

    private static final String CK_OPTION_KEY = "kits_option_key";
    private static final String CK_MILESTONE_KEY = "kits_milestone_key";
    private static final String CK_REWARD_LIST = "kits_reward_list";
    private static final String CK_REWARD_INDEX = "kits_reward_index";

    private static final String TYPE_PREVIEW = "preview";
    private static final String STRUCT_ID_FIELD = "struct:id";

    private static final List<String> TYPES = List.of(
            "join", "invite", "daily", "weekly", "monthly", "cdk", "choice", "generic");
    private static final List<String> REWARD_KINDS = List.of("item", "money", "nested_kit", "action");
    private static final List<String> LIMIT_MODES = List.of("always", "range", "cron");
    private static final List<String> CDK_MODES = List.of("global_once", "per_player_once");
    private static final List<String> REWARD_TARGETS = List.of("INVITER", "INVITEE");
    private static final List<String> CODE_TYPES = List.of("uuid", "name");
    private static final List<String> ECONOMY_PROVIDERS = List.of("auto", "vault", "excellenteconomy");

    private static final Map<String, EditorMenuMeta> MENUS = buildMenus();

    private final EmakiKitsPlugin plugin;
    private final KitYamlValidator validator;
    private EditorService service;

    public KitsEditorModule(EmakiKitsPlugin plugin) {
        this.plugin = plugin;
        this.validator = new KitYamlValidator(plugin);
    }

    public void bind(EditorService service) {
        this.service = service;
    }

    public EditorService service() {
        return service;
    }

    public EditorBrowserRoot kitsRoot() {
        return new KitsRoot();
    }

    @Override
    public String rootCommand() {
        return "emakikits";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        return List.of(new KitsRoot());
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return menuId == null ? null : MENUS.get(menuId);
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
        return validator;
    }

    @Override
    public void reload() {
        plugin.reloadContent();
    }

    @Override
    public Map<String, Object> skeleton(EditorBrowserRoot root, String id) {
        return KitSkeletons.skeleton(id);
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        if (menuId == null) {
            return List.of();
        }
        return switch (menuId) {
            case MENU_HOME -> homeFields();
            case MENU_JOIN -> joinFields();
            case MENU_INVITE -> inviteFields();
            case MENU_CYCLE -> cycleFields();
            case MENU_CDK -> cdkFields();
            case MENU_CHOICE -> choiceFields();
            case MENU_OPTION_EDIT -> optionEditFields(session);
            case MENU_PURCHASE -> purchaseFields();
            case MENU_OPEN -> openFields();
            case MENU_MILESTONE_EDIT -> milestoneEditFields(session);
            case MENU_LIMIT -> limitFields();
            case MENU_REWARDS -> rewardsFields();
            case MENU_REWARD_ENTRY -> rewardEntryFields(session);
            default -> List.of();
        };
    }

    private static List<EditorFieldSpec> homeFields() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("enabled", "editor.field.enabled", "enabled"));
        specs.add(EditorFieldSpec.cycle("kit_type", "editor.field.type", TYPES, "type"));
        specs.add(EditorFieldSpec.text("permission", "editor.field.permission", "permission"));
        specs.add(EditorFieldSpec.text("display_name", "editor.field.display_name", "display_name"));
        specs.add(EditorFieldSpec.number("max_claims_total", "editor.field.max_claims_total", "max_claims_total"));
        specs.add(nav("nav_join", "editor.field.nav_join", "join", MENU_JOIN));
        specs.add(nav("nav_invite", "editor.field.nav_invite", "invite", MENU_INVITE));
        specs.add(nav("nav_cycle", "editor.field.nav_cycle", "cycle", MENU_CYCLE));
        specs.add(nav("nav_cdk", "editor.field.nav_cdk", "cdk", MENU_CDK));
        specs.add(nav("nav_choice", "editor.field.nav_choice", "choice", MENU_CHOICE));
        specs.add(nav("nav_purchase", "editor.field.nav_purchase", "purchase", MENU_PURCHASE));
        specs.add(nav("nav_open", "editor.field.nav_open", "open", MENU_OPEN));
        specs.add(nav("nav_limit", "editor.field.nav_limit", "limit", MENU_LIMIT));
        specs.add(nav("nav_rewards", "editor.field.nav_rewards", "rewards", MENU_REWARDS));
        return specs;
    }

    private static List<EditorFieldSpec> joinFields() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("join_first", "editor.field.join_first",
                "join", "on_first_join"));
        specs.add(EditorFieldSpec.toggle("join_every", "editor.field.join_every",
                "join", "on_every_specified_join"));
        specs.add(EditorFieldSpec.toggle("join_require_code", "editor.field.join_require_code",
                "join", "require_invite_code"));
        specs.add(EditorFieldSpec.cycle("join_code_type", "editor.field.join_code_type", CODE_TYPES,
                "join", "invite_code_type"));
        specs.add(EditorFieldSpec.number("join_window", "editor.field.join_window",
                "join", "missed_window_seconds"));
        specs.add(EditorFieldSpec.list("join_worlds", "editor.field.worlds",
                MENU_LIST_ENTRIES, "join.worlds"));
        return specs;
    }

    private static List<EditorFieldSpec> inviteFields() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("invite_target", "editor.field.invite_target", REWARD_TARGETS,
                "invite", "reward_target"));
        specs.add(EditorFieldSpec.toggle("invite_require_new", "editor.field.invite_require_new",
                "invite", "require_new_player"));
        specs.add(EditorFieldSpec.toggle("invite_mutual", "editor.field.invite_mutual",
                "invite", "allow_mutual"));
        specs.add(EditorFieldSpec.number("invite_max_binds", "editor.field.invite_max_binds",
                "invite", "max_binds_per_invitee"));
        return specs;
    }

    private static List<EditorFieldSpec> cycleFields() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("cycle_tz", "editor.field.cycle_tz", "cycle", "timezone"));
        specs.add(EditorFieldSpec.toggle("cycle_makeup", "editor.field.cycle_makeup",
                "cycle", "allow_makeup"));
        return specs;
    }

    private static List<EditorFieldSpec> cdkFields() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("cdk_mode", "editor.field.cdk_mode", CDK_MODES, "cdk", "mode"));
        specs.add(EditorFieldSpec.list("cdk_codes", "editor.field.codes",
                MENU_LIST_ENTRIES, "cdk.codes"));
        specs.add(EditorFieldSpec.number("cdk_limit", "editor.field.cdk_limit",
                "cdk", "per_player_limit"));
        return specs;
    }

    private static List<EditorFieldSpec> choiceFields() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.number("choice_min", "editor.field.choice_min",
                "choice", "min_select"));
        specs.add(EditorFieldSpec.number("choice_max", "editor.field.choice_max",
                "choice", "max_select"));
        specs.add(EditorFieldSpec.text("choice_gui_template", "editor.field.choice_gui_template",
                "choice", "gui_template"));
        specs.add(EditorFieldSpec.list("choice_options", "editor.field.options",
                MENU_OPTION_EDIT, LP_OPTIONS));
        return specs;
    }

    private static List<EditorFieldSpec> optionEditFields(EditorSession session) {
        String key = session.context(CK_OPTION_KEY);
        if (key == null || key.isBlank()) {
            return List.of();
        }
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.text("option_display", "editor.field.option_display",
                "choice", "options", key, "display"));
        specs.add(EditorFieldSpec.text("option_permission", "editor.field.option_permission",
                "choice", "options", key, "permission"));
        specs.add(EditorFieldSpec.text("option_kit_id", "editor.field.option_kit_id",
                "choice", "options", key, "kit_id"));
        specs.add(EditorFieldSpec.list("option_rewards", "editor.field.option_rewards",
                MENU_REWARD_ENTRY, LP_OPTIONS + "." + key + ".rewards"));
        return specs;
    }

    private static List<EditorFieldSpec> purchaseFields() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("purchase_enabled", "editor.field.purchase_enabled",
                "purchase", "enabled"));
        specs.add(EditorFieldSpec.number("purchase_price", "editor.field.purchase_price",
                "purchase", "price"));
        specs.add(EditorFieldSpec.cycle("purchase_provider", "editor.field.purchase_provider",
                ECONOMY_PROVIDERS, "purchase", "economy_provider"));
        specs.add(EditorFieldSpec.text("purchase_currency", "editor.field.purchase_currency",
                "purchase", "currency_id"));
        specs.add(EditorFieldSpec.number("purchase_max", "editor.field.purchase_max",
                "purchase", "max_purchases"));
        specs.add(EditorFieldSpec.toggle("purchase_pass", "editor.field.purchase_pass",
                "purchase", "count_pass_to_nested"));
        return specs;
    }

    private static List<EditorFieldSpec> openFields() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("open_enabled", "editor.field.open_enabled",
                "open", "enabled"));
        specs.add(EditorFieldSpec.number("open_max", "editor.field.open_max",
                "open", "max_opens"));
        specs.add(EditorFieldSpec.list("open_milestones", "editor.field.milestones",
                MENU_MILESTONE_EDIT, LP_MILESTONES));
        return specs;
    }

    private static List<EditorFieldSpec> milestoneEditFields(EditorSession session) {
        String key = session.context(CK_MILESTONE_KEY);
        if (key == null || key.isBlank()) {
            return List.of();
        }
        return List.of(EditorFieldSpec.list("milestone_rewards", "editor.field.milestone_rewards",
                MENU_REWARD_ENTRY, LP_MILESTONES + "." + key + ".rewards"));
    }

    private static List<EditorFieldSpec> limitFields() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("limit_enabled", "editor.field.limit_enabled",
                "limit", "enabled"));
        specs.add(EditorFieldSpec.cycle("limit_mode", "editor.field.limit_mode", LIMIT_MODES,
                "limit", "mode"));
        specs.add(EditorFieldSpec.text("limit_start", "editor.field.limit_start", "limit", "start"));
        specs.add(EditorFieldSpec.text("limit_end", "editor.field.limit_end", "limit", "end"));
        specs.add(EditorFieldSpec.text("limit_cron", "editor.field.limit_cron", "limit", "cron"));
        specs.add(EditorFieldSpec.number("limit_duration", "editor.field.limit_duration",
                "limit", "duration_seconds"));
        specs.add(EditorFieldSpec.text("limit_tz", "editor.field.limit_tz", "limit", "timezone"));
        return specs;
    }

    private static List<EditorFieldSpec> rewardsFields() {
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.toggle("rewards_weighted", "editor.field.rewards_weighted",
                "rewards", "weighted"));
        specs.add(EditorFieldSpec.list("rewards_fixed", "editor.field.rewards_fixed",
                MENU_REWARD_ENTRY, LP_FIXED));
        specs.add(EditorFieldSpec.list("rewards_random", "editor.field.rewards_random",
                MENU_REWARD_ENTRY, LP_RANDOM));
        specs.add(EditorFieldSpec.number("pity_count", "editor.field.pity_count",
                "rewards", "pity", "count"));
        specs.add(EditorFieldSpec.text("pity_guaranteed", "editor.field.pity_guaranteed",
                "rewards", "pity", "guaranteed"));
        return specs;
    }

    private static List<EditorFieldSpec> rewardEntryFields(EditorSession session) {
        String[] base = rewardBase(session);
        if (base == null) {
            return List.of();
        }
        List<EditorFieldSpec> specs = new ArrayList<>();
        specs.add(EditorFieldSpec.cycle("reward_type", "editor.field.reward_type", REWARD_KINDS,
                with(base, "type")));
        specs.add(EditorFieldSpec.text("reward_id", "editor.field.reward_id", with(base, "id")));
        specs.add(EditorFieldSpec.number("reward_amount", "editor.field.reward_amount",
                with(base, "amount")));
        specs.add(EditorFieldSpec.text("reward_source", "editor.field.reward_source",
                with(base, "source")));
        specs.add(EditorFieldSpec.text("reward_action", "editor.field.reward_action",
                with(base, "action")));
        specs.add(EditorFieldSpec.text("reward_kit_id", "editor.field.reward_kit_id",
                with(base, "kit_id")));
        specs.add(EditorFieldSpec.number("reward_weight", "editor.field.reward_weight",
                with(base, "weight")));
        specs.add(EditorFieldSpec.text("reward_permission", "editor.field.reward_permission",
                with(base, "permission")));
        return specs;
    }

    private static EditorFieldSpec nav(String id, String labelKey, String section, String target) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey,
                new String[] { section }, List.of(), target, null, null);
    }

    private static String[] rewardBase(EditorSession session) {
        String listPath = session.context(CK_REWARD_LIST);
        String index = session.context(CK_REWARD_INDEX);
        if (listPath == null || index == null) {
            return null;
        }
        return with(listPath.split("\\."), index);
    }

    private static String[] with(String[] base, String key) {
        String[] result = new String[base.length + 1];
        System.arraycopy(base, 0, result, 0, base.length);
        result[base.length] = key;
        return result;
    }

    private static Map<String, EditorMenuMeta> buildMenus() {
        Map<String, EditorMenuMeta> menus = new LinkedHashMap<>();
        menus.put(MENU_HOME, new EditorMenuMeta(MENU_HOME, "editor.menu.home", MENU_HOME,
                new String[0], false));
        menus.put(MENU_JOIN, new EditorMenuMeta(MENU_JOIN, "editor.menu.join", MENU_HOME,
                new String[] { "join" }, false));
        menus.put(MENU_INVITE, new EditorMenuMeta(MENU_INVITE, "editor.menu.invite", MENU_HOME,
                new String[] { "invite" }, false));
        menus.put(MENU_CYCLE, new EditorMenuMeta(MENU_CYCLE, "editor.menu.cycle", MENU_HOME,
                new String[] { "cycle" }, false));
        menus.put(MENU_CDK, new EditorMenuMeta(MENU_CDK, "editor.menu.cdk", MENU_HOME,
                new String[] { "cdk" }, false));
        menus.put(MENU_CHOICE, new EditorMenuMeta(MENU_CHOICE, "editor.menu.choice", MENU_HOME,
                new String[] { "choice" }, false));
        menus.put(MENU_OPTION_EDIT, new EditorMenuMeta(MENU_OPTION_EDIT, "editor.menu.option_edit",
                MENU_CHOICE, null, false));
        menus.put(MENU_PURCHASE, new EditorMenuMeta(MENU_PURCHASE, "editor.menu.purchase", MENU_HOME,
                new String[] { "purchase" }, false));
        menus.put(MENU_OPEN, new EditorMenuMeta(MENU_OPEN, "editor.menu.open", MENU_HOME,
                new String[] { "open" }, false));
        menus.put(MENU_MILESTONE_EDIT, new EditorMenuMeta(MENU_MILESTONE_EDIT,
                "editor.menu.milestone_edit", MENU_OPEN, null, false));
        menus.put(MENU_LIMIT, new EditorMenuMeta(MENU_LIMIT, "editor.menu.limit", MENU_HOME,
                new String[] { "limit" }, false));
        menus.put(MENU_REWARDS, new EditorMenuMeta(MENU_REWARDS, "editor.menu.rewards", MENU_HOME,
                new String[] { "rewards" }, false));
        menus.put(MENU_REWARD_ENTRY, new EditorMenuMeta(MENU_REWARD_ENTRY, "editor.menu.reward_entry",
                MENU_REWARDS, null, false));
        menus.put(MENU_LIST_ENTRIES, new EditorMenuMeta(MENU_LIST_ENTRIES, "editor.menu.list_entries",
                MENU_HOME, null, true));
        return menus;
    }

    @Override
    public Material fieldIcon(EditorField field) {
        return switch (field.id()) {
            case "kit_type", "display_name" -> Material.NAME_TAG;
            case "permission" -> Material.TRIPWIRE_HOOK;
            case "max_claims_total" -> Material.GOLD_INGOT;
            case "nav_join" -> Material.OAK_DOOR;
            case "nav_invite" -> Material.PLAYER_HEAD;
            case "nav_cycle" -> Material.CLOCK;
            case "nav_cdk", "cdk_codes", "codes" -> Material.PAPER;
            case "nav_choice" -> Material.COMPASS;
            case "nav_purchase", "purchase_price" -> Material.EMERALD;
            case "nav_open" -> Material.CHEST;
            case "nav_limit", "limit_mode" -> Material.REPEATER;
            case "nav_rewards" -> Material.GOLDEN_APPLE;
            case "cdk_limit" -> Material.CLOCK;
            case "rewards_fixed", "rewards_random", "option_rewards", "milestone_rewards",
                    "choice_options", "options", "milestones", "open_milestones" -> Material.CHEST;
            case "pity_count" -> Material.HEART_OF_THE_SEA;
            case "pity_guaranteed" -> Material.NETHER_STAR;
            case "reward_type", "reward_amount" -> Material.HOPPER;
            case "reward_source" -> Material.DIAMOND;
            case "reward_weight" -> Material.GOLD_NUGGET;
            default -> null;
        };
    }

    @Override
    public ItemStack renderCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot) {
        if (!TYPE_PREVIEW.equals(Texts.lower(slot.definition().type()))) {
            return null;
        }
        return renderPreview(session);
    }

    private ItemStack renderPreview(EditorSession session) {
        Map<String, Object> draftRoot = session.draft().root();
        String id = Texts.toStringSafe(draftRoot.get("id"));
        String type = Texts.toStringSafe(draftRoot.get("type"));
        String display = Texts.toStringSafe(draftRoot.get("display_name"));
        if (display.isBlank()) {
            display = id;
        }
        ConfiguredItemDefinition definition = new ConfiguredItemDefinition("minecraft-chest", 1, Map.of(
                "minecraft:custom_name", ItemComponentPatch.set("<white>" + display + "</white>"),
                "minecraft:lore", ItemComponentPatch.set(List.of(
                        plugin.messageService().message("editor.kits.preview_id", Map.of("value", id)),
                        plugin.messageService().message("editor.kits.preview_type", Map.of("value", type))))));
        return GuiItemBuilder.build(definition, Map.of(),
                EmakiCoreLibApi.requireService(EmakiCoreLibPlugin.class).configuredItemService());
    }

    @Override
    public boolean handleCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot,
            GuiClickContext click) {
        String type = Texts.lower(slot.definition().type());
        if (!EditorService.TYPE_CONFIRM.equals(type) || !MENU_LIST_ENTRIES.equals(menuId)) {
            return false;
        }
        String listPath = session.context(EditorService.CONTEXT_LIST_PATH);
        if (listPath == null) {
            return false;
        }
        if (LP_OPTIONS.equals(listPath)) {
            promptOptionCreate(session);
            return true;
        }
        if (LP_MILESTONES.equals(listPath)) {
            promptMilestoneCreate(session);
            return true;
        }
        if (isRewardEntryList(listPath)) {
            appendDefaultReward(session, listPath, null,
                    plugin.messageService().message("editor.field.entry"));
            return true;
        }
        return false;
    }

    @Override
    public boolean handleFieldOverride(EditorSession session, EditorField field, GuiClickContext click) {
        if (STRUCT_ID_FIELD.equals(field.id())) {
            plugin.messageService().send(session.player(), "editor.kits.readonly");
            EditorFeedback.play(session.player(), false);
            return true;
        }
        if (MENU_LIST_ENTRIES.equals(session.currentMenuId()) && field.id().startsWith("entry_")) {
            return handleEntryClick(session, field, click);
        }
        if (field.kind() == EditorField.Kind.LIST) {
            return handleListCreate(session, field, click);
        }
        return false;
    }

    private boolean handleEntryClick(EditorSession session, EditorField field, GuiClickContext click) {
        if (click == null || click.isShiftClick()) {
            return false;
        }
        String listPath = session.context(EditorService.CONTEXT_LIST_PATH);
        if (listPath == null) {
            return false;
        }
        Object target = session.draft().valueLenient(listPath.split("\\."));
        if (target instanceof Map<?, ?>) {
            String key = field.options().isEmpty() ? null : field.options().get(0);
            if (key == null) {
                return false;
            }
            if (LP_OPTIONS.equals(listPath)) {
                session.putContext(CK_OPTION_KEY, key);
                service.openMenu(session, MENU_OPTION_EDIT);
                return true;
            }
            if (LP_MILESTONES.equals(listPath)) {
                session.putContext(CK_MILESTONE_KEY, key);
                service.openMenu(session, MENU_MILESTONE_EDIT);
                return true;
            }
            return false;
        }
        if (isRewardEntryList(listPath)) {
            session.putContext(CK_REWARD_LIST, listPath);
            session.putContext(CK_REWARD_INDEX, Integer.toString(field.slotIndex()));
            service.openMenu(session, MENU_REWARD_ENTRY);
            return true;
        }
        return false;
    }

    private boolean handleListCreate(EditorSession session, EditorField field, GuiClickContext click) {
        if (click == null || click.isRightClick() || click.isShiftClick()) {
            return false;
        }
        EditorFieldSpec spec = service.renderer().spec(session, session.current(), field.id());
        String listPath = spec == null ? null : spec.listPath();
        if (!isRewardEntryList(listPath)) {
            return false;
        }
        appendDefaultReward(session, listPath, field, plugin.messageService().message(spec.labelKey()));
        return true;
    }

    private void appendDefaultReward(EditorSession session, String listPath, EditorField field, String label) {
        String[] path = listPath.split("\\.");
        int index = session.draft().sequenceSize(path);
        EditorDocument.SaveResult result = service.mutate(session, field, label, false,
                candidate -> candidate.appendListItem(KitSkeletons.defaultRewardEntry(index),
                        candidate.resolvePath(path)));
        if (!result.saved()) {
            return;
        }
        session.putContext(CK_REWARD_LIST, listPath);
        session.putContext(CK_REWARD_INDEX, Integer.toString(index));
        service.openMenu(session, MENU_REWARD_ENTRY);
    }

    private void promptOptionCreate(EditorSession session) {
        String label = plugin.messageService().message("editor.field.options");
        service.input().promptChat(session.player(),
                plugin.messageService().message("editor.kits.option_create_hint"),
                plugin.messageService().message("editor.prompt.chat", Map.of("label", label)),
                text -> {
                    String key = KitIds.normalize(text);
                    if (!KitIds.valid(key)) {
                        plugin.messageService().send(session.player(), "editor.kits.option_bad_id");
                        EditorFeedback.play(session.player(), false);
                        return;
                    }
                    if (session.draft().value("choice", "options", key) != null) {
                        plugin.messageService().send(session.player(), "editor.kits.option_duplicate",
                                Map.of("id", key));
                        EditorFeedback.play(session.player(), false);
                        return;
                    }
                    EditorDocument.SaveResult result = service.mutate(session, null, label, false,
                            candidate -> candidate.set(KitSkeletons.defaultOptionEntry(key),
                                    "choice", "options", key));
                    if (!result.saved()) {
                        return;
                    }
                    session.putContext(CK_OPTION_KEY, key);
                    service.openMenu(session, MENU_OPTION_EDIT);
                });
    }

    private void promptMilestoneCreate(EditorSession session) {
        String label = plugin.messageService().message("editor.field.milestones");
        service.input().promptChat(session.player(),
                plugin.messageService().message("editor.kits.milestone_create_hint"),
                plugin.messageService().message("editor.prompt.chat", Map.of("label", label)),
                text -> {
                    String key = text == null ? "" : text.trim();
                    if (parsePositiveInt(key) <= 0) {
                        plugin.messageService().send(session.player(), "editor.kits.milestone_bad_count");
                        EditorFeedback.play(session.player(), false);
                        return;
                    }
                    if (session.draft().value("open", "milestones", key) != null) {
                        plugin.messageService().send(session.player(), "editor.kits.milestone_duplicate",
                                Map.of("id", key));
                        EditorFeedback.play(session.player(), false);
                        return;
                    }
                    EditorDocument.SaveResult result = service.mutate(session, null, label, false,
                            candidate -> candidate.set(KitSkeletons.defaultMilestoneEntry(),
                                    "open", "milestones", key));
                    if (!result.saved()) {
                        return;
                    }
                    session.putContext(CK_MILESTONE_KEY, key);
                    service.openMenu(session, MENU_MILESTONE_EDIT);
                });
    }

    @Override
    public String listHint(EditorSession session, String listPath, String current) {
        if ("cdk.codes".equals(listPath)) {
            return plugin.messageService().message("editor.kits.hint_code");
        }
        if ("join.worlds".equals(listPath)) {
            return plugin.messageService().message("editor.kits.hint_world");
        }
        return null;
    }

    private static boolean isRewardEntryList(String listPath) {
        if (listPath == null) {
            return false;
        }
        return LP_FIXED.equals(listPath) || LP_RANDOM.equals(listPath)
                || (listPath.startsWith(LP_OPTIONS + ".") && listPath.endsWith(".rewards"))
                || (listPath.startsWith(LP_MILESTONES + ".") && listPath.endsWith(".rewards"));
    }

    private static int parsePositiveInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException failure) {
            return 0;
        }
    }

    private static final class KitsRoot implements EditorBrowserRoot {

        @Override
        public String id() {
            return ROOT_ID;
        }

        @Override
        public String directory() {
            return DIRECTORY;
        }

        @Override
        public String labelKey() {
            return "editor.root.kits";
        }

        @Override
        public Material icon() {
            return Material.CHEST;
        }

        @Override
        public BiFunction<File, String, String> fileId() {
            return (file, directory) -> {
                String name = file.getName();
                int dot = name.lastIndexOf('.');
                return dot > 0 ? name.substring(0, dot) : name;
            };
        }
    }
}
