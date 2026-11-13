package emaki.jiuwu.craft.kits.config;

import emaki.jiuwu.craft.kits.model.ChoiceOption;
import emaki.jiuwu.craft.kits.model.KitDefinition;
import emaki.jiuwu.craft.kits.model.KitType;
import emaki.jiuwu.craft.kits.model.OpenMilestone;
import emaki.jiuwu.craft.kits.model.RewardEntry;
import emaki.jiuwu.craft.kits.model.RewardGroup;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.time.ZoneId;

public final class KitsConfigLoader {

    private final File dataFolder;

    public KitsConfigLoader(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public LoadResult loadAll() {
        AppConfig base = AppConfig.defaults();
        File mainFile = new File(dataFolder, "config.yml");
        YamlConfiguration main = YamlConfiguration.loadConfiguration(mainFile);
        base = parseMain(main, base);

        File kitsFile = new File(dataFolder, "kits.yml");
        YamlConfiguration kitsYaml = YamlConfiguration.loadConfiguration(kitsFile);
        ConfigurationSection kitsSection = kitsYaml.getConfigurationSection("kits");
        List<AppConfig.ConfigIssue> issues = new ArrayList<>(base.issues());
        Map<String, KitDefinition> parsed = new LinkedHashMap<>();

        if (kitsSection == null) {
            issues.add(new AppConfig.ConfigIssue("kits", "error.config.kits_missing"));
            return new LoadResult(base.withKits(parsed, issues), parsed, issues);
        }

        for (String rawId : kitsSection.getKeys(false)) {
            String id = KitIds.normalize(rawId);
            if (!KitIds.valid(rawId)) {
                issues.add(new AppConfig.ConfigIssue("kits." + rawId, "error.config.invalid_id"));
                continue;
            }
            if (parsed.containsKey(id)) {
                issues.add(new AppConfig.ConfigIssue("kits." + id, "error.config.duplicate_id"));
                continue;
            }
            ConfigurationSection node = kitsSection.getConfigurationSection(rawId);
            if (node == null) {
                issues.add(new AppConfig.ConfigIssue("kits." + id, "error.config.section_missing"));
                continue;
            }
            List<KitDefinition.ConfigIssue> kitIssues = new ArrayList<>();
            KitDefinition def = parseKit(id, node, base, kitIssues);
            if (kitIssues.isEmpty()) {
                parsed.put(id, def);
            }
            for (KitDefinition.ConfigIssue issue : kitIssues) {
                issues.add(new AppConfig.ConfigIssue("kits." + id + "." + issue.path(), issue.reasonKey()));
            }
        }

        validateNestedGraph(parsed, issues);
        return new LoadResult(base.withKits(parsed, issues), parsed, List.copyOf(issues));
    }

    private AppConfig parseMain(YamlConfiguration yaml, AppConfig fallback) {
        String version = yaml.getString("version", fallback.version());
        String language = yaml.getString("language", fallback.language());
        String timeZone = yaml.getString("timezone", fallback.timeZone());
        if (timeZone == null || timeZone.isBlank()) {
            timeZone = fallback.timeZone();
        }
        ConfigurationSection storage = yaml.getConfigurationSection("storage");
        AppConfig.StorageSettings storageSettings = fallback.storage();
        if (storage != null) {
            storageSettings = new AppConfig.StorageSettings(
                    storage.getString("backend", storageSettings.backend()),
                    Math.max(1, storage.getInt("save_delay_ticks", storageSettings.saveDelayTicks())),
                    storage.getBoolean("atomic_writes", storageSettings.atomicWrites()));
        }
        ConfigurationSection economy = yaml.getConfigurationSection("economy");
        AppConfig.EconomySettings economySettings = fallback.economy();
        if (economy != null) {
            economySettings = new AppConfig.EconomySettings(
                    economy.getString("provider", economySettings.provider()),
                    economy.getString("currency_id", economySettings.currencyId()));
        }
        ConfigurationSection invite = yaml.getConfigurationSection("invite");
        AppConfig.InviteSettings inviteSettings = fallback.invite();
        if (invite != null) {
            List<String> markers = invite.getStringList("new_player_markers");
            if (markers.isEmpty()) {
                markers = inviteSettings.newPlayerMarkers();
            }
            inviteSettings = new AppConfig.InviteSettings(
                    invite.getBoolean("enabled", inviteSettings.enabled()),
                    invite.getBoolean("require_new_player", inviteSettings.requireNewPlayer()),
                    invite.getBoolean("allow_mutual", inviteSettings.allowMutual()),
                    Math.max(1, invite.getInt("max_binds_per_invitee", inviteSettings.maxBindsPerInvitee())),
                    markers);
        }
        ConfigurationSection cdk = yaml.getConfigurationSection("cdk");
        AppConfig.CdkSettings cdkSettings = fallback.cdk();
        if (cdk != null) {
            cdkSettings = new AppConfig.CdkSettings(
                    cdk.getBoolean("case_sensitive", cdkSettings.caseSensitive()),
                    Math.max(1, cdk.getInt("max_codes_per_kit", cdkSettings.maxCodesPerKit())));
        }
        ConfigurationSection join = yaml.getConfigurationSection("join");
        AppConfig.JoinSettings joinSettings = fallback.join();
        if (join != null) {
            joinSettings = new AppConfig.JoinSettings(
                    Math.max(0L, join.getLong("missed_window_seconds", joinSettings.missedWindowSeconds())),
                    join.getString("invite_code_type", joinSettings.inviteCodeType()));
        }
        ConfigurationSection nested = yaml.getConfigurationSection("nested");
        AppConfig.NestedSettings nestedSettings = fallback.nested();
        if (nested != null) {
            nestedSettings = new AppConfig.NestedSettings(
                    Math.max(1, Math.min(16, nested.getInt("max_depth", nestedSettings.maxDepth()))),
                    nested.getBoolean("count_pass_to_nested", nestedSettings.countPassToNested()));
        }
        return new AppConfig(version, language, timeZone, storageSettings, economySettings,
                inviteSettings, cdkSettings, nestedSettings, joinSettings, fallback.kits(), List.of());
    }

    private KitDefinition parseKit(String id, ConfigurationSection node, AppConfig base,
                                   List<KitDefinition.ConfigIssue> issues) {
        KitType type = KitType.fromConfig(node.getString("type"));
        if (type == null) {
            issues.add(new KitDefinition.ConfigIssue("type", "error.config.unknown_type"));
            type = KitType.GENERIC;
        }
        boolean enabled = node.getBoolean("enabled", true);
        String permission = node.getString("permission", "emakikits.claim");
        if (!permission.startsWith("emakikits.")) {
            issues.add(new KitDefinition.ConfigIssue("permission", "error.config.permission_prefix"));
            permission = "emakikits.claim";
        }
        String display = node.getString("display_name", id);

        KitDefinition.JoinRule joinRule = new KitDefinition.JoinRule(
                node.getBoolean("join.on_first_join", true),
                node.getBoolean("join.on_every_specified_join", false),
                node.getBoolean("join.require_invite_code", true),
                node.getString("join.invite_code_type", base.join().inviteCodeType()),
                Math.max(0L, node.getLong("join.missed_window_seconds", base.join().missedWindowSeconds())),
                node.getStringList("join.worlds"));
        KitDefinition.InviteRule inviteRule = new KitDefinition.InviteRule(
                node.getString("invite.reward_target", "INVITER").toUpperCase(Locale.ROOT),
                node.getBoolean("invite.require_new_player", base.invite().requireNewPlayer()),
                node.getBoolean("invite.allow_mutual", base.invite().allowMutual()),
                Math.max(1, node.getInt("invite.max_binds_per_invitee", base.invite().maxBindsPerInvitee())));
        KitDefinition.CycleRule cycle = new KitDefinition.CycleRule(
                node.getString("cycle.timezone", base.timeZone()),
                node.getBoolean("cycle.allow_makeup", false));
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
                Math.max(1, node.getInt("cdk.per_player_limit", 1)));

        List<ChoiceOption> options = parseOptions(node.getConfigurationSection("choice.options"), issues);
        KitDefinition.ChoiceRule choice = new KitDefinition.ChoiceRule(
                Math.max(0, node.getInt("choice.min_select", 1)),
                node.getInt("choice.max_select", 1),
                node.getString("choice.gui_template", "choice"), options);
        if (type == KitType.CHOICE && !choice.valid()) {
            issues.add(new KitDefinition.ConfigIssue("choice", "error.config.choice_invalid"));
        }

        KitDefinition.PurchaseRule purchase = new KitDefinition.PurchaseRule(
                node.getBoolean("purchase.enabled", false),
                Math.max(0D, node.getDouble("purchase.price", 0D)),
                node.getString("purchase.economy_provider", base.economy().provider()),
                node.getString("purchase.currency_id", base.economy().currencyId()),
                Math.max(0, node.getInt("purchase.max_purchases", 0)),
                node.getBoolean("purchase.count_pass_to_nested", base.nested().countPassToNested()));
        if (purchase.enabled() && purchase.price() > 0D && purchase.maxPurchases() == 0) {
            issues.add(new KitDefinition.ConfigIssue("purchase.max_purchases", "error.config.purchase_limit_missing"));
        }
        List<OpenMilestone> milestones = parseMilestones(node.getConfigurationSection("open.milestones"), issues);
        KitDefinition.OpenRule open = new KitDefinition.OpenRule(
                node.getBoolean("open.enabled", false),
                Math.max(0, node.getInt("open.max_opens", 0)), milestones);
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
        boolean weighted = node.getBoolean("rewards.weighted", false);
        RewardGroup random = weighted
                ? RewardGroup.weighted(id + ":random", randomEntries)
                : RewardGroup.fixed(id + ":random", randomEntries);
        if (weighted && !random.valid()) {
            issues.add(new KitDefinition.ConfigIssue("rewards.random", "error.config.invalid_weight"));
        }
        if (fixed.entries().isEmpty() && random.entries().isEmpty() && options.isEmpty()) {
            issues.add(new KitDefinition.ConfigIssue("rewards", "error.config.rewards_empty"));
        }

        ConfigurationSection limitNode = node.getConfigurationSection("limit");
        KitDefinition.LimitRule limit = new KitDefinition.LimitRule(
                limitNode != null && limitNode.getBoolean("enabled", false),
                limitNode == null ? "always" : limitNode.getString("mode", "always"),
                limitNode == null ? "" : limitNode.getString("start", ""),
                limitNode == null ? "" : limitNode.getString("end", ""),
                limitNode == null ? "" : limitNode.getString("cron", ""),
                limitNode == null ? 0L : Math.max(0L, limitNode.getLong("duration_seconds", 0L)),
                limitNode == null ? base.timeZone() : limitNode.getString("timezone", base.timeZone()));
        if (limit.enabled()) {
            validateLimit(id, limit, issues);
        }
        int maxClaimsTotal = Math.max(0, node.getInt("max_claims_total", 0));
        return new KitDefinition(id, type, enabled, permission, display, joinRule, inviteRule, cycle,
                cdkRule, choice, purchase, open, limit, fixed, random, maxClaimsTotal, List.copyOf(issues));
    }

