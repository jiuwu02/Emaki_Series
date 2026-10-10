package emaki.jiuwu.craft.level.editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorRenderer;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;

final class LevelEditorMenus {

    enum DocKind {
        TYPE,
        SOURCE,
        REQUIREMENTS
    }

    static final String HOME = "home";
    static final String TYPE_REQUIREMENT = "type_requirement";
    static final String TYPE_UPGRADE = "type_upgrade";
    static final String TYPE_COST = "type_upgrade_cost";
    static final String TYPE_REWARDS = "type_upgrade_rewards";
    static final String COST_CURRENCY = "cost_currency";
    static final String COST_MATERIAL = "cost_material";
    static final String REWARD_ITEM = "reward_item";
    static final String TYPE_ATTRIBUTES = "type_attributes";
    static final String SOURCE_RULE_ENTRY = "source_rule_entry";
    static final String LIST_ENTRIES = EditorService.MENU_LIST_ENTRIES;
    static final String SOURCE_RULE_PREFIX = "src_rule:";
    static final String REQUIREMENT_GROUP_PREFIX = "req_group:";
    static final String CONTEXT_RULE_NAME = "source_rule_name";

    private static final List<String> TRIGGER_OPTIONS = List.of(
            "entity_kill", "mythic_mob_kill", "block_break", "crop_harvest",
            "craft_item", "furnace_extract", "player_fish", "entity_tame", "brew_complete");

    private record MenuInfo(String titleKey, String parent, boolean paginated) {
    }

    private static final Map<String, MenuInfo> MENUS = buildMenus();

    private LevelEditorMenus() {
    }

    private static Map<String, MenuInfo> buildMenus() {
        Map<String, MenuInfo> menus = new LinkedHashMap<>();
        menus.put(HOME, new MenuInfo("editor.menu.home", HOME, false));
        menus.put(TYPE_REQUIREMENT, new MenuInfo("editor.menu.type_requirement", HOME, false));
        menus.put(TYPE_UPGRADE, new MenuInfo("editor.menu.type_upgrade", HOME, false));
        menus.put(TYPE_COST, new MenuInfo("editor.menu.type_upgrade_cost", TYPE_UPGRADE, false));
        menus.put(TYPE_REWARDS, new MenuInfo("editor.menu.type_upgrade_rewards", TYPE_UPGRADE, false));
        menus.put(COST_CURRENCY, new MenuInfo("editor.menu.cost_currency", TYPE_COST, true));
        menus.put(COST_MATERIAL, new MenuInfo("editor.menu.cost_material", TYPE_COST, true));
        menus.put(REWARD_ITEM, new MenuInfo("editor.menu.reward_item", TYPE_REWARDS, true));
        menus.put(TYPE_ATTRIBUTES, new MenuInfo("editor.menu.type_attributes", HOME, false));
        menus.put(SOURCE_RULE_ENTRY, new MenuInfo("editor.menu.source_rule_entry", HOME, true));
        menus.put(LIST_ENTRIES, new MenuInfo("editor.menu.list_entries", HOME, true));
        return menus;
    }

    static String titleKey(String menuId) {
        if (menuId == null) {
            return "editor.menu.home";
        }
        if (menuId.startsWith(SOURCE_RULE_PREFIX)) {
            return "editor.menu.source_rule";
        }
        if (menuId.startsWith(REQUIREMENT_GROUP_PREFIX)) {
            return "editor.menu.requirement_group";
        }
        MenuInfo info = MENUS.get(menuId);
        return info == null ? "editor.menu." + menuId : info.titleKey();
    }

    static EditorMenuMeta meta(String menuId) {
        if (menuId == null) {
            return null;
        }
        if (menuId.startsWith(SOURCE_RULE_PREFIX) || menuId.startsWith(REQUIREMENT_GROUP_PREFIX)) {
            return new EditorMenuMeta(menuId, titleKey(menuId), HOME, null, false);
        }
        MenuInfo info = MENUS.get(menuId);
        if (info == null) {
            return null;
        }
        return new EditorMenuMeta(menuId, info.titleKey(), info.parent(), null, info.paginated());
    }

