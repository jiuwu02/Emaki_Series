package emaki.jiuwu.craft.kits.config;

import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateParser;
import emaki.jiuwu.craft.corelib.schedule.cron.CronExpression;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.kits.model.ChoiceOption;
import emaki.jiuwu.craft.kits.model.KitDefinition;
import emaki.jiuwu.craft.kits.model.KitType;
import emaki.jiuwu.craft.kits.model.OpenMilestone;
import emaki.jiuwu.craft.kits.model.RewardEntry;
import emaki.jiuwu.craft.kits.model.RewardGroup;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class KitsConfigLoader extends YamlDirectoryLoader<KitDefinition> {

    private AppConfig base = AppConfig.defaults();

    public KitsConfigLoader(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "kits";
    }

    @Override
    protected String typeName() {
        return "kit";
    }

    @Override
    protected String idOf(KitDefinition value) {
        return value == null ? null : value.id();
    }

    public LoadResult loadAll() {
        File mainFile = new File(plugin.getDataFolder(), "config.yml");
        YamlSection main = YamlFiles.load(mainFile);
        base = parseMain(main, AppConfig.defaults());

        List<AppConfig.ConfigIssue> issues = new ArrayList<>(base.issues());
        int loaded = load();
        Map<String, KitDefinition> parsed = new LinkedHashMap<>(all());
        for (String message : issues()) {
            issues.add(new AppConfig.ConfigIssue("kits", message));
        }
        validateNestedGraph(parsed, issues);
        AppConfig config = base.withKits(parsed, issues);
        return new LoadResult(config, parsed, List.copyOf(issues), loaded);
    }

    private AppConfig parseMain(YamlSection yaml, AppConfig fallback) {
        String version = yaml.getString("version", fallback.version());
        String language = yaml.getString("language", fallback.language());
        String timeZone = yaml.getString("timezone", fallback.timeZone());
        if (timeZone == null || timeZone.isBlank()) {
            timeZone = fallback.timeZone();
        }
        YamlSection storage = yaml.getSection("storage");
        AppConfig.StorageSettings storageSettings = fallback.storage();
        if (storage != null) {
            storageSettings = new AppConfig.StorageSettings(
                    storage.getString("backend", storageSettings.backend()),
                    Math.max(1, intVal(storage.get("save_delay_ticks"), storageSettings.saveDelayTicks())),
                    boolVal(storage.get("atomic_writes"), storageSettings.atomicWrites()));
        }
        YamlSection economy = yaml.getSection("economy");
        AppConfig.EconomySettings economySettings = fallback.economy();
        if (economy != null) {
            economySettings = new AppConfig.EconomySettings(
                    economy.getString("provider", economySettings.provider()),
                    economy.getString("currency_id", economySettings.currencyId()));
        }
        YamlSection invite = yaml.getSection("invite");
        AppConfig.InviteSettings inviteSettings = fallback.invite();
        if (invite != null) {
            List<String> markers = invite.getStringList("new_player_markers");
            if (markers.isEmpty()) {
                markers = inviteSettings.newPlayerMarkers();
            }
            inviteSettings = new AppConfig.InviteSettings(
                    boolVal(invite.get("enabled"), inviteSettings.enabled()),
                    boolVal(invite.get("require_new_player"), inviteSettings.requireNewPlayer()),
                    boolVal(invite.get("allow_mutual"), inviteSettings.allowMutual()),
                    Math.max(1, intVal(invite.get("max_binds_per_invitee"), inviteSettings.maxBindsPerInvitee())),
                    markers);
        }
        YamlSection cdk = yaml.getSection("cdk");
        AppConfig.CdkSettings cdkSettings = fallback.cdk();
        if (cdk != null) {
            cdkSettings = new AppConfig.CdkSettings(
                    boolVal(cdk.get("case_sensitive"), cdkSettings.caseSensitive()),
                    Math.max(1, intVal(cdk.get("max_codes_per_kit"), cdkSettings.maxCodesPerKit())));
        }
        YamlSection join = yaml.getSection("join");
        AppConfig.JoinSettings joinSettings = fallback.join();
        if (join != null) {
            joinSettings = new AppConfig.JoinSettings(
                    Math.max(0L, longVal(join.get("missed_window_seconds"), joinSettings.missedWindowSeconds())),
                    join.getString("invite_code_type", joinSettings.inviteCodeType()));
        }
        YamlSection nested = yaml.getSection("nested");
        AppConfig.NestedSettings nestedSettings = fallback.nested();
        if (nested != null) {
            nestedSettings = new AppConfig.NestedSettings(
                    Math.max(1, Math.min(16, intVal(nested.get("max_depth"), nestedSettings.maxDepth()))),
                    boolVal(nested.get("count_pass_to_nested"), nestedSettings.countPassToNested()));
        }
        boolean releaseDefaultData = boolVal(yaml.get("release_default_data"), fallback.releaseDefaultData());
        return new AppConfig(version, language, timeZone, storageSettings, economySettings,
                inviteSettings, cdkSettings, nestedSettings, joinSettings, fallback.kits(),
                releaseDefaultData, List.of());
    }

    @Override
    protected KitDefinition parse(File file, YamlSection node) {
        if (node == null) {
            return null;
        }
        String rawId = node.getString("id", "");
        if (!KitIds.valid(rawId)) {
            plugin.getLogger().warning("[kits] " + file.getName()
                    + " 礼包 id 非法或缺失: " + rawId);
            return null;
        }
        String id = KitIds.normalize(rawId);
        List<KitDefinition.ConfigIssue> issues = new ArrayList<>();
        KitDefinition def = parseKit(id, node, base, issues);
        for (KitDefinition.ConfigIssue issue : issues) {
            plugin.getLogger().warning("[kits] " + file.getName() + " 礼包 " + id
                    + " 的 " + issue.path() + " 校验失败: " + issue.reasonKey());
        }
        if (!issues.isEmpty()) {
            return null;
        }
        return def;
    }

    private KitDefinition parseKit(String id, YamlSection node, AppConfig base,
                                   List<KitDefinition.ConfigIssue> issues) {
        KitType type = KitType.fromConfig(node.getString("type"));
        if (type == null) {
            issues.add(new KitDefinition.ConfigIssue("type", "error.config.unknown_type"));
            type = KitType.GENERIC;
        }
        boolean enabled = boolVal(node.get("enabled"), true);
        String permission = node.getString("permission", "emakikits.claim");
        if (!permission.startsWith("emakikits.")) {
            issues.add(new KitDefinition.ConfigIssue("permission", "error.config.permission_prefix"));
            permission = "emakikits.claim";
        }
        String display = node.getString("display_name", id);

        KitDefinition.JoinRule joinRule = new KitDefinition.JoinRule(
                boolVal(node.get("join.on_first_join"), true),
                boolVal(node.get("join.on_every_specified_join"), false),
                boolVal(node.get("join.require_invite_code"), true),
                node.getString("join.invite_code_type", base.join().inviteCodeType()),
                Math.max(0L, longVal(node.get("join.missed_window_seconds"), base.join().missedWindowSeconds())),
                node.getStringList("join.worlds"));
        KitDefinition.InviteRule inviteRule = new KitDefinition.InviteRule(
                node.getString("invite.reward_target", "INVITER").toUpperCase(Locale.ROOT),
                boolVal(node.get("invite.require_new_player"), base.invite().requireNewPlayer()),
                boolVal(node.get("invite.allow_mutual"), base.invite().allowMutual()),
                Math.max(1, intVal(node.get("invite.max_binds_per_invitee"), base.invite().maxBindsPerInvitee())));
        KitDefinition.CycleRule cycle = new KitDefinition.CycleRule(
                node.getString("cycle.timezone", base.timeZone()),
                boolVal(node.get("cycle.allow_makeup"), false));
        boolean cyclic = type == KitType.DAILY || type == KitType.WEEKLY || type == KitType.MONTHLY;
        if (cyclic && !validTimeZone(cycle.timeZone())) {
            issues.add(new KitDefinition.ConfigIssue("cycle.timezone", "error.config.invalid_timezone"));
        }
        KitDefinition.CdkRule.CdkMode cdkMode = "per_player_once".equalsIgnoreCase(node.getString("cdk.mode"))
                ? KitDefinition.CdkRule.CdkMode.PER_PLAYER_ONCE
                : KitDefinition.CdkRule.CdkMode.GLOBAL_ONCE;
        List<String> codes = node.getStringList("cdk.codes");
        if (type == KitType.CDK && codes.isEmpty()) {
            issues.add(new KitDefinition.ConfigIssue("cdk.codes", "error.config.cdk_codes_empty"));
        }
        if (codes.size() > base.cdk().maxCodesPerKit()) {
            issues.add(new KitDefinition.ConfigIssue("cdk.codes", "error.config.cdk_codes_overflow"));
        }
        KitDefinition.CdkRule cdkRule = new KitDefinition.CdkRule(cdkMode, codes,
                Math.max(1, intVal(node.get("cdk.per_player_limit"), 1)));

        List<ChoiceOption> options = parseOptions(node.getSection("choice.options"), issues);
        GuiTemplate inlineGui = parseInlineGui(node.getSection("choice.gui"), id, issues);
        KitDefinition.ChoiceRule choice = new KitDefinition.ChoiceRule(
                Math.max(0, intVal(node.get("choice.min_select"), 1)),
                intVal(node.get("choice.max_select"), 1),
                node.getString("choice.gui_template", ""), options, inlineGui);
        if (type == KitType.CHOICE && !choice.valid()) {
            issues.add(new KitDefinition.ConfigIssue("choice", "error.config.choice_invalid"));
        }
        if (type == KitType.CHOICE && !choice.hasGui()) {
            issues.add(new KitDefinition.ConfigIssue("choice.gui", "error.config.choice_gui_missing"));
        }

        KitDefinition.PurchaseRule purchase = new KitDefinition.PurchaseRule(
                boolVal(node.get("purchase.enabled"), false),
                Math.max(0D, doubleVal(node.get("purchase.price"), 0D)),
                node.getString("purchase.economy_provider", base.economy().provider()),
                node.getString("purchase.currency_id", base.economy().currencyId()),
                Math.max(0, intVal(node.get("purchase.max_purchases"), 0)),
                boolVal(node.get("purchase.count_pass_to_nested"), base.nested().countPassToNested()));
        if (purchase.enabled() && purchase.price() > 0D && purchase.maxPurchases() == 0) {
            issues.add(new KitDefinition.ConfigIssue("purchase.max_purchases", "error.config.purchase_limit_missing"));
        }
        List<OpenMilestone> milestones = parseMilestones(node.getSection("open.milestones"), issues);
        KitDefinition.OpenRule open = new KitDefinition.OpenRule(
                boolVal(node.get("open.enabled"), false),
                Math.max(0, intVal(node.get("open.max_opens"), 0)), milestones);
        for (OpenMilestone milestone : milestones) {
            if (!milestone.valid()) {
                issues.add(new KitDefinition.ConfigIssue("open.milestones", "error.config.milestone_invalid"));
                break;
            }
        }

        RewardGroup fixed = new RewardGroup(id + ":fixed",
                parseRewards(node.getMapList("rewards.fixed"), id + ".rewards.fixed", issues), false, 0);
        List<RewardEntry> randomEntries = parseRewards(node.getMapList("rewards.random"),
                id + ".rewards.random", issues);
        boolean weighted = boolVal(node.get("rewards.weighted"), false);
        RewardGroup random = weighted
                ? RewardGroup.weighted(id + ":random", randomEntries)
                : RewardGroup.fixed(id + ":random", randomEntries);
        if (weighted && !random.valid()) {
            issues.add(new KitDefinition.ConfigIssue("rewards.random", "error.config.invalid_weight"));
        }
        if (fixed.entries().isEmpty() && random.entries().isEmpty() && options.isEmpty()) {
            issues.add(new KitDefinition.ConfigIssue("rewards", "error.config.rewards_empty"));
        }

        YamlSection limitNode = node.getSection("limit");
        KitDefinition.LimitRule limit = new KitDefinition.LimitRule(
                limitNode != null && boolVal(limitNode.get("enabled"), false),
                limitNode == null ? "always" : limitNode.getString("mode", "always"),
                limitNode == null ? "" : limitNode.getString("start", ""),
                limitNode == null ? "" : limitNode.getString("end", ""),
                limitNode == null ? "" : limitNode.getString("cron", ""),
                limitNode == null ? 0L : Math.max(0L, longVal(limitNode.get("duration_seconds"), 0L)),
                limitNode == null ? base.timeZone() : limitNode.getString("timezone", base.timeZone()));
        if (limit.enabled()) {
            validateLimit(id, limit, issues);
        }
        int maxClaimsTotal = Math.max(0, intVal(node.get("max_claims_total"), 0));
        return new KitDefinition(id, type, enabled, permission, display, joinRule, inviteRule, cycle,
                cdkRule, choice, purchase, open, limit, fixed, random, maxClaimsTotal, List.copyOf(issues));
    }

    private GuiTemplate parseInlineGui(YamlSection section, String kitId,
                                       List<KitDefinition.ConfigIssue> issues) {
        if (section == null || section.isEmpty()) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>(section.asMap());
        if (!data.containsKey("id")) {
            data.put("id", "kit_choice_" + kitId);
        }
        GuiTemplate template = GuiTemplateParser.parse(new MapYamlSection(data));
        if (template == null || template.id() == null || template.id().isBlank()) {
            issues.add(new KitDefinition.ConfigIssue("choice.gui", "error.config.choice_gui_invalid"));
            return null;
        }
        return template;
    }

    private List<RewardEntry> parseRewards(List<Map<?, ?>> rawList, String path,
                                           List<KitDefinition.ConfigIssue> issues) {
        List<RewardEntry> entries = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> raw : rawList) {
            String at = path + "[" + index + "]";
            String kindRaw = str(raw.get("type"));
            RewardEntry.RewardKind kind = switch (kindRaw.toLowerCase(Locale.ROOT)) {
                case "item" -> RewardEntry.RewardKind.ITEM;
                case "money" -> RewardEntry.RewardKind.MONEY;
                case "nested_kit" -> RewardEntry.RewardKind.NESTED_KIT;
                case "action" -> RewardEntry.RewardKind.ACTION;
                default -> null;
            };
            if (kind == null) {
                issues.add(new KitDefinition.ConfigIssue(at + ".type", "error.config.reward_type"));
                index++;
                continue;
            }
            String entryId = str(raw.get("id"));
            if (entryId.isBlank()) {
                entryId = "reward_" + index;
            }
            int weight = intVal(raw.get("weight"), 0);
            if (weight < 0) {
                issues.add(new KitDefinition.ConfigIssue(at + ".weight", "error.config.invalid_weight"));
                weight = 0;
            }
            String source = str(raw.get("source"));
            String action = str(raw.get("action"));
            String nestedKitId = str(raw.get("kit_id"));
            if (kind == RewardEntry.RewardKind.ITEM && source.isBlank()) {
                issues.add(new KitDefinition.ConfigIssue(at + ".source", "error.config.item_source_missing"));
            }
            if (kind == RewardEntry.RewardKind.ACTION && action.isBlank()) {
                issues.add(new KitDefinition.ConfigIssue(at + ".action", "error.config.action_empty"));
            }
            if (kind == RewardEntry.RewardKind.NESTED_KIT && nestedKitId.isBlank()) {
                issues.add(new KitDefinition.ConfigIssue(at + ".kit_id", "error.config.nested_kit_missing"));
            }
            entries.add(new RewardEntry(entryId, kind,
                    Math.max(1, intVal(raw.get("amount"), 1)),
                    source, action, nestedKitId, weight, str(raw.get("permission"))));
            index++;
        }
        return List.copyOf(entries);
    }

    private List<ChoiceOption> parseOptions(YamlSection section,
                                            List<KitDefinition.ConfigIssue> issues) {
        List<ChoiceOption> options = new ArrayList<>();
        if (section == null) {
            return options;
        }
        for (String rawId : section.getKeys(false)) {
            YamlSection node = section.getSection(rawId);
            if (node == null) {
                continue;
            }
            String optionId = KitIds.normalize(rawId);
            if (optionId.isEmpty()) {
                issues.add(new KitDefinition.ConfigIssue("choice.options." + rawId, "error.config.invalid_id"));
                continue;
            }
            List<RewardEntry> rewards = parseRewards(node.getMapList("rewards"),
                    "choice.options." + optionId + ".rewards", issues);
            options.add(new ChoiceOption(optionId, node.getString("display", optionId),
                    node.getString("permission", ""), rewards, node.getString("kit_id", "")));
        }
        return List.copyOf(options);
    }

    private List<OpenMilestone> parseMilestones(YamlSection section,
                                                List<KitDefinition.ConfigIssue> issues) {
        List<OpenMilestone> milestones = new ArrayList<>();
        if (section == null) {
            return milestones;
        }
        for (String rawCount : section.getKeys(false)) {
            int count;
            try {
                count = Integer.parseInt(rawCount.trim());
            } catch (NumberFormatException exception) {
                issues.add(new KitDefinition.ConfigIssue("open.milestones." + rawCount, "error.config.milestone_invalid"));
                continue;
            }
            YamlSection node = section.getSection(rawCount);
            List<RewardEntry> rewards = node == null ? List.of()
                    : parseRewards(node.getMapList("rewards"), "open.milestones." + rawCount + ".rewards", issues);
            milestones.add(new OpenMilestone(count, rewards));
        }
        return List.copyOf(milestones);
    }

    private void validateNestedGraph(Map<String, KitDefinition> kits, List<AppConfig.ConfigIssue> issues) {
        for (KitDefinition def : kits.values()) {
            Set<String> visited = new HashSet<>();
            detectCycle(def.id(), def, kits, visited, issues);
        }
    }

    private void detectCycle(String rootId, KitDefinition current, Map<String, KitDefinition> kits,
                             Set<String> visited, List<AppConfig.ConfigIssue> issues) {
        if (!visited.add(current.id())) {
            issues.add(new AppConfig.ConfigIssue("kits." + rootId + ".rewards", "error.config.nested_cycle"));
            return;
        }
        for (RewardEntry entry : allRewards(current)) {
            if (entry.kind() != RewardEntry.RewardKind.NESTED_KIT || entry.nestedKitId().isBlank()) {
                continue;
            }
            if (entry.nestedKitId().equalsIgnoreCase(current.id())) {
                issues.add(new AppConfig.ConfigIssue("kits." + rootId + ".rewards", "error.config.nested_self"));
                continue;
            }
            KitDefinition nested = kits.get(KitIds.normalize(entry.nestedKitId()));
            if (nested == null) {
                issues.add(new AppConfig.ConfigIssue("kits." + rootId + ".rewards", "error.config.nested_missing"));
                continue;
            }
            detectCycle(rootId, nested, kits, new HashSet<>(visited), issues);
        }
    }

    private List<RewardEntry> allRewards(KitDefinition def) {
        List<RewardEntry> all = new ArrayList<>(def.fixedRewards().entries());
        all.addAll(def.randomRewards().entries());
        for (ChoiceOption option : def.choice().options()) {
            all.addAll(option.rewards());
        }
        for (OpenMilestone milestone : def.open().milestones()) {
            all.addAll(milestone.rewards());
        }
        return all;
    }

    private void validateLimit(String kitId, KitDefinition.LimitRule limit,
                               List<KitDefinition.ConfigIssue> issues) {
        String mode = limit.mode().toLowerCase(Locale.ROOT);
        if (!Set.of("always", "range", "cron").contains(mode)) {
            issues.add(new KitDefinition.ConfigIssue("limit.mode", "error.config.limit_mode"));
            return;
        }
        if ("range".equals(mode)) {
            boolean valid = parseEpoch(limit.start()) != null && parseEpoch(limit.end()) != null
                    && parseEpoch(limit.start()) <= parseEpoch(limit.end());
            if (!valid) {
                issues.add(new KitDefinition.ConfigIssue("limit", "error.config.limit_range"));
            }
        }
        if ("cron".equals(mode)) {
            try {
                CronExpression.parse(limit.cron());
            } catch (RuntimeException exception) {
                issues.add(new KitDefinition.ConfigIssue("limit.cron", "error.config.limit_cron"));
            }
            if (limit.durationSeconds() <= 0L) {
                issues.add(new KitDefinition.ConfigIssue("limit.duration_seconds", "error.config.limit_duration"));
            }
        }
    }

    private static Long parseEpoch(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim()).toEpochMilli();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean validTimeZone(String raw) {
        try {
            ZoneId.of(raw);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long longVal(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double doubleVal(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean boolVal(Object value, boolean fallback) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public record LoadResult(AppConfig config, Map<String, KitDefinition> kits,
                             List<AppConfig.ConfigIssue> issues, int loaded) {}
}