    private List<RewardEntry> parseRewards(List<Map<?, ?>> rawList, String path,
                                           List<KitDefinition.ConfigIssue> issues) {
        List<RewardEntry> entries = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> raw : rawList) {
            String at = path + "[" + index + "]";
            String kindRaw = str(raw.get("type"));
            RewardEntry.RewardKind kind = switch (kindRaw.toLowerCase(Locale.ROOT)) {
                case "command" -> RewardEntry.RewardKind.COMMAND;
                case "item" -> RewardEntry.RewardKind.ITEM;
                case "money" -> RewardEntry.RewardKind.MONEY;
                case "exp" -> RewardEntry.RewardKind.EXP;
                case "nested_kit" -> RewardEntry.RewardKind.NESTED_KIT;
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
            List<String> lore = stringList(raw.get("lore"));
            RewardEntry entry = new RewardEntry(entryId, kind,
                    Math.max(1, intVal(raw.get("amount"), 1)),
                    str(raw.get("command")), str(raw.get("material")),
                    str(raw.get("name")), lore, str(raw.get("kit_id")), weight,
                    str(raw.get("permission")));
            if (kind == RewardEntry.RewardKind.NESTED_KIT && entry.nestedKitId().isBlank()) {
                issues.add(new KitDefinition.ConfigIssue(at + ".kit_id", "error.config.nested_kit_missing"));
            }
            if (kind == RewardEntry.RewardKind.COMMAND && entry.command().isBlank()) {
                issues.add(new KitDefinition.ConfigIssue(at + ".command", "error.config.command_empty"));
            }
            entries.add(entry);
            index++;
        }
        return List.copyOf(entries);
    }

    private List<ChoiceOption> parseOptions(ConfigurationSection section,
                                            List<KitDefinition.ConfigIssue> issues) {
        List<ChoiceOption> options = new ArrayList<>();
        if (section == null) {
            return options;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(rawId);
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

    private List<OpenMilestone> parseMilestones(ConfigurationSection section,
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
            ConfigurationSection node = section.getConfigurationSection(rawCount);
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
                emaki.jiuwu.craft.corelib.schedule.cron.CronExpression.parse(limit.cron());
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

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(str(item));
            }
            return List.copyOf(result);
        }
        return List.of();
    }

    public record LoadResult(AppConfig config, Map<String, KitDefinition> kits,
                             List<AppConfig.ConfigIssue> issues) {}
}
