package emaki.jiuwu.craft.dungeon.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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

public final class DungeonCommandRouter {

    private static final List<String> ROOT_SUBS = List.of(
            "help", "list", "enter", "leave", "cancel", "ready", "rewards",
            "party", "stats", "rank", "globalchat", "editor", "admin", "recruit", "match", "spectate", "random"
    );
    private static final List<String> PARTY_SUBS = List.of(
            "create", "invite", "accept", "deny", "kick", "leave", "disband", "chat"
    );
    private static final List<String> EDITOR_SUBS = List.of("menu", "save", "exit", "pos1", "pos2", "mark", "marks", "test", "fill");
    private static final List<String> RECRUIT_SUBS = List.of("open", "close", "list", "join");
    private static final List<String> MATCH_SUBS = List.of("join", "leave");
    private static final List<String> SPECTATE_SUBS = List.of("next", "prev");
    private static final List<String> ADMIN_SUBS = List.of(
            "instances", "inspect", "stop", "status", "reload", "validate", "poolreset", "clearslots"
    );

    private final Plugin plugin;
    final MessageService messages;
    final ChallengeService challenge;
    final PartyService parties;
    private final RecoveryService recovery;
    final SnapshotManager snapshots;
    private final ChatInputService chatInput;
    final TemplateRepository templates;
    private final InstanceCoordinator coordinator;
    final InstanceRegistry registry;
    final MessageIsolation isolation;
    volatile LimitService limitService;
    volatile LeaderboardStore leaderboard;
    volatile Function<String, DungeonConfig> configOf;
    volatile RewardBoxGui rewardBoxGui;
    volatile EditorService editor;
    volatile emaki.jiuwu.craft.dungeon.exit.InstanceExitService exitService;

    public void bindEditor(EditorService editor) { new DungeonCommandRouterDelegate(this).bindEditor(editor); }

    public void bindExitService(emaki.jiuwu.craft.dungeon.exit.InstanceExitService exitService) { new DungeonCommandRouterDelegate(this).bindExitService(exitService); }

    public void bindRewardBox(RewardBoxGui rewardBoxGui) { new DungeonCommandRouterDelegate(this).bindRewardBox(rewardBoxGui); }

    public void bindOperations(LimitService limitService,
            LeaderboardStore leaderboard,
            Function<String, DungeonConfig> configOf) { new DungeonCommandRouterDelegate(this).bindOperations(limitService, leaderboard, configOf); }

    volatile WorldPool adminPool;
    volatile InstanceSlotAllocator adminAllocator;
    volatile Runnable adminReload;
    volatile AuditService audit;
    volatile MatchmakingService matchmaking;
    volatile PlayerStateService stateService;

    public void bindMatchmaking(MatchmakingService matchmaking) { new DungeonCommandRouterDelegate(this).bindMatchmaking(matchmaking); }

    public void bindStateService(PlayerStateService stateService) { new DungeonCommandRouterDelegate(this).bindStateService(stateService); }

    public void bindAdmin(WorldPool pool,
            InstanceSlotAllocator allocator,
            Runnable reloadAction) { new DungeonCommandRouterDelegate(this).bindAdmin(pool, allocator, reloadAction); }

    public void bindAudit(AuditService audit) { new DungeonCommandRouterDelegate(this).bindAudit(audit); }

    private emaki.jiuwu.craft.dungeon.random.command.RandomCommands randomCommands;

    public void bindRandom(emaki.jiuwu.craft.dungeon.random.command.RandomCommands randomCommands) {
        this.randomCommands = randomCommands;
    }

    private void routeRandom(Player player, String[] args) {
        if (randomCommands == null) {
            messages.send(player, "command.unknown", Map.of("command", "random"));
            return;
        }
        randomCommands.route(player, args, player.hasPermission("emakidungeon.admin"));
    }

