package emaki.jiuwu.craft.dungeon.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputRequest;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputResult;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.audit.AuditService;
import emaki.jiuwu.craft.dungeon.challenge.ChallengeService;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.editor.EditorService;
import emaki.jiuwu.craft.dungeon.flow.FlowRuntime;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.isolation.MessageIsolation;
import emaki.jiuwu.craft.dungeon.leaderboard.LeaderboardStore;
import emaki.jiuwu.craft.dungeon.limit.LimitService;
import emaki.jiuwu.craft.dungeon.match.MatchmakingService;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceCoordinator;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceSlotAllocator;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.recovery.RecoveryService;
import emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxGui;
import emaki.jiuwu.craft.dungeon.snapshot.SnapshotManager;
import emaki.jiuwu.craft.dungeon.state.PlayerStateService;
import emaki.jiuwu.craft.dungeon.world.TemplateRepository;
import emaki.jiuwu.craft.dungeon.world.WorldPool;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;
import java.util.function.Function;
import org.bukkit.plugin.Plugin;

final class DungeonCommandRouterDelegate {

    private final DungeonCommandRouter ownerRef;

    DungeonCommandRouterDelegate(DungeonCommandRouter ownerRef) {
        this.ownerRef = ownerRef;
    }

    void bindEditor(EditorService editor) {
        ownerRef.editor = editor;
    }

    void bindExitService(emaki.jiuwu.craft.dungeon.exit.InstanceExitService exitService) {
        ownerRef.exitService = exitService;
    }

    void bindRewardBox(RewardBoxGui rewardBoxGui) {
        ownerRef.rewardBoxGui = rewardBoxGui;
    }

    void bindOperations(LimitService limitService, LeaderboardStore leaderboard, Function<String, DungeonConfig> configOf) {
        ownerRef.limitService = limitService;
        ownerRef.leaderboard = leaderboard;
        ownerRef.configOf = configOf;
    }

    void bindMatchmaking(MatchmakingService matchmaking) {
        ownerRef.matchmaking = matchmaking;
    }

    void bindStateService(PlayerStateService stateService) {
        ownerRef.stateService = stateService;
    }

    void bindAdmin(WorldPool pool, InstanceSlotAllocator allocator, Runnable reloadAction) {
        ownerRef.adminPool = pool;
        ownerRef.adminAllocator = allocator;
        ownerRef.adminReload = reloadAction;
    }

    void bindAudit(AuditService audit) {
        ownerRef.audit = audit;
    }

    void adminInstances(CommandSender sender) {
        var instances = ownerRef.registry.all();
        ownerRef.messages.send(sender, "command.admin.instances_header",
                Map.of("count", String.valueOf(instances.size())));
        for (DungeonInstance instance : instances) {
            String queue = instance.commands() == null ? "0" : String.valueOf(instance.commands().pending());
            ownerRef.messages.send(sender, "command.admin.instance_line", Map.of(
                    "instance", instance.instanceId(),
                    "dungeon", instance.dungeonId(),
                    "state", instance.state().name(),
                    "party", instance.partyId() == null
                            ? "-" : instance.partyId().toString().substring(0, 8),
                    "queue", queue));
        }
    }

    void adminClearSlots(CommandSender sender) {
        if (ownerRef.adminAllocator == null) {
            ownerRef.messages.send(sender, "command.admin.reload_failed", Map.of("error", "unbound"));
            return;
        }
        ownerRef.adminAllocator.reset();
        if (ownerRef.audit != null) {
            ownerRef.audit.write("admin", Map.of("action", "clearslots", "sender", sender.getName()));
        }
        ownerRef.messages.send(sender, "command.admin.slots_cleared", Map.of());
    }

    void routeList(CommandSender sender) {
        List<String> dungeons = ownerRef.templates.discoverDungeons();
        if (dungeons.isEmpty()) {
            ownerRef.messages.send(sender, "command.list.empty");
            return;
        }
        ownerRef.messages.send(sender, "command.list.header");
        for (String dungeonId : dungeons) {
            ownerRef.messages.send(sender, "command.list.entry", Map.of("dungeon", dungeonId));
        }
    }

