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
import emaki.jiuwu.craft.dungeon.challenge.ChallengeService;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceCoordinator;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.recovery.RecoveryService;
import emaki.jiuwu.craft.dungeon.snapshot.SnapshotManager;
import emaki.jiuwu.craft.dungeon.world.TemplateRepository;

public final class DungeonCommandRouter {

    private static final List<String> ROOT_SUBS = List.of(
            "help", "list", "enter", "leave", "cancel", "ready", "rewards",
            "party", "stats", "rank", "globalchat", "editor", "admin"
    );
    private static final List<String> PARTY_SUBS = List.of(
            "create", "invite", "accept", "deny", "kick", "leave", "disband", "chat"
    );
    private static final List<String> EDITOR_SUBS = List.of("exit", "pos1", "pos2", "mark", "marks", "test", "fill");

    private final org.bukkit.plugin.Plugin plugin;
    private final MessageService messages;
    private final ChallengeService challenge;
    private final PartyService parties;
    private final RecoveryService recovery;
    private final SnapshotManager snapshots;
    private final ChatInputService chatInput;
    private final TemplateRepository templates;
    private final InstanceCoordinator coordinator;
    private final InstanceRegistry registry;
    private final emaki.jiuwu.craft.dungeon.isolation.MessageIsolation isolation;
    private volatile emaki.jiuwu.craft.dungeon.limit.LimitService limitService;
    private volatile emaki.jiuwu.craft.dungeon.leaderboard.LeaderboardStore leaderboard;
    private volatile java.util.function.Function<String, emaki.jiuwu.craft.dungeon.config.DungeonConfig> configOf;
    private volatile emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxGui rewardBoxGui;
    private volatile emaki.jiuwu.craft.dungeon.editor.EditorService editor;

    public void bindEditor(emaki.jiuwu.craft.dungeon.editor.EditorService editor) {
        this.editor = editor;
    }

    public void bindRewardBox(emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxGui rewardBoxGui) {
        this.rewardBoxGui = rewardBoxGui;
    }

    public void bindOperations(emaki.jiuwu.craft.dungeon.limit.LimitService limitService,
            emaki.jiuwu.craft.dungeon.leaderboard.LeaderboardStore leaderboard,
            java.util.function.Function<String, emaki.jiuwu.craft.dungeon.config.DungeonConfig> configOf) {
        this.limitService = limitService;
        this.leaderboard = leaderboard;
        this.configOf = configOf;
    }

    public DungeonCommandRouter(org.bukkit.plugin.Plugin plugin,
            MessageService messages,
            ChallengeService challenge,
            PartyService parties,
            RecoveryService recovery,
            SnapshotManager snapshots,
            ChatInputService chatInput,
            TemplateRepository templates,
            InstanceCoordinator coordinator,
            InstanceRegistry registry,
            emaki.jiuwu.craft.dungeon.isolation.MessageIsolation isolation) {
        this.plugin = plugin;
        this.messages = messages;
        this.challenge = challenge;
        this.parties = parties;
        this.recovery = recovery;
        this.snapshots = snapshots;
        this.chatInput = chatInput;
        this.templates = templates;
        this.coordinator = coordinator;
        this.registry = registry;
        this.isolation = isolation;
    }

    public void route(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            messages.send(sender, "command.help");
            return;
        }
        String sub = args[0];
        if ("list".equalsIgnoreCase(sub)) {
            routeList(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player_only");
            return;
        }
        switch (Texts.lower(sub)) {
            case "enter" -> routeEnter(player, args);
            case "leave" -> routeLeave(player);
            case "cancel" -> challenge.cancelBy(player, "member_cancel");
            case "ready" -> challenge.readyBy(player);
            case "party" -> routeParty(player, args);
            case "stats" -> routeStats(player, args);
            case "rank" -> routeRank(player, args);
            case "rewards" -> {
                if (rewardBoxGui != null) {
                    if (!rewardBoxGui.serviceEnabled()) {
                        messages.send(player, "rewardbox.disabled", Map.of());
                    } else {
                        rewardBoxGui.open(player, 0);
                    }
                }
            }
            case "globalchat" -> routeGlobalChat(player, args);
            case "editor" -> routeEditor(player, args);
            default -> messages.send(sender, "command.unknown", Map.of("command", sub));
        }
    }