    static List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        if (menuId == null) {
            return List.of();
        }
        if (menuId.startsWith(SOURCE_RULE_PREFIX)) {
            return sourceRule(session, menuId.substring(SOURCE_RULE_PREFIX.length()));
        }
        if (menuId.startsWith(REQUIREMENT_GROUP_PREFIX)) {
            return requirementGroup(session, menuId.substring(REQUIREMENT_GROUP_PREFIX.length()));
        }
        return switch (menuId) {
            case HOME -> home(session);
            case TYPE_REQUIREMENT -> withStructural(session, "requirement", typeRequirement());
            case TYPE_UPGRADE -> withStructural(session, "upgrade", typeUpgrade());
            case TYPE_COST -> withStructural(session, "upgrade.cost", typeCost());
            case TYPE_REWARDS -> withStructural(session, "upgrade.rewards", typeRewards());
            case TYPE_ATTRIBUTES -> withStructural(session, "attributes", typeAttributes());
            case COST_CURRENCY -> withStructural(session,
                    "upgrade.cost.economy.currencies." + entryIndex(session), currencyEntry(session));
            case COST_MATERIAL -> withStructural(session,
                    "upgrade.cost.materials." + entryIndex(session), materialEntry(session));
            case REWARD_ITEM -> withStructural(session,
                    "upgrade.rewards.items." + entryIndex(session), rewardEntry(session));
            case SOURCE_RULE_ENTRY -> sourceRuleEntry(session);
            default -> List.of();
        };
    }

    static DocKind docKind(EditorSession session) {
        Path path = session.document().path();
        Path parent = path.getParent();
        String directory = parent == null || parent.getFileName() == null ? "" : parent.getFileName().toString();
        if ("sources".equals(directory)) {
            return DocKind.SOURCE;
        }
        if ("types".equals(directory)) {
            return DocKind.TYPE;
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        if (name.startsWith("requirements")) {
            return DocKind.REQUIREMENTS;
        }
        return DocKind.TYPE;
    }

    private static List<EditorFieldSpec> home(EditorSession session) {
        DocKind kind = docKind(session);
        if (kind == DocKind.SOURCE) {
            List<EditorFieldSpec> curated = List.of(
                    EditorFieldSpec.toggle("enabled", "editor.field.enabled", "enabled"),
                    EditorFieldSpec.text("display_name", "editor.field.display_name", "display_name"),
                    EditorFieldSpec.list("sources", "editor.field.sources", LIST_ENTRIES, "sources"));
            return withStructural(session, "", curated);
        }
        if (kind == DocKind.REQUIREMENTS) {
            return List.of(
                    EditorFieldSpec.text("global_formula", "editor.field.global_formula", "global", "formula"),
                    EditorFieldSpec.list("global_values", "editor.field.global_values", LIST_ENTRIES,
                            "global.values"));
        }
        List<EditorFieldSpec> curated = List.of(
                EditorFieldSpec.toggle("enabled", "editor.field.enabled", "enabled"),
                EditorFieldSpec.text("display_name", "editor.field.display_name", "display_name"),
                EditorFieldSpec.text("primary", "editor.field.primary", "primary"),
                EditorFieldSpec.number("start_level", "editor.field.start_level", "start_level"),
                EditorFieldSpec.number("max_level", "editor.field.max_level", "max_level"),
                EditorFieldSpec.list("description", "editor.field.description", LIST_ENTRIES, "description"),
                EditorFieldSpec.toggle("pdc_enabled", "editor.field.pdc_enabled", "pdc", "enabled"),
                nav("requirement", "editor.field.requirement", TYPE_REQUIREMENT, "requirement"),
                nav("upgrade", "editor.field.upgrade", TYPE_UPGRADE, "upgrade"),
                nav("attributes", "editor.field.attributes", TYPE_ATTRIBUTES, "attributes"));
        return withStructural(session, "", curated);
    }

    private static List<EditorFieldSpec> typeRequirement() {
        return List.of(
                EditorFieldSpec.text("req_group", "editor.field.req_group", "requirement", "group"),
                EditorFieldSpec.text("req_formula", "editor.field.req_formula", "requirement", "formula"));
    }

    private static List<EditorFieldSpec> typeUpgrade() {
        return List.of(
                EditorFieldSpec.toggle("upgrade_enabled", "editor.field.upgrade_enabled", "upgrade", "enabled"),
                EditorFieldSpec.toggle("auto_upgrade", "editor.field.auto_upgrade", "upgrade", "auto_upgrade"),
                EditorFieldSpec.toggle("manual_upgrade", "editor.field.manual_upgrade",
                        "upgrade", "manual_upgrade"),
                nav("upgrade_cost", "editor.field.upgrade_cost", TYPE_COST, "upgrade", "cost"),
                nav("upgrade_rewards", "editor.field.upgrade_rewards", TYPE_REWARDS, "upgrade", "rewards"));
    }

    private static List<EditorFieldSpec> typeCost() {
        return List.of(
                EditorFieldSpec.toggle("cost_enabled", "editor.field.cost_enabled",
                        "upgrade", "cost", "enabled"),
                EditorFieldSpec.toggle("economy_enabled", "editor.field.economy_enabled",
                        "upgrade", "cost", "economy", "enabled"),
                EditorFieldSpec.list("currencies", "editor.field.currencies", COST_CURRENCY,
                        "upgrade.cost.economy.currencies"),
                EditorFieldSpec.list("materials", "editor.field.materials", COST_MATERIAL,
                        "upgrade.cost.materials"));
    }

    private static List<EditorFieldSpec> typeRewards() {
        return List.of(EditorFieldSpec.list("rewards_items", "editor.field.rewards_items",
                REWARD_ITEM, "upgrade.rewards.items"));
    }

    private static List<EditorFieldSpec> typeAttributes() {
        return List.of(
                EditorFieldSpec.toggle("attributes_enabled", "editor.field.attributes_enabled",
                        "attributes", "enabled"),
                EditorFieldSpec.list("attribute_values", "editor.field.attribute_values",
                        LIST_ENTRIES, "attributes.values"));
    }

    private static List<EditorFieldSpec> currencyEntry(EditorSession session) {
        String base = "upgrade.cost.economy.currencies." + entryIndex(session);
        return List.of(
                EditorFieldSpec.text("currency_provider", "editor.field.currency_provider",
                        extend(base, "provider")),
                EditorFieldSpec.text("currency_id", "editor.field.currency_id",
                        extend(base, "currency_id")),
                EditorFieldSpec.number("base_cost", "editor.field.base_cost",
                        extend(base, "base_cost")),
                EditorFieldSpec.text("cost_formula", "editor.field.cost_formula",
                        extend(base, "cost_formula")),
                EditorFieldSpec.text("currency_display_name", "editor.field.currency_display_name",
                        extend(base, "display_name")));
    }

    private static List<EditorFieldSpec> materialEntry(EditorSession session) {
        String base = "upgrade.cost.materials." + entryIndex(session);
        return List.of(
                EditorFieldSpec.list("material_item_sources", "editor.field.material_item_sources",
                        LIST_ENTRIES, base + ".item_sources"),
                EditorFieldSpec.number("material_base_amount", "editor.field.material_base_amount",
                        extend(base, "base_amount")),
                EditorFieldSpec.text("material_amount_formula", "editor.field.material_amount_formula",
                        extend(base, "amount_formula")));
    }

    private static List<EditorFieldSpec> rewardEntry(EditorSession session) {
        String base = "upgrade.rewards.items." + entryIndex(session);
        return List.of(
                EditorFieldSpec.text("reward_levels", "editor.field.reward_levels",
                        extend(base, "levels")),
                EditorFieldSpec.text("reward_item_source", "editor.field.reward_item_source",
                        extend(base, "item_source")),
                EditorFieldSpec.number("reward_amount", "editor.field.reward_amount",
                        extend(base, "amount")));
    }

    private static List<EditorFieldSpec> requirementGroup(EditorSession session, String name) {
        String base = "groups." + name;
        List<EditorFieldSpec> curated = List.of(
                EditorFieldSpec.text("group_formula", "editor.field.group_formula",
                        extend(base, "formula")),
                EditorFieldSpec.list("group_values", "editor.field.group_values",
                        LIST_ENTRIES, base + ".values"));
        return withStructural(session, base, curated);
    }

    private static List<EditorFieldSpec> sourceRule(EditorSession session, String name) {
        String base = "sources." + name;
        YamlTextDocument draft = session.draft();
        List<EditorFieldSpec> curated = new ArrayList<>();
        curated.add(EditorFieldSpec.toggle("rule_enabled", "editor.field.rule_enabled",
                extend(base, "enabled")));
        curated.add(EditorFieldSpec.cycle("rule_type", "editor.field.rule_type",
                typeOptions(session, draft, name), extend(base, "type")));
        curated.add(EditorFieldSpec.cycle("rule_trigger", "editor.field.rule_trigger",
                triggerOptions(draft, name), extend(base, "trigger")));
        curated.add(EditorFieldSpec.toggle("rule_include_players", "editor.field.rule_include_players",
                extend(base, "include_players")));
        curated.add(nav("rule_anti_abuse", "editor.field.rule_anti_abuse",
                "#" + base + ".anti_abuse", extend(base, "anti_abuse")));
        curated.add(nav("rule_actions", "editor.field.rule_actions",
                "#" + base + ".actions", extend(base, "actions")));
        curated.add(EditorFieldSpec.list("rule_rules", "editor.field.rule_rules",
                SOURCE_RULE_ENTRY, base + ".rules"));
        return withStructural(session, base, curated);
    }

    private static List<EditorFieldSpec> sourceRuleEntry(EditorSession session) {
        String name = session.context(CONTEXT_RULE_NAME);
        if (name == null || name.isEmpty()) {
            return List.of();
        }
        String base = "sources." + name + ".rules." + entryIndex(session);
        List<EditorFieldSpec> curated = List.of(
                EditorFieldSpec.text("rule_exp_formula", "editor.field.rule_exp_formula",
                        extend(base, "exp_formula")),
                EditorFieldSpec.list("rule_entity_types", "editor.field.rule_entity_types",
                        LIST_ENTRIES, base + ".entity_types"),
                EditorFieldSpec.list("rule_blocks", "editor.field.rule_blocks",
                        LIST_ENTRIES, base + ".blocks"),
                EditorFieldSpec.list("rule_states", "editor.field.rule_states",
                        LIST_ENTRIES, base + ".states"),
                EditorFieldSpec.list("rule_potion_types", "editor.field.rule_potion_types",
                        LIST_ENTRIES, base + ".potion_types"),
                EditorFieldSpec.list("rule_mob_ids", "editor.field.rule_mob_ids",
                        LIST_ENTRIES, base + ".mob_ids"));
        return withStructural(session, base, curated);
    }

    private static List<String> typeOptions(EditorSession session, YamlTextDocument draft, String name) {
        List<String> options = new ArrayList<>();
        if (session.service().plugin() instanceof EmakiLevelPlugin plugin) {
            for (LevelTypeConfig type : plugin.typeRegistry().all()) {
                options.add(type.id());
            }
        }
        String current = Texts.toStringSafe(draft.valueLenient("sources", name, "type"));
        if (!current.isBlank() && !options.contains(current)) {
            options.add(current);
        }
        return options;
    }

    private static List<String> triggerOptions(YamlTextDocument draft, String name) {
        List<String> options = new ArrayList<>(TRIGGER_OPTIONS);
        String current = Texts.toStringSafe(draft.valueLenient("sources", name, "trigger"));
        if (!current.isBlank() && !options.contains(current)) {
            options.add(current);
        }
        return options;
    }

    private static List<EditorFieldSpec> withStructural(EditorSession session, String base,
            List<EditorFieldSpec> curated) {
        String[] segments = base == null || base.isEmpty() ? new String[0] : base.split("\\.");
        List<EditorFieldSpec> combined = new ArrayList<>(curated);
        combined.addAll(EditorRenderer.structuralSpecs(session.draft(), segments, curated));
        return combined;
    }

    private static int entryIndex(EditorSession session) {
        try {
            return Math.max(0, Integer.parseInt(session.context("entry_index")));
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    private static String[] extend(String base, String... tail) {
        String[] head = base == null || base.isEmpty() ? new String[0] : base.split("\\.");
        String[] result = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, result, head.length, tail.length);
        return result;
    }

    private static EditorFieldSpec nav(String id, String labelKey, String targetMenu, String... path) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey, path, List.of(),
                targetMenu, null, null);
    }
}
