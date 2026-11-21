package emaki.jiuwu.craft.kits.command;

import emaki.jiuwu.craft.kits.EmakiKitsPlugin;
import emaki.jiuwu.craft.kits.config.AppConfig;
import emaki.jiuwu.craft.kits.config.KitIds;
import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.kits.limit.TimeWindow;
import emaki.jiuwu.craft.kits.model.ChoiceOption;
import emaki.jiuwu.craft.kits.model.ClaimResult;
import emaki.jiuwu.craft.kits.model.KitDefinition;
import emaki.jiuwu.craft.kits.model.KitType;
import emaki.jiuwu.craft.kits.model.RewardEntry;
import emaki.jiuwu.craft.kits.service.KitService;
import emaki.jiuwu.craft.kits.gui.ChoiceGuiService;
import emaki.jiuwu.craft.kits.join.InviteCodeService;
import emaki.jiuwu.craft.kits.store.KitState;
import emaki.jiuwu.craft.kits.store.KitStore;
import emaki.jiuwu.craft.kits.api.event.KitClaimedEvent;
import emaki.jiuwu.craft.kits.api.model.KitClaimView;
import org.bukkit.Bukkit;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import emaki.jiuwu.craft.corelib.log.LogTag;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.kits.log.KitsLogTag;

public final class KitCommandRouter {

    private static final List<String> TOP = List.of("help", "list", "claim", "choose", "buy",
            "redeem", "invite", "open", "inspect", "grant", "cdk", "reload",
            "check", "preview", "edit", "top");
    private static final List<String> KIT_ARG_SUBS = List.of(
            "claim", "choose", "buy", "redeem", "open", "preview", "edit");
    private static final String HELP_ROOT = "/emakikits";

    private final EmakiKitsPlugin plugin;
    private final KitService service;
    private final KitStore store;
    private final MessageService messages;
    private final Supplier<AppConfig> config;
    private final ChoiceGuiService choiceGui;
    private final InviteCodeService inviteCodes;

    public KitCommandRouter(EmakiKitsPlugin plugin, KitService service, KitStore store,
                            MessageService messages, Supplier<AppConfig> config,
                            ChoiceGuiService choiceGui, InviteCodeService inviteCodes) {
        this.plugin = plugin;
        this.service = service;
        this.store = store;
        this.messages = messages;
        this.config = config;
        this.choiceGui = choiceGui;
        this.inviteCodes = inviteCodes;
    }