    private void routeList(CommandSender sender) {
        List<String> dungeons = templates.discoverDungeons();
        if (dungeons.isEmpty()) {
            messages.send(sender, "command.list.empty");
            return;
        }
        messages.send(sender, "command.list.header");
        for (String dungeonId : dungeons) {
            messages.send(sender, "command.list.entry", Map.of("dungeon", dungeonId));
        }
    }

    private void routeEnter(Player player, String[] args) {
        if (args.length < 2 || Texts.isBlank(args[1])) {
            messages.send(player, "command.enter.usage");
            return;
        }
        challenge.startEnter(player, args[1]);
    }

    private void routeLeave(Player player) {
        String pointer = snapshots.pointerOf(player.getUniqueId());
        DungeonInstance instance = pointer == null ? null : registry.get(pointer);
        if (pointer == null || instance == null) {
            messages.send(player, "command.leave.no_instance");
            return;
        }
        String instanceId = pointer;
        messages.send(player, "command.leave.confirm_hint");
        chatInput.await(new ChatInputRequest(plugin, player, 30L, List.of("取消", "cancel"), result -> {
            if (!result.submitted()) {
                return;
            }
            String text = Texts.lower(MiniMessages.plainText(result.text()));
            if (!"确认".equals(text) && !"confirm".equals(text) && !"yes".equals(text) && !"y".equals(text)) {
                messages.send(player, "command.leave.aborted");
                return;
            }
            completeLeave(player, instanceId);
        }));
    }

    private void completeLeave(Player player, String instanceId) {
        UUID playerId = player.getUniqueId();
        Party party = parties.partyOf(playerId);
        boolean soleMember = party != null && party.size() == 1;
        UUID partyId = party == null ? null : party.id();
        recovery.onExit(player, "voluntary");
        if (party != null) {
            parties.leave(player);
        }
        if (partyId != null) {
            challenge.onPartyChanged(partyId);
        }
        DungeonInstance instance = registry.get(instanceId);
        if (instance != null && (soleMember || noOnlineMemberInside(instance))) {
            coordinator.dispose(instanceId, "all_left");
        }
    }

    private boolean noOnlineMemberInside(DungeonInstance instance) {
        UUID partyId = instance.partyId();
        if (partyId == null) {
            return true;
        }
        Party party = parties.partyById(partyId);
        if (party == null) {
            return true;
        }
        for (UUID memberId : party.members()) {
            if (Bukkit.getPlayer(memberId) == null) {
                continue;
            }
            String pointer = snapshots.pointerOf(memberId);
            if (instance.instanceId().equals(pointer)) {
                return false;
            }
        }
        return true;
    }

    private void routeStats(Player player, String[] args) {
        if (limitService == null || leaderboard == null || configOf == null) {
            messages.send(player, "general.invalid_args", Map.of());
            return;
        }
        Player target = player;
        if (args.length >= 2 && !Texts.isBlank(args[1])) {
            Player requested = Bukkit.getPlayerExact(args[1]);
            if (requested == null) {
                messages.send(player, "party.result.invalid_target", Map.of("player", args[1]));
                return;
            }
            target = requested;
        }
        List<String> dungeons = templates.discoverDungeons();
        if (args.length >= 3 && !Texts.isBlank(args[2])) {
            dungeons = List.of(args[2]);
        }
        if (dungeons.isEmpty()) {
            messages.send(player, "command.list.empty");
            return;
        }
        messages.send(player, "command.stats.header", Map.of("player", target.getName()));
        for (String dungeonId : dungeons) {
            if (configOf.apply(dungeonId) == null) {
                continue;
            }
            Map<String, Object> status = limitService.status(target.getUniqueId(), dungeonId);
            int dailyTotal = number(status.get("daily-total"));
            int dailyUsed = number(status.get("daily-used"));
            int weeklyTotal = number(status.get("weekly-total"));
            int weeklyUsed = number(status.get("weekly-used"));
            int monthlyTotal = number(status.get("monthly-total"));
            int monthlyUsed = number(status.get("monthly-used"));
            long cooldown = ((Number) status.getOrDefault("cooldown-seconds", 0L)).longValue();
            StringBuilder limits = new StringBuilder();
            if (dailyTotal > 0) {
                limits.append(messages.message("command.stats.limit_daily", Map.of(
                        "used", String.valueOf(dailyUsed),
                        "total", String.valueOf(dailyTotal)))).append(' ');
            }
            if (weeklyTotal > 0) {
                limits.append(messages.message("command.stats.limit_weekly", Map.of(
                        "used", String.valueOf(weeklyUsed),
                        "total", String.valueOf(weeklyTotal)))).append(' ');
            }
            if (monthlyTotal > 0) {
                limits.append(messages.message("command.stats.limit_monthly", Map.of(
                        "used", String.valueOf(monthlyUsed),
                        "total", String.valueOf(monthlyTotal)))).append(' ');
            }
            if (cooldown > 0) {
                limits.append(messages.message("command.stats.limit_cooldown", Map.of(
                        "seconds", String.valueOf(cooldown))));
            }
            if (limits.isEmpty()) {
                limits.append(messages.message("command.stats.no_limits", Map.of()));
            }
            messages.send(player, "command.stats.entry", Map.of(
                    "dungeon", dungeonId,
                    "limits", limits.toString()
            ));
            var best = leaderboard.personalBest(dungeonId, target.getName());
            if (!best.isEmpty()) {
                var entry = best.get(0);
                messages.send(player, "command.stats.best", Map.of(
                        "dungeon", dungeonId,
                        "score", String.valueOf(entry.score()),
                        "duration", formatDuration(entry.durationMs())
                ));
            }
        }
    }

