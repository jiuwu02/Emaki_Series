package emaki.jiuwu.craft.kits.command;

import emaki.jiuwu.craft.kits.EmakiKitsPlugin;
import emaki.jiuwu.craft.kits.config.AppConfig;
import emaki.jiuwu.craft.kits.config.KitIds;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.kits.model.ClaimResult;
import emaki.jiuwu.craft.kits.model.KitDefinition;
import emaki.jiuwu.craft.kits.model.KitType;
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

public final class KitCommandRouter {

    private static final List<String> TOP = List.of("help", "list", "claim", "choose", "buy",
            "redeem", "invite", "open", "inspect", "grant", "cdk", "reload");
    private static final List<String> KIT_ARG_SUBS = List.of(
            "claim", "choose", "buy", "redeem", "open", "grant");

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
                messages.send(sender, "help.lines");
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
                report(sender, service.claim(player, args[1]));
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
                messages.send(sender, "console.reload_complete");
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
            if (KIT_ARG_SUBS.contains(sub)) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                List<String> result = new ArrayList<>();
                for (String id : config.get().kits().keySet()) {
                    if (id.startsWith(prefix)) {
                        result.add(id);
                    }
                }
                return result;
            }
        }
        return List.of();
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
        Player target = Bukkit.getPlayerExact(rawName);
        if (target == null) {
            messages.send(sender, "error.player.offline", Map.of("player", rawName));
            return;
        }
        UUID id = target.getUniqueId();
        List<KitState.AuditRecord> records = store.read(s -> s.audits().values().stream()
                .filter(a -> a.player().equals(id)).toList());
        messages.send(sender, "inspect.summary", Map.of("player", target.getName(),
                "audits", records.size()));
        for (KitState.AuditRecord record : records) {
            messages.send(sender, "inspect.line", Map.of("id", record.id(),
                    "action", record.action(), "detail", record.detail()));
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