    public void route(CommandSender sender, String[] args) {
        if (!sender.hasPermission(KitCommandAdapter.ROOT_PERMISSION)) {
            messages.send(sender, "error.permission.denied", Map.of("sub", "root"));
            return;
        }
        if (args.length == 0) {
            messages.send(sender, "general.usage");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> {
                if (denied(sender, "emakikits.use", sub)) return;
                sendHelpPage(sender, Arrays.asList(args).subList(1, args.length));
            }
            case "list" -> {
                if (denied(sender, "emakikits.use", sub)) return;
                listKits(sender);
            }
            case "claim" -> {
                if (denied(sender, "emakikits.claim", sub)) return;
                Player player = requirePlayer(sender);
                if (player == null) return;
                if (args.length < 2) {
                    messages.send(sender, "general.usage_claim");
                    return;
                }
                boolean makeup = args.length >= 3 && "makeup".equalsIgnoreCase(args[2]);
                report(sender, service.claim(player, args[1], List.of(), makeup));
            }
            case "choose" -> {
                if (denied(sender, "emakikits.open", sub)) return;
                Player player = requirePlayer(sender);
                if (player == null) return;
                if (args.length < 2) {
                    messages.send(sender, "general.usage_choose");
                    return;
                }
                if (args.length == 2) {
                    choiceGui.open(player, args[1]);
                    return;
                }
                List<String> options = Arrays.asList(args).subList(2, args.length);
                report(sender, service.claim(player, args[1], options));
            }
            case "buy" -> {
                if (denied(sender, "emakikits.buy", sub)) return;
                Player player = requirePlayer(sender);
                if (player == null) return;
                if (args.length < 2) {
                    messages.send(sender, "general.usage_buy");
                    return;
                }
                report(sender, service.buy(player, args[1]));
            }
            case "redeem" -> {
                if (denied(sender, "emakikits.redeem", sub)) return;
                Player player = requirePlayer(sender);
                if (player == null) return;
                if (args.length < 3) {
                    messages.send(sender, "general.usage_redeem");
                    return;
                }
                report(sender, service.redeem(player, args[1], args[2]));
            }
            case "invite" -> {
                if (denied(sender, "emakikits.invite", sub)) return;
                Player player = requirePlayer(sender);
                if (player == null) return;
                if (args.length < 2) {
                    messages.send(sender, "general.usage_invite");
                    return;
                }
                Player invitee = Bukkit.getPlayerExact(args[1]);
                if (invitee == null) {
                    messages.send(sender, "error.invite.offline", Map.of("player", args[1]));
                    return;
                }
                boolean isNew = !store.read(s -> s.firstJoin().containsKey(invitee.getUniqueId()));
                report(sender, service.bindInvite(player, invitee.getUniqueId(), isNew));
            }
            case "open" -> {
                if (denied(sender, "emakikits.open", sub)) return;
                Player player = requirePlayer(sender);
                if (player == null) return;
                if (args.length < 2) {
                    messages.send(sender, "general.usage_open");
                    return;
                }
                report(sender, service.claim(player, args[1]));
            }
            case "inspect" -> {
                if (denied(sender, "emakikits.inspect", sub)) return;
                if (args.length < 2) {
                    messages.send(sender, "general.usage_inspect");
                    return;
                }
                inspect(sender, args[1]);
            }
            case "grant" -> {
                if (denied(sender, "emakikits.grant", sub)) return;
                if (args.length < 3) {
                    messages.send(sender, "general.usage_grant");
                    return;
                }
                grant(sender, args[1], args[2]);
            }
            case "cdk" -> {
                if (denied(sender, "emakikits.cdk", sub)) return;
                cdk(sender, args);
            }
            case "reload" -> {
                if (denied(sender, "emakikits.reload", sub)) return;
                plugin.reloadContent();
                messages.send(sender, LogTag.RELOAD, "console.reload_complete");
            }
            case "check" -> {
                if (denied(sender, "emakikits.check", sub)) return;
                check(sender);
            }
            case "preview" -> {
                if (denied(sender, "emakikits.preview", sub)) return;
                if (args.length < 2) {
                    messages.send(sender, "general.usage_preview");
                    return;
                }
                preview(sender, args[1]);
            }
            case "edit" -> {
                if (denied(sender, "emakikits.edit", sub)) return;
                Player player = requirePlayer(sender);
                if (player == null) return;
                if (args.length < 2) {
                    plugin.editorService().openBrowser(player);
                    return;
                }
                plugin.editorService().open(player, args[1]);
            }
            case "top" -> {
                if (denied(sender, "emakikits.top", sub)) return;
                Player player = requirePlayer(sender);
                if (player == null) return;
                int page = 1;
                if (args.length >= 2) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException invalidPage) {
                        page = 1;
                    }
                }
                plugin.topService().open(player, page);
            }
            default -> messages.send(sender, "general.unknown_command");
        }
    }

    public Collection<String> suggest(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String top : TOP) {
                if (top.startsWith(prefix)) {
                    result.add(top);
                }
            }
            return result;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            if (KIT_ARG_SUBS.contains(sub)) {
                for (String id : config.get().kits().keySet()) {
                    if (id.startsWith(prefix)) {
                        result.add(id);
                    }
                }
                return result;
            }
            if ("grant".equals(sub) || "invite".equals(sub) || "inspect".equals(sub)) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        result.add(online.getName());
                    }
                }
                return result;
            }
            if ("cdk".equals(sub)) {
                for (String action : List.of("list", "disable")) {
                    if (action.startsWith(prefix)) {
                        result.add(action);
                    }
                }
                return result;
            }
            if ("top".equals(sub)) {
                return CommandTabHelper.completeIntRange(args[1], 1, 10);
            }
            return result;
        }
        if (args.length >= 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            if ("grant".equals(sub) && args.length == 3) {
                List<String> result = new ArrayList<>();
                for (String id : config.get().kits().keySet()) {
                    if (id.startsWith(prefix)) {
                        result.add(id);
                    }
                }
                return result;
            }
            if ("claim".equals(sub) && args.length == 3) {
                return "makeup".startsWith(prefix) ? List.of("makeup") : List.of();
            }
            if ("choose".equals(sub)) {
                KitDefinition def = config.get().kit(KitIds.normalize(args[1]));
                if (def == null) {
                    return List.of();
                }
                List<String> result = new ArrayList<>();
                for (ChoiceOption option : def.choice().options()) {
                    if (option.optionId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        result.add(option.optionId());
                    }
                }
                return result;
            }
        }
        return List.of();
    }

    private void sendHelpPage(CommandSender sender, List<String> path) {
        if (path.isEmpty()) {
            sendHelpLines(sender, "general.help_header",
                    "general.help_entry_list", "general.help_entry_claim", "general.help_entry_choose",
                    "general.help_entry_buy", "general.help_entry_redeem", "general.help_entry_invite",
                    "general.help_entry_open", "general.help_entry_inspect", "general.help_entry_grant",
                    "general.help_entry_cdk", "general.help_entry_reload", "general.help_entry_check",
                    "general.help_entry_preview", "general.help_entry_edit", "general.help_entry_top",
                    "general.help_entry_help");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(0).toLowerCase(Locale.ROOT)) {
            case "claim" -> sendKitHelp(sender, "general.help_claim_kit_header",
                    "general.help_value_desc_claim_kit", "claim");
            case "choose" -> sendKitHelp(sender, "general.help_choose_kit_header",
                    "general.help_value_desc_choose_kit", "choose");
            case "buy" -> sendKitHelp(sender, "general.help_buy_kit_header",
                    "general.help_value_desc_buy_kit", "buy");
            case "open" -> sendKitHelp(sender, "general.help_open_kit_header",
                    "general.help_value_desc_open_kit", "open");
            case "preview" -> sendKitHelp(sender, "general.help_preview_kit_header",
                    "general.help_value_desc_preview_kit", "preview");
            case "invite" -> sendPlayerHelp(sender, "invite",
                    "general.help_invite_player_header", "general.help_value_desc_invite_player");
            case "inspect" -> sendPlayerHelp(sender, "inspect",
                    "general.help_inspect_player_header", "general.help_value_desc_inspect_player");
            case "cdk" -> sendCdkHelp(sender);
            default -> sendHelpPage(sender, List.of());
        }
    }

    private void sendKitHelp(CommandSender sender, String headerKey, String descKey, String command) {
        sendHelpLine(sender, messages.message(headerKey, Map.of("root", HELP_ROOT)));
        List<String> kitIds = new ArrayList<>(config.get().kits().keySet());
        kitIds.sort(String::compareTo);
        if (kitIds.isEmpty()) {
            sendHelpLine(sender, messages.message("general.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String kitId : kitIds) {
            sendValueEntry(sender, kitId,
                    messages.message(descKey, Map.of("value", kitId)),
                    HELP_ROOT + " " + command + " " + kitId);
        }
        sendHelpFooter(sender);
    }

    private void sendPlayerHelp(CommandSender sender, String command, String headerKey, String descKey) {
        sendHelpLine(sender, messages.message(headerKey, Map.of("root", HELP_ROOT)));
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        names.sort(String::compareTo);
        if (names.isEmpty()) {
            sendHelpLine(sender, messages.message("general.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String name : names) {
            sendValueEntry(sender, name,
                    messages.message(descKey, Map.of("value", name)),
                    HELP_ROOT + " " + command + " " + name);
        }
        sendHelpFooter(sender);
    }

    private void sendCdkHelp(CommandSender sender) {
        sendHelpLines(sender, "general.help_cdk_header",
                "general.help_cdk_entry_list", "general.help_cdk_entry_disable");
        sendHelpFooter(sender);
    }

    private void sendValueEntry(CommandSender sender, String value, String desc, String run) {
        sendHelpLine(sender, messages.message("general.help_value_entry", Map.of(
                "value", value,
                "desc", desc,
                "run", run)));
    }

    private void sendHelpLines(CommandSender sender, String... keys) {
        Map<String, ?> replacements = Map.of("root", HELP_ROOT);
        for (String key : keys) {
            sendHelpLine(sender, messages.message(key, replacements));
        }
    }

    private void sendHelpLine(CommandSender sender, String line) {
        messages.sendComponent(sender, messages.render(line));
    }

    private void sendHelpFooter(CommandSender sender) {
        sendHelpLine(sender, messages.message("general.help_footer"));
    }

    private void listKits(CommandSender sender) {
        Map<String, KitDefinition> kits = config.get().kits();
        if (kits.isEmpty()) {
            messages.send(sender, "list.empty");
            return;
        }
        for (KitDefinition def : kits.values()) {
            messages.send(sender, "list.entry", Map.of("id", def.id(),
                    "type", def.type().name().toLowerCase(Locale.ROOT),
                    "display", def.displayName(), "enabled", def.enabled()));
        }
    }

    private void inspect(CommandSender sender, String rawName) {
        UUID id;
        String display;
        Player target = Bukkit.getPlayerExact(rawName);
        if (target != null) {
            id = target.getUniqueId();
            display = target.getName();
        } else {
            try {
                id = UUID.fromString(rawName);
                display = rawName;
            } catch (IllegalArgumentException notUuid) {
                org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(rawName);
                if (cached == null) {
                    messages.send(sender, "error.player.offline", Map.of("player", rawName));
                    return;
                }
                id = cached.getUniqueId();
                display = rawName;
            }
        }
        UUID playerId = id;
        List<KitState.AuditRecord> records = store.read(s -> s.audits().values().stream()
                .filter(a -> a.player().equals(playerId)).toList());
        messages.send(sender, "inspect.summary", Map.of("player", display,
                "audits", records.size()));
        for (KitState.AuditRecord record : records) {
            messages.send(sender, "inspect.line", Map.of("id", record.id(),
                    "action", record.action(), "detail", record.detail()));
        }
    }

    private void check(CommandSender sender) {
        AppConfig cfg = config.get();
        messages.send(sender, "check.header", Map.of("version", cfg.version()));
        for (Map.Entry<String, List<String>> entry : plugin.skippedKits().entrySet()) {
            messages.send(sender, "check.skipped_line", Map.of("file", entry.getKey(),
                    "reason", String.join(", ", entry.getValue())));
        }
        for (KitDefinition def : cfg.kits().values()) {
            messages.send(sender, "check.kit_line", Map.of("id", def.id(),
                    "type", def.type().name().toLowerCase(Locale.ROOT),
                    "enabled", String.valueOf(def.enabled())));
            if (def.hasTimeLimit()) {
                TimeWindow window = service.timeWindow(def);
                boolean active = window.contains(System.currentTimeMillis());
                messages.send(sender, "check.window_line", Map.of("kit", def.id(),
                        "mode", def.limit().mode(), "active", String.valueOf(active)));
            }
            if (def.isCyclic()) {
                messages.send(sender, "check.switch_line", Map.of(
                        "name", def.id() + ".cycle.allow_makeup",
                        "value", String.valueOf(def.cycle().allowMakeup())));
            }
            if (def.type() == KitType.CDK) {
                messages.send(sender, "check.switch_line", Map.of(
                        "name", def.id() + ".cdk.per_player_limit",
                        "value", String.valueOf(def.cdk().perPlayerLimit())));
            }
            if (def.type() == KitType.INVITE) {
                messages.send(sender, "check.switch_line", Map.of(
                        "name", def.id() + ".invite.max_binds_per_invitee",
                        "value", String.valueOf(def.invite().maxBindsPerInvitee())));
            }
        }
        messages.send(sender, "check.switch_line", Map.of(
                "name", "invite.max_binds_per_invitee",
                "value", String.valueOf(cfg.invite().maxBindsPerInvitee())));
        long idempotency = store.read(s -> (long) s.idempotencyKeys().size());
        long audits = store.read(s -> (long) s.audits().size());
        long counts = store.read(s -> (long) (s.claimTotals().size() + s.purchaseCounts().size()
                + s.openCounts().size() + s.cdkRedeemCounts().size() + s.pityCounts().size()));
        long binds = store.read(s -> (long) s.inviteBinds().size());
        messages.send(sender, "check.store_line", Map.of("idempotency", idempotency,
                "audits", audits, "counts", counts, "binds", binds));
        messages.send(sender, "check.retention_line", Map.of(
                "audit_days", cfg.storage().retention().auditDays(),
                "idempotency_days", cfg.storage().retention().idempotencyDays()));
        org.bukkit.plugin.Plugin coreLib = Bukkit.getPluginManager().getPlugin("EmakiCoreLib");
        messages.send(sender, "check.corelib_line", Map.of("status",
                coreLib == null ? "missing" : coreLib.getPluginMeta().getVersion()));
    }

    private void preview(CommandSender sender, String rawKitId) {
        String kitId = KitIds.normalize(rawKitId);
        KitDefinition def = config.get().kit(kitId);
        if (def == null) {
            messages.send(sender, "error.kit.unknown", Map.of("kit", kitId));
            return;
        }
        messages.send(sender, "preview.header", Map.of("id", def.id(),
                "display", def.displayName(),
                "type", def.type().name().toLowerCase(Locale.ROOT)));
        if (!def.fixedRewards().entries().isEmpty()) {
            messages.send(sender, "preview.fixed_header", Map.of());
            for (RewardEntry entry : def.fixedRewards().entries()) {
                previewEntry(sender, entry);
            }
        }
        if (!def.randomRewards().entries().isEmpty()) {
            messages.send(sender, "preview.random_header", Map.of());
            for (RewardEntry entry : def.randomRewards().entries()) {
                previewEntry(sender, entry);
            }
            if (def.randomRewards().pity() != null && def.randomRewards().pity().valid()) {
                messages.send(sender, "preview.pity_line", Map.of(
                        "count", def.randomRewards().pity().count(),
                        "guaranteed", def.randomRewards().pity().guaranteedId()));
            }
        }
        for (ChoiceOption option : def.choice().options()) {
            messages.send(sender, "preview.option_line", Map.of("id", option.optionId(),
                    "display", option.display()));
        }
        if (def.hasPurchase()) {
            messages.send(sender, "preview.purchase_line", Map.of("price", def.purchase().price(),
                    "max", def.purchase().maxPurchases()));
        }
        if (def.hasOpenCount()) {
            messages.send(sender, "preview.open_line", Map.of("count", def.open().milestones().size()));
        }
        if (def.hasTimeLimit()) {
            messages.send(sender, "preview.window_line", Map.of("mode", def.limit().mode()));
        }
    }

    private void previewEntry(CommandSender sender, RewardEntry entry) {
        switch (entry.kind()) {
            case ITEM -> messages.send(sender, "preview.reward_item", Map.of("id", entry.id(),
                    "source", entry.source(), "amount", entry.amount()));
            case MONEY -> messages.send(sender, "preview.reward_money", Map.of("id", entry.id(),
                    "amount", entry.amount()));
            case NESTED_KIT -> messages.send(sender, "preview.reward_nested", Map.of("id", entry.id(),
                    "kit", entry.nestedKitId()));
            case ACTION -> messages.send(sender, "preview.reward_action", Map.of("id", entry.id(),
                    "action", entry.action()));
        }
    }

    private void grant(CommandSender sender, String rawName, String rawKitId) {
        Player target = Bukkit.getPlayerExact(rawName);
        if (target == null) {
            messages.send(sender, "error.player.offline", Map.of("player", rawName));
            return;
        }
        String kitId = KitIds.normalize(rawKitId);
        KitDefinition def = config.get().kit(kitId);
        if (def == null) {
            messages.send(sender, "error.kit.unknown", Map.of("kit", kitId));
            return;
        }
        store.mutate(s -> s.purchaseCounts().merge(
                KitState.countKey("purchase", kitId, target.getUniqueId()), 1L, Long::sum));
        messages.send(sender, "grant.success", Map.of("player", target.getName(), "kit", kitId));
    }

    private void cdk(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "general.usage_cdk");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                for (KitDefinition def : config.get().kits().values()) {
                    if (def.type() == KitType.CDK) {
                        messages.send(sender, "cdk.list_line", Map.of("kit", def.id(),
                                "mode", def.cdk().mode().name().toLowerCase(Locale.ROOT),
                                "codes", def.cdk().codes().size()));
                    }
                }
            }
            case "disable" -> {
                if (args.length < 3) {
                    messages.send(sender, "general.usage_cdk");
                    return;
                }
                String code = config.get().cdk().caseSensitive() ? args[2] : args[2].toLowerCase(Locale.ROOT);
                store.mutate(s -> s.cdkDisabled().add(code));
                messages.send(sender, "cdk.disabled", Map.of("code", code));
            }
            default -> messages.send(sender, "general.usage_cdk");
        }
    }

    private void report(CommandSender sender, ClaimResult result) {
        Map<String, ?> vars = Map.of("kit", result.kitId(), "period", result.periodKey());
        if (result.success()) {
            messages.send(sender, "claim.success", vars);
            if (sender instanceof org.bukkit.entity.Player player) {
                KitClaimView view = new KitClaimView(result.kitId(), result.periodKey(),
                        true, result.reasonKey(), result.auditIds());
                Bukkit.getPluginManager().callEvent(new KitClaimedEvent(player, view, "claim"));
            }
        } else {
            messages.send(sender, result.reasonKey(), vars);
            if (result.status() == ClaimResult.Status.ALREADY_CLAIMED
                    || result.status() == ClaimResult.Status.CDK_EXHAUSTED) {
                String who = sender instanceof Player player ? player.getName() : "console";
                LogTags.send(plugin, KitsLogTag.KITS, Level.INFO, "console.duplicate_claim_blocked",
                        Map.of("player", who, "kit", result.kitId()));
            }
        }
    }

    private boolean denied(CommandSender sender, String permission, String sub) {
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "error.permission.denied", Map.of("sub", sub));
            return true;
        }
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player_only");
            return null;
        }
        return player;
    }
}