    private void routeRank(Player player, String[] args) {
        if (leaderboard == null) {
            messages.send(player, "general.invalid_args", Map.of());
            return;
        }
        if (args.length < 2 || Texts.isBlank(args[1])) {
            messages.send(player, "command.rank.usage");
            return;
        }
        String dungeonId = args[1];
        var entries = leaderboard.top(dungeonId, 10);
        if (entries.isEmpty()) {
            messages.send(player, "command.rank.empty", Map.of("dungeon", dungeonId));
            return;
        }
        messages.send(player, "command.rank.header", Map.of("dungeon", dungeonId));
        int place = 1;
        for (var entry : entries) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < entry.members().size(); i++) {
                if (i > 0) {
                    names.append(", ");
                }
                names.append(entry.members().get(i).name());
            }
            messages.send(player, "command.rank.entry", Map.of(
                    "place", String.valueOf(place++),
                    "score", String.valueOf(entry.score()),
                    "duration", formatDuration(entry.durationMs()),
                    "difficulty", entry.difficulty(),
                    "members", names.toString()
            ));
        }
    }

    private static String formatDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void routeGlobalChat(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "command.globalchat.usage");
            return;
        }
        String sub = Texts.lower(args[1]);
        boolean current = isolation.globalListen(player.getUniqueId());
        boolean target = switch (sub) {
            case "on" -> true;
            case "off" -> false;
            default -> current;
        };
        if (!"on".equals(sub) && !"off".equals(sub)) {
            messages.send(player, "command.globalchat.usage");
            return;
        }
        if (current != target) {
            isolation.toggleGlobal(player);
        }
        isolation.sendToggleFeedback(player, target);
    }

    private void routeEditor(Player player, String[] args) {
        if (editor == null) {
            messages.send(player, "general.invalid_args", Map.of());
            return;
        }
        if (!player.hasPermission("emakidungeon.editor")) {
            messages.send(player, "general.no_permission", Map.of());
            return;
        }
        if (editor.hasSession(player.getUniqueId())) {
            String sub = args.length >= 2 && !Texts.isBlank(args[1]) ? Texts.lower(args[1]) : null;
            switch (sub == null ? "" : sub) {
                case "exit" -> {
                    String pointer = snapshots.pointerOf(player.getUniqueId());
                    if (pointer != null && registry.get(pointer) != null) {
                        messages.send(player, "editor.instance_active", Map.of());
                        return;
                    }
                    editor.exit(player);
                }
                case "pos1" -> editor.markPos(player, 1);
                case "pos2" -> editor.markPos(player, 2);
                case "mark" -> {
                    if (args.length < 3 || Texts.isBlank(args[2])) {
                        messages.send(player, "editor.mark_usage", Map.of());
                        return;
                    }
                    editor.mark(player, args[2], args.length >= 4 ? args[3] : null);
                }
                case "marks" -> editor.listMarks(player);
                case "test" -> {
                    String pointer = snapshots.pointerOf(player.getUniqueId());
                    if (pointer != null && registry.get(pointer) != null) {
                        messages.send(player, "editor.instance_active", Map.of());
                        return;
                    }
                    editor.startTest(player);
                }
                case "fill" -> {
                    if (args.length < 3 || Texts.isBlank(args[2])) {
                        messages.send(player, "editor.fill_usage", Map.of());
                        return;
                    }
                    editor.fill(player, args[2]);
                }
                default -> messages.send(player, "editor.usage_session", Map.of());
            }
            return;
        }
        if (args.length < 2 || Texts.isBlank(args[1])) {
            messages.send(player, "editor.usage", Map.of());
            return;
        }
        String pointer = snapshots.pointerOf(player.getUniqueId());
        if (pointer != null && registry.get(pointer) != null) {
            messages.send(player, "editor.in_instance", Map.of());
            return;
        }
        if (challenge.sessionContains(player.getUniqueId())) {
            messages.send(player, "editor.busy_challenge", Map.of());
            return;
        }
        editor.open(player, args[1]);
    }

    private void routeParty(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "command.party.usage");
            return;
        }
        String sub = Texts.lower(args[1]);
        switch (sub) {
            case "create" -> sendPartyResult(player, parties.create(player));
            case "invite" -> routePartyInvite(player, args);
            case "accept" -> sendPartyResult(player, parties.accept(player));
            case "deny" -> sendPartyResult(player, parties.deny(player));
            case "kick" -> routePartyKick(player, args);
            case "leave" -> {
                Party party = parties.partyOf(player.getUniqueId());
                UUID partyId = party == null ? null : party.id();
                sendPartyResult(player, parties.leave(player));
                if (partyId != null) {
                    challenge.onPartyChanged(partyId);
                }
            }
            case "disband" -> sendPartyResult(player, parties.disband(player));
            case "chat" -> {
                boolean enabled = parties.toggleChatChannel(player);
                messages.send(player, enabled ? "party.chat_enabled" : "party.chat_disabled");
            }
            default -> messages.send(player, "command.party.usage");
        }
    }

    private void routePartyInvite(Player player, String[] args) {
        if (args.length < 3 || Texts.isBlank(args[2])) {
            messages.send(player, "command.party.usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages.send(player, "party.result.invalid_target", Map.of("player", args[2]));
            return;
        }
        PartyService.InviteResult result = parties.invite(player, target.getUniqueId());
        if (result == PartyService.InviteResult.OK) {
            messages.send(player, "party.invite_sent", Map.of("player", target.getName()));
            messages.send(target, "party.invite_received", Map.of("player", player.getName()));
            return;
        }
        sendPartyResult(player, result);
    }

    private void routePartyKick(Player player, String[] args) {
        if (args.length < 3 || Texts.isBlank(args[2])) {
            messages.send(player, "command.party.usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        UUID targetId = target != null ? target.getUniqueId() : parseUuid(args[2]);
        if (targetId == null) {
            messages.send(player, "party.result.invalid_target", Map.of("player", args[2]));
            return;
        }
        sendPartyResult(player, parties.kick(player, targetId));
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void sendPartyResult(Player player, PartyService.InviteResult result) {
        if (result == PartyService.InviteResult.OK) {
            return;
        }
        String key = "party.result." + Texts.lower(result.name());
        messages.send(player, key, Map.of());
    }

    public @NotNull Collection<String> suggest(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length <= 1) {
            List<String> visible = new ArrayList<>(ROOT_SUBS.size());
            for (String sub : ROOT_SUBS) {
                if ("editor".equals(sub) && !sender.hasPermission("emakidungeon.editor")) {
                    continue;
                }
                if ("admin".equals(sub) && !sender.hasPermission("emakidungeon.admin")) {
                    continue;
                }
                visible.add(sub);
            }
            return visible;
        }
        String root = Texts.lower(args[0]);
        if ("enter".equals(root) && args.length == 2) {
            return templates.discoverDungeons();
        }
        if ("editor".equals(root) && sender.hasPermission("emakidungeon.editor")) {
            if (args.length == 2) {
                if (editor != null && sender instanceof Player player
                        && editor.hasSession(player.getUniqueId())) {
                    return EDITOR_SUBS;
                }
                return templates.discoverDungeons();
            }
            if (args.length == 3 && "mark".equals(Texts.lower(args[1]))) {
                return emaki.jiuwu.craft.dungeon.editor.EditorService.markTypes();
            }
        }
        if ("party".equals(root)) {
            if (args.length == 2) {
                return PARTY_SUBS;
            }
            String partySub = Texts.lower(args[1]);
            if (("invite".equals(partySub) || "kick".equals(partySub)) && args.length == 3) {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    names.add(online.getName());
                }
                return names;
            }
        }
        return List.of();
    }
}