    private void routeAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("emakidungeon.admin")) {
            messages.send(sender, CommandKeys.NO_PERMISSION, Map.of());
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "command.admin.usage", Map.of());
            return;
        }
        switch (Texts.lower(args[1])) {
            case "instances" -> adminInstances(sender);
            case "inspect" -> adminInspect(sender, args);
            case "stop" -> adminStop(sender, args);
            case "status" -> adminStatus(sender);
            case "reload" -> adminReload(sender);
            case "validate" -> adminValidate(sender);
            case "poolreset" -> adminPoolReset(sender, args);
            case "clearslots" -> adminClearSlots(sender);
            default -> messages.send(sender, "command.admin.usage", Map.of());
        }
    }

    private void adminInstances(CommandSender sender) { new DungeonCommandRouterDelegate(this).adminInstances(sender); }

    private void adminInspect(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "command.admin.inspect_usage", Map.of());
            return;
        }
        String instanceId = args[2];
        DungeonInstance instance = registry.get(instanceId);
        if (instance == null) {
            messages.send(sender, "command.admin.stop_missing", Map.of("instance", instanceId));
            return;
        }
        long started = instance.startedAt();
        String elapsed = started == 0L ? "-" : String.valueOf((System.currentTimeMillis() - started) / 1000L);
        String party = instance.partyId() == null ? "-" : instance.partyId().toString().substring(0, 8);
        FlowRuntime flow = instance.flowRuntime();
        int pending = instance.commands() == null ? 0 : instance.commands().pending();
        long deferred = instance.commands() == null ? 0L : instance.commands().deferredTotal();
        messages.send(sender, "command.admin.inspect_header", Map.of(
                "instance", instance.instanceId(),
                "dungeon", instance.dungeonId(),
                "state", instance.state().name()));
        messages.send(sender, "command.admin.inspect_meta", Map.of(
                "difficulty", instance.difficulty(),
                "drop", instance.dropMode(),
                "type", instance.type(),
                "test", String.valueOf(instance.isTest()),
                "party", party,
                "elapsed", elapsed));
        if (flow == null) {
            messages.send(sender, "command.admin.inspect_no_flow", Map.of());
        } else {
            int order = flow.currentRoomOrder();
            String room = flow.currentRoomId();
            String checkpoint = flow.checkpointId();
            messages.send(sender, "command.admin.inspect_flow", Map.of(
                    "room", room.isEmpty() ? "-" : room,
                    "order", order == Integer.MIN_VALUE ? "-" : String.valueOf(order),
                    "checkpoint", checkpoint == null || checkpoint.isEmpty() ? "-" : checkpoint,
                    "gates", String.valueOf(flow.openedGateCount()),
                    "spawns", String.valueOf(flow.triggeredSpawnCount()),
                    "queue", String.valueOf(pending),
                    "deferred", String.valueOf(deferred)));
            List<String> lines = flow.timelineView();
            messages.send(sender, "command.admin.inspect_timeline_header",
                    Map.of("count", String.valueOf(lines.size())));
            if (lines.isEmpty()) {
                messages.send(sender, "command.admin.inspect_timeline_empty", Map.of());
            } else {
                for (String line : lines) {
                    messages.send(sender, "command.admin.inspect_timeline_line", Map.of("line", line));
                }
            }
        }
        if (audit != null) {
            audit.write("admin", Map.of("action", "inspect", "sender", sender.getName(),
                    "instance", instanceId));
        }
    }

    private void adminStop(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "command.admin.usage", Map.of());
            return;
        }
        String instanceId = args[2];
        if (registry.get(instanceId) == null) {
            messages.send(sender, "command.admin.stop_missing", Map.of("instance", instanceId));
            return;
        }
        coordinator.dispose(instanceId, "admin");
        if (audit != null) {
            audit.write("admin", Map.of("action", "stop", "sender", sender.getName(),
                    "instance", instanceId));
        }
        messages.send(sender, "command.admin.stopped", Map.of("instance", instanceId));
    }

    private void adminStatus(CommandSender sender) {
        int threads = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().startsWith("emd-instance-")) {
                threads++;
            }
        }
        int templateCount = templates.discoverDungeons().size();
        messages.send(sender, "command.admin.status_header", Map.of(
                "instances", String.valueOf(registry.all().size()),
                "threads", String.valueOf(threads),
                "templates", String.valueOf(templateCount)));
        if (adminPool != null && adminAllocator != null) {
            for (String dungeonId : templates.discoverDungeons()) {
                messages.send(sender, "command.admin.status_pool", Map.of(
                        "dungeon", dungeonId,
                        "worlds", String.valueOf(adminAllocator.worldsOf(dungeonId)),
                        "reserves", String.valueOf(adminPool.reservesOf(dungeonId).size())));
            }
        }
        boolean anyBacklog = false;
        for (DungeonInstance instance : registry.all()) {
            if (instance.commands() == null) {
                continue;
            }
            int pending = instance.commands().pending();
            if (pending <= 0 && instance.commands().deferredTotal() <= 0L) {
                continue;
            }
            anyBacklog = true;
            messages.send(sender, "command.admin.status_queue", Map.of(
                    "instance", instance.instanceId(),
                    "pending", String.valueOf(pending),
                    "deferred", String.valueOf(instance.commands().deferredTotal())));
        }
        if (!anyBacklog) {
            messages.send(sender, "command.admin.status_queue_empty", Map.of());
        }
        if (audit != null) {
            audit.write("admin", Map.of("action", "status", "sender", sender.getName()));
        }
    }

    void adminReload(CommandSender sender) {
        if (adminReload == null) {
            messages.send(sender, "command.admin.reload_failed", Map.of("error", "unbound"));
            return;
        }
        try {
            adminReload.run();
            messages.send(sender, "command.admin.reloaded", Map.of());
            if (audit != null) {
                audit.write("admin", Map.of("action", "reload", "sender", sender.getName()));
            }
        } catch (RuntimeException exception) {
            messages.send(sender, "command.admin.reload_failed",
                    Map.of("error", String.valueOf(exception.getMessage())));
        }
    }

    private void adminValidate(CommandSender sender) {
        var dungeons = templates.discoverDungeons();
        if (dungeons.isEmpty()) {
            messages.send(sender, "command.admin.validate_empty", Map.of());
            return;
        }
        for (String dungeonId : dungeons) {
            try {
                DungeonConfig config =
                        templates.findConfig(dungeonId);
                if (config == null) {
                    messages.send(sender, "command.admin.validate_error",
                            Map.of("dungeon", dungeonId, "error", "配置无效"));
                    continue;
                }
                var templateDir = templates.templateDir(dungeonId);
                boolean flowPresent = templateDir.isPresent()
                        && new java.io.File(templateDir.get(), "flow.yml").isFile();
                if (flowPresent) {
                    messages.send(sender, "command.admin.validate_ok",
                            Map.of("dungeon", dungeonId));
                } else {
                    messages.send(sender, "command.admin.validate_flow_missing",
                            Map.of("dungeon", dungeonId));
                }
            } catch (RuntimeException exception) {
                messages.send(sender, "command.admin.validate_error",
                        Map.of("dungeon", dungeonId,
                                "error", String.valueOf(exception.getMessage())));
            }
        }
        if (audit != null) {
            audit.write("admin", Map.of("action", "validate", "sender", sender.getName()));
        }
    }

    private void adminPoolReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "command.admin.usage", Map.of());
            return;
        }
        String dungeonId = args[2];
        if (templates.findConfig(dungeonId) == null) {
            messages.send(sender, "command.admin.pool_missing", Map.of("dungeon", dungeonId));
            return;
        }
        if (adminPool == null) {
            messages.send(sender, "command.admin.pool_missing", Map.of("dungeon", dungeonId));
            return;
        }
        adminPool.invalidateAllReserves(dungeonId);
        if (audit != null) {
            audit.write("admin", Map.of("action", "poolreset", "sender", sender.getName(),
                    "dungeon", dungeonId));
        }
        messages.send(sender, "command.admin.pool_reset", Map.of("dungeon", dungeonId));
    }

    private void adminClearSlots(CommandSender sender) { new DungeonCommandRouterDelegate(this).adminClearSlots(sender); }

    public DungeonCommandRouter(Plugin plugin,
            MessageService messages,
            ChallengeService challenge,
            PartyService parties,
            RecoveryService recovery,
            SnapshotManager snapshots,
            ChatInputService chatInput,
            TemplateRepository templates,
            InstanceCoordinator coordinator,
            InstanceRegistry registry,
            MessageIsolation isolation) {
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
            sendHelpPage(sender, helpRoot(), args.length == 0
                    ? List.of() : Arrays.asList(args).subList(1, args.length));
            return;
        }
        String sub = args[0];
        if ("list".equalsIgnoreCase(sub)) {
            routeList(sender);
            return;
        }
        if ("admin".equalsIgnoreCase(sub)) {
            routeAdmin(sender, args);
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
            case "recruit" -> routeRecruit(player, args);
            case "match" -> routeMatch(player, args);
            case "spectate" -> routeSpectate(player, args);
            case "random" -> routeRandom(player, args);
            default -> messages.send(sender, "command.unknown", Map.of("command", sub));
        }
    }

    private void routeList(CommandSender sender) { new DungeonCommandRouterDelegate(this).routeList(sender); }

    private void routeEnter(Player player, String[] args) { new DungeonCommandRouterDelegate(this).routeEnter(player, args); }

    private void routeRecruit(Player player, String[] args) {
        if (matchmaking == null) {
            messages.send(player, "match.disabled", Map.of());
            return;
        }
        if (args.length < 2) {
            messages.send(player, "command.recruit.usage");
            return;
        }
        switch (Texts.lower(args[1])) {
            case "open" -> {
                if (args.length < 3 || Texts.isBlank(args[2])) {
                    messages.send(player, "command.recruit.usage");
                    return;
                }
                matchmaking.openRecruit(player, args[2], args.length >= 4 ? args[3] : null);
            }
            case "close" -> matchmaking.closeRecruit(player);
            case "list" -> matchmaking.listRecruits(player);
            case "join" -> {
                if (args.length < 3 || Texts.isBlank(args[2])) {
                    messages.send(player, "command.recruit.usage");
                    return;
                }
                int id;
                try {
                    id = Integer.parseInt(args[2]);
                } catch (NumberFormatException exception) {
                    messages.send(player, "command.recruit.usage");
                    return;
                }
                matchmaking.joinRecruit(player, id);
            }
            default -> messages.send(player, "command.recruit.usage");
        }
    }

    private void routeMatch(Player player, String[] args) {
        if (matchmaking == null) {
            messages.send(player, "match.disabled", Map.of());
            return;
        }
        if (args.length < 2) {
            messages.send(player, "command.match.usage");
            return;
        }
        switch (Texts.lower(args[1])) {
            case "join" -> {
                if (args.length < 3 || Texts.isBlank(args[2])) {
                    messages.send(player, "command.match.usage");
                    return;
                }
                matchmaking.queue(player, args[2], args.length >= 4 ? args[3] : null);
            }
            case "leave" -> matchmaking.leaveQueue(player);
            default -> messages.send(player, "command.match.usage");
        }
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
            if (!"确认".equals(text) && !"confirm".equals(text)) {
                messages.send(player, "command.leave.aborted");
                return;
            }
            completeLeave(player, instanceId);
        }));
    }

    private void completeLeave(Player player, String instanceId) {
        emaki.jiuwu.craft.dungeon.exit.InstanceExitService service = exitService;
        if (service != null) {
            service.leave(player, "voluntary");
            return;
        }
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
        if (instance != null && instance.flowRuntime() != null) {
            instance.flowRuntime().clearHintBossBar(playerId);
        }
        if (instance != null && (soleMember || noOnlineMemberInside(instance))) {
            coordinator.dispose(instanceId, "all_left");
        }
    }

    private boolean noOnlineMemberInside(DungeonInstance instance) { return new DungeonCommandRouterDelegate(this).noOnlineMemberInside(instance); }

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
            var dungeonConfig = configOf.apply(dungeonId);
            if (dungeonConfig == null) {
                continue;
            }
            Map<String, Object> status = limitService.status(target.getUniqueId(), dungeonId);
            int dailyTotal = DungeonCommandRouterSupport.number(status.get("daily-total"));
            int dailyUsed = DungeonCommandRouterSupport.number(status.get("daily-used"));
            int weeklyTotal = DungeonCommandRouterSupport.number(status.get("weekly-total"));
            int weeklyUsed = DungeonCommandRouterSupport.number(status.get("weekly-used"));
            int monthlyTotal = DungeonCommandRouterSupport.number(status.get("monthly-total"));
            int monthlyUsed = DungeonCommandRouterSupport.number(status.get("monthly-used"));
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
            messages.send(player, "command.stats.limit_windows", Map.of(
                    "monthly", dungeonConfig.limitsMonthlyWeeks().isEmpty()
                            ? "*" : DungeonCommandRouterSupport.joinNumbers(dungeonConfig.limitsMonthlyWeeks()),
                    "weekly", dungeonConfig.limitsWeeklyDays().isEmpty()
                            ? "*" : DungeonCommandRouterSupport.joinNumbers(dungeonConfig.limitsWeeklyDays()),
                    "daily", dungeonConfig.limitsDailyWindow() == null
                            ? "*" : dungeonConfig.limitsDailyWindow().format()));
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
                        "duration", DungeonCommandRouterSupport.formatDuration(entry.durationMs())
                ));
            }
        }
    }

    private void routeRank(Player player, String[] args) { new DungeonCommandRouterDelegate(this).routeRank(player, args); }


    private void routeGlobalChat(Player player, String[] args) { new DungeonCommandRouterDelegate(this).routeGlobalChat(player, args); }

    private void routeEditor(Player player, String[] args) {
        if (editor == null) {
            messages.send(player, "general.invalid_args", Map.of());
            return;
        }
        if (!player.hasPermission("emakidungeon.editor")) {
            messages.send(player, CommandKeys.NO_PERMISSION, Map.of());
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
                case "menu" -> editor.openMenu(player);
                case "save" -> editor.save(player);
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

    private void routePartyInvite(Player player, String[] args) { new DungeonCommandRouterDelegate(this).routePartyInvite(player, args); }

    private void routePartyKick(Player player, String[] args) { new DungeonCommandRouterDelegate(this).routePartyKick(player, args); }

    UUID parseUuid(String raw) { return new DungeonCommandRouterDelegate(this).parseUuid(raw); }

    void sendPartyResult(Player player, PartyService.InviteResult result) { new DungeonCommandRouterDelegate(this).sendPartyResult(player, result); }

    private void routeSpectate(Player player, String[] args) {
        if (stateService == null || !stateService.isSpectator(player.getUniqueId())) {
            messages.send(player, "command.spectate.not_spectator", Map.of());
            return;
        }
        if (args.length < 2) {
            messages.send(player, "command.spectate.usage", Map.of());
            return;
        }
        String sub = Texts.lower(args[1]);
        if ("next".equals(sub)) {
            stateService.switchSpectateTarget(player.getUniqueId(), true);
        } else if ("prev".equals(sub)) {
            stateService.switchSpectateTarget(player.getUniqueId(), false);
        } else {
            messages.send(player, "command.spectate.usage", Map.of());
        }
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
                return EditorService.markTypes();
            }
            if (args.length == 3 && "fill".equals(Texts.lower(args[1]))) {
                String prefix = Texts.lower(args[2]);
                List<String> blocks = new ArrayList<>();
                for (Material material : Material.values()) {
                    if (material.isBlock() && Texts.lower(material.name()).startsWith(prefix)) {
                        blocks.add(Texts.lower(material.name()));
                    }
                }
                return blocks;
            }
        }
        if ("recruit".equals(root)) {
            if (args.length == 2) {
                return RECRUIT_SUBS;
            }
            String recruitSub = Texts.lower(args[1]);
            if ("open".equals(recruitSub) && args.length == 3) {
                return templates.discoverDungeons();
            }
            if ("open".equals(recruitSub) && args.length == 4) {
                return difficultiesFor(args[2], Texts.lower(args[3]));
            }
            if ("join".equals(recruitSub) && args.length == 3 && matchmaking != null) {
                String prefix = Texts.lower(args[2]);
                List<String> ids = new ArrayList<>();
                for (int id : matchmaking.recruitIds()) {
                    if (String.valueOf(id).startsWith(prefix)) {
                        ids.add(String.valueOf(id));
                    }
                }
                return ids;
            }
        }
        if ("match".equals(root)) {
            if (args.length == 2) {
                return MATCH_SUBS;
            }
            if ("join".equals(Texts.lower(args[1])) && args.length == 3) {
                return templates.discoverDungeons();
            }
            if ("join".equals(Texts.lower(args[1])) && args.length == 4) {
                return difficultiesFor(args[2], Texts.lower(args[3]));
            }
        }
        if ("spectate".equals(root)) {
            if (args.length == 2) {
                return SPECTATE_SUBS;
            }
        }
        if ("admin".equals(root) && sender.hasPermission("emakidungeon.admin")) {
            if (args.length == 2) {
                return ADMIN_SUBS;
            }
            String adminSub = Texts.lower(args[1]);
            if (("inspect".equals(adminSub) || "stop".equals(adminSub)) && args.length == 3) {
                List<String> ids = new ArrayList<>();
                for (DungeonInstance instance : registry.all()) {
                    ids.add(instance.instanceId());
                }
                return ids;
            }
            if ("poolreset".equals(adminSub) && args.length == 3) {
                return templates.discoverDungeons();
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
        if ("stats".equals(root)) {
            if (args.length == 2) {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    names.add(online.getName());
                }
                return names;
            }
            if (args.length == 3) {
                return templates.discoverDungeons();
            }
        }
        if ("rank".equals(root) && args.length == 2) {
            return templates.discoverDungeons();
        }
        if ("globalchat".equals(root) && args.length == 2) {
            String prefix = Texts.lower(args[1]);
            List<String> modes = new ArrayList<>();
            for (String mode : List.of("on", "off")) {
                if (mode.startsWith(prefix)) {
                    modes.add(mode);
                }
            }
            return modes;
        }
        if ("random".equals(root) && randomCommands != null) {
            return randomCommands.complete(Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }

    private List<String> difficultiesFor(String dungeonId, String prefix) {
        List<String> result = new ArrayList<>();
        if (configOf == null) {
            return result;
        }
        DungeonConfig config = configOf.apply(dungeonId);
        if (config == null) {
            return result;
        }
        for (String difficulty : config.difficulties()) {
            if (Texts.lower(difficulty).startsWith(prefix)) {
                result.add(difficulty);
            }
        }
        return result;
    }

    private String helpRoot() {
        return "/edungeon";
    }

    private void sendHelpPage(CommandSender sender, String root, List<String> path) {
        if (path.isEmpty()) {
            sendHelpLines(sender, root, "command.help_header",
                    "command.help_entry_list", "command.help_entry_enter", "command.help_entry_leave",
                    "command.help_entry_cancel", "command.help_entry_ready", "command.help_entry_rewards",
                    "command.help_entry_party", "command.help_entry_stats", "command.help_entry_rank",
                    "command.help_entry_globalchat", "command.help_entry_editor", "command.help_entry_admin",
                    "command.help_entry_recruit", "command.help_entry_match", "command.help_entry_spectate",
                    "command.help_entry_random", "command.help_entry_help");
            sendHelpFooter(sender);
            return;
        }
        switch (Texts.lower(path.get(0))) {
            case "enter" -> sendValueHelp(sender, root, "command.help_enter_header",
                    "command.help_value_desc_enter", "enter", templates.discoverDungeons());
            case "rank" -> sendValueHelp(sender, root, "command.help_rank_header",
                    "command.help_value_desc_rank", "rank", templates.discoverDungeons());
            case "stats" -> sendValueHelp(sender, root, "command.help_stats_header",
                    "command.help_value_desc_stats", "stats", onlinePlayerNames());
            case "party" -> sendPartyHelp(sender, root, path);
            case "globalchat" -> sendGlobalChatHelp(sender, root);
            case "editor" -> sendEditorHelp(sender, root, path);
            case "admin" -> sendAdminHelp(sender, root, path);
            case "recruit" -> sendRecruitHelp(sender, root, path);
            case "match" -> sendMatchHelp(sender, root, path);
            case "spectate" -> sendSpectateHelp(sender, root);
            case "random" -> sendRandomHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of());
        }
    }

    private void sendPartyHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 2) {
            String tail = Texts.lower(path.get(1));
            if ("invite".equals(tail)) {
                sendValueHelp(sender, root, "command.help_party_invite_header",
                        "command.help_value_desc_party_invite", "party invite", onlinePlayerNames());
                return;
            }
            if ("kick".equals(tail)) {
                sendValueHelp(sender, root, "command.help_party_kick_header",
                        "command.help_value_desc_party_kick", "party kick", onlinePlayerNames());
                return;
            }
        }
        sendHelpLines(sender, root, "command.help_party_header",
                "command.help_party_entry_create", "command.help_party_entry_invite",
                "command.help_party_entry_accept", "command.help_party_entry_deny",
                "command.help_party_entry_kick", "command.help_party_entry_leave",
                "command.help_party_entry_disband", "command.help_party_entry_chat");
        sendHelpFooter(sender);
    }

    private void sendGlobalChatHelp(CommandSender sender, String root) {
        sendHelpLines(sender, root, "command.help_globalchat_header",
                "command.help_globalchat_entry_on", "command.help_globalchat_entry_off");
        sendHelpFooter(sender);
    }

    private void sendEditorHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 2) {
            String tail = Texts.lower(path.get(1));
            if ("dungeon".equals(tail)) {
                sendValueHelp(sender, root, "command.help_editor_dungeon_header",
                        "command.help_value_desc_editor_dungeon", "editor", templates.discoverDungeons());
                return;
            }
            if ("mark".equals(tail)) {
                sendValueHelp(sender, root, "command.help_editor_mark_header",
                        "command.help_value_desc_editor_mark", "editor mark", EditorService.markTypes());
                return;
            }
        }
        sendHelpLines(sender, root, "command.help_editor_header",
                "command.help_editor_entry_dungeon", "command.help_editor_entry_exit",
                "command.help_editor_entry_pos_one", "command.help_editor_entry_pos_two",
                "command.help_editor_entry_mark", "command.help_editor_entry_marks",
                "command.help_editor_entry_test", "command.help_editor_entry_fill");
        sendHelpFooter(sender);
    }

    private void sendAdminHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 2) {
            String tail = Texts.lower(path.get(1));
            if ("inspect".equals(tail)) {
                sendValueHelp(sender, root, "command.help_admin_inspect_header",
                        "command.help_value_desc_admin_inspect", "admin inspect", instanceIds());
                return;
            }
            if ("stop".equals(tail)) {
                sendValueHelp(sender, root, "command.help_admin_stop_header",
                        "command.help_value_desc_admin_stop", "admin stop", instanceIds());
                return;
            }
            if ("poolreset".equals(tail)) {
                sendValueHelp(sender, root, "command.help_admin_poolreset_header",
                        "command.help_value_desc_admin_poolreset", "admin poolreset", templates.discoverDungeons());
                return;
            }
        }
        sendHelpLines(sender, root, "command.help_admin_header",
                "command.help_admin_entry_instances", "command.help_admin_entry_inspect",
                "command.help_admin_entry_stop", "command.help_admin_entry_status",
                "command.help_admin_entry_reload", "command.help_admin_entry_validate",
                "command.help_admin_entry_poolreset", "command.help_admin_entry_clearslots");
        sendHelpFooter(sender);
    }

    private void sendRecruitHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 2) {
            String tail = Texts.lower(path.get(1));
            if ("open".equals(tail)) {
                sendValueHelp(sender, root, "command.help_recruit_open_header",
                        "command.help_value_desc_recruit_open", "recruit open", templates.discoverDungeons());
                return;
            }
            if ("join".equals(tail)) {
                sendValueHelp(sender, root, "command.help_recruit_join_header",
                        "command.help_value_desc_recruit_join", "recruit join", recruitIds());
                return;
            }
        }
        sendHelpLines(sender, root, "command.help_recruit_header",
                "command.help_recruit_entry_open", "command.help_recruit_entry_close",
                "command.help_recruit_entry_list", "command.help_recruit_entry_join");
        sendHelpFooter(sender);
    }

    private void sendMatchHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 2 && "join".equals(Texts.lower(path.get(1)))) {
            sendValueHelp(sender, root, "command.help_match_join_header",
                    "command.help_value_desc_match_join", "match join", templates.discoverDungeons());
            return;
        }
        sendHelpLines(sender, root, "command.help_match_header",
                "command.help_match_entry_join", "command.help_match_entry_leave");
        sendHelpFooter(sender);
    }

    private void sendSpectateHelp(CommandSender sender, String root) {
        sendHelpLines(sender, root, "command.help_spectate_header",
                "command.help_spectate_entry_next", "command.help_spectate_entry_prev");
        sendHelpFooter(sender);
    }

    private void sendRandomHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 2) {
            String tail = Texts.lower(path.get(1));
            if ("template".equals(tail)) {
                sendRandomTemplateHelp(sender, root, path);
                return;
            }
            if ("enter".equals(tail)) {
                sendValueHelp(sender, root, "command.help_random_enter_header",
                        "command.help_value_desc_random_enter", "random enter", randomInstanceIds());
                return;
            }
            if ("stop".equals(tail)) {
                sendValueHelp(sender, root, "command.help_random_stop_header",
                        "command.help_value_desc_random_stop", "random stop", randomInstanceIds());
                return;
            }
            if ("replay".equals(tail)) {
                sendValueHelp(sender, root, "command.help_random_replay_header",
                        "command.help_value_desc_random_replay", "random replay", randomRecordIds());
                return;
            }
        }
        sendHelpLines(sender, root, "command.help_random_header",
                "command.help_random_entry_template", "command.help_random_entry_create",
                "command.help_random_entry_enter", "command.help_random_entry_leave",
                "command.help_random_entry_list", "command.help_random_entry_records",
                "command.help_random_entry_stop", "command.help_random_entry_replay",
                "command.help_random_entry_reload");
        sendHelpFooter(sender);
    }

    private void sendRandomTemplateHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 3) {
            String tail = Texts.lower(path.get(2));
            if ("meta".equals(tail)) {
                sendHelpLines(sender, root, "command.help_random_template_meta_header",
                        "command.help_random_template_meta_entry_type",
                        "command.help_random_template_meta_entry_tags",
                        "command.help_random_template_meta_entry_floors",
                        "command.help_random_template_meta_entry_mirrorable",
                        "command.help_random_template_meta_entry_intrusion_policy",
                        "command.help_random_template_meta_entry_door_policy",
                        "command.help_random_template_meta_entry_seal_block",
                        "command.help_random_template_meta_entry_rotations");
                sendHelpFooter(sender);
                return;
            }
            if ("anchor".equals(tail)) {
                sendHelpLines(sender, root, "command.help_random_template_anchor_header",
                        "command.help_random_template_anchor_entry_add",
                        "command.help_random_template_anchor_entry_remove");
                sendHelpFooter(sender);
                return;
            }
            if ("node".equals(tail)) {
                sendHelpLines(sender, root, "command.help_random_template_node_header",
                        "command.help_random_template_node_entry_add",
                        "command.help_random_template_node_entry_param");
                sendHelpFooter(sender);
                return;
            }
        }
        sendHelpLines(sender, root, "command.help_random_template_header",
                "command.help_random_template_entry_start", "command.help_random_template_entry_pos_one",
                "command.help_random_template_entry_pos_two", "command.help_random_template_entry_meta",
                "command.help_random_template_entry_anchor", "command.help_random_template_entry_node",
                "command.help_random_template_entry_save", "command.help_random_template_entry_discard",
                "command.help_random_template_entry_status");
        sendHelpFooter(sender);
    }

    private void sendValueHelp(CommandSender sender, String root, String headerKey, String descKey,
            String command, List<String> values) {
        sendHelpLine(sender, messages.message(headerKey, Map.of("root", root)));
        if (values.isEmpty()) {
            sendHelpLine(sender, messages.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String value : values) {
            sendValueEntry(sender, value,
                    messages.message(descKey, Map.of("value", value)),
                    root + " " + command + " " + value);
        }
        sendHelpFooter(sender);
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    private List<String> instanceIds() {
        List<String> ids = new ArrayList<>();
        for (DungeonInstance instance : registry.all()) {
            ids.add(instance.instanceId());
        }
        return ids;
    }

    private List<String> recruitIds() {
        List<String> ids = new ArrayList<>();
        if (matchmaking != null) {
            for (int id : matchmaking.recruitIds()) {
                ids.add(String.valueOf(id));
            }
        }
        return ids;
    }

    private List<String> randomInstanceIds() {
        if (randomCommands == null) {
            return List.of();
        }
        return randomCommands.instanceIds();
    }

    private List<String> randomRecordIds() {
        if (randomCommands == null) {
            return List.of();
        }
        return randomCommands.recordIds();
    }

    private void sendValueEntry(CommandSender sender, String value, String desc, String run) {
        sendHelpLine(sender, messages.message("command.help_value_entry", Map.of(
                "value", value,
                "desc", desc,
                "run", run)));
    }

    private void sendHelpLines(CommandSender sender, String root, String... keys) {
        Map<String, ?> replacements = Map.of("root", root);
        for (String key : keys) {
            sendHelpLine(sender, messages.message(key, replacements));
        }
    }

    private void sendHelpLine(CommandSender sender, String line) {
        messages.sendComponent(sender, messages.render(line));
    }

    private void sendHelpFooter(CommandSender sender) {
        sendHelpLine(sender, messages.message("command.help_footer"));
    }
}