    void routeEnter(Player player, String[] args) {
        if (args.length < 2 || Texts.isBlank(args[1])) {
            ownerRef.messages.send(player, "command.enter.usage");
            return;
        }
        ownerRef.challenge.startEnter(player, args[1]);
    }

    boolean noOnlineMemberInside(DungeonInstance instance) {
        UUID partyId = instance.partyId();
        if (partyId == null) {
            return true;
        }
        Party party = ownerRef.parties.partyById(partyId);
        if (party == null) {
            return true;
        }
        for (UUID memberId : party.members()) {
            if (Bukkit.getPlayer(memberId) == null) {
                continue;
            }
            String pointer = ownerRef.snapshots.pointerOf(memberId);
            if (instance.instanceId().equals(pointer)) {
                return false;
            }
        }
        return true;
    }

    void routeRank(Player player, String[] args) {
        if (ownerRef.leaderboard == null) {
            ownerRef.messages.send(player, "general.invalid_args", Map.of());
            return;
        }
        if (args.length < 2 || Texts.isBlank(args[1])) {
            ownerRef.messages.send(player, "command.rank.usage");
            return;
        }
        String dungeonId = args[1];
        var entries = ownerRef.leaderboard.top(dungeonId, 10);
        if (entries.isEmpty()) {
            ownerRef.messages.send(player, "command.rank.empty", Map.of("dungeon", dungeonId));
            return;
        }
        ownerRef.messages.send(player, "command.rank.header", Map.of("dungeon", dungeonId));
        int place = 1;
        for (var entry : entries) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < entry.members().size(); i++) {
                if (i > 0) {
                    names.append(", ");
                }
                names.append(entry.members().get(i).name());
            }
            ownerRef.messages.send(player, "command.rank.entry", Map.of(
                    "place", String.valueOf(place++),
                    "score", String.valueOf(entry.score()),
                    "duration", DungeonCommandRouterSupport.formatDuration(entry.durationMs()),
                    "difficulty", entry.difficulty(),
                    "members", names.toString()
            ));
        }
    }

    void routeGlobalChat(Player player, String[] args) {
        if (args.length < 2) {
            ownerRef.messages.send(player, "command.globalchat.usage");
            return;
        }
        String sub = Texts.lower(args[1]);
        boolean current = ownerRef.isolation.globalListen(player.getUniqueId());
        boolean target = switch (sub) {
            case "on" -> true;
            case "off" -> false;
            default -> current;
        };
        if (!"on".equals(sub) && !"off".equals(sub)) {
            ownerRef.messages.send(player, "command.globalchat.usage");
            return;
        }
        if (current != target) {
            ownerRef.isolation.toggleGlobal(player);
        }
        ownerRef.isolation.sendToggleFeedback(player, target);
    }

    void routePartyInvite(Player player, String[] args) {
        if (args.length < 3 || Texts.isBlank(args[2])) {
            ownerRef.messages.send(player, "command.party.usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            ownerRef.messages.send(player, "party.result.invalid_target", Map.of("player", args[2]));
            return;
        }
        PartyService.InviteResult result = ownerRef.parties.invite(player, target.getUniqueId());
        if (result == PartyService.InviteResult.OK) {
            ownerRef.messages.send(player, "party.invite_sent", Map.of("player", target.getName()));
            ownerRef.messages.send(target, "party.invite_received", Map.of("player", player.getName()));
            return;
        }
        ownerRef.sendPartyResult(player, result);
    }

    void routePartyKick(Player player, String[] args) {
        if (args.length < 3 || Texts.isBlank(args[2])) {
            ownerRef.messages.send(player, "command.party.usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        UUID targetId = target != null ? target.getUniqueId() : ownerRef.parseUuid(args[2]);
        if (targetId == null) {
            ownerRef.messages.send(player, "party.result.invalid_target", Map.of("player", args[2]));
            return;
        }
        ownerRef.sendPartyResult(player, ownerRef.parties.kick(player, targetId));
    }

    UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    void sendPartyResult(Player player, PartyService.InviteResult result) {
        if (result == PartyService.InviteResult.OK) {
            return;
        }
        String key = "party.result." + Texts.lower(result.name());
        ownerRef.messages.send(player, key, Map.of());
    }

}
