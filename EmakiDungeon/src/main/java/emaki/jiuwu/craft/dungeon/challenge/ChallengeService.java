package emaki.jiuwu.craft.dungeon.challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.action.ActionBridge;
import emaki.jiuwu.craft.dungeon.api.ErrorCodes;
import emaki.jiuwu.craft.dungeon.api.Result;
import emaki.jiuwu.craft.dungeon.api.event.DungeonEnterEvent;
import emaki.jiuwu.craft.dungeon.audit.AuditService;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.flow.FlowModules;
import emaki.jiuwu.craft.dungeon.flow.FlowRuntime;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.EventIngress.IngressEvent;
import emaki.jiuwu.craft.dungeon.limit.LimitService;
import emaki.jiuwu.craft.dungeon.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.dungeon.orchestration.DenyReason;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceSlotAllocator;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.script.ScriptRuntimeManager;
import emaki.jiuwu.craft.dungeon.snapshot.SnapshotManager;
import emaki.jiuwu.craft.dungeon.state.PlayerStateService;
import emaki.jiuwu.craft.dungeon.tempitem.TempItemManager;
import emaki.jiuwu.craft.dungeon.ticket.TicketService;
import emaki.jiuwu.craft.dungeon.trigger.TriggerType;
import emaki.jiuwu.craft.dungeon.world.WorldPool;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.scoreboard.Scoreboard;

public final class ChallengeService {

    private static final long TICK_PERIOD = 10L;
    private static final List<String> SCOREBOARD_CHANNELS = List.of("scoreboard");
    static final Set<String> PLAYER_SIDE_REASONS = Set.of(
            "member_cancel", "member_left", "window_timeout", "invalid_difficulty",
            "invalid_mode", "challenge_start_cancelled", "permission",
            "ticket_insufficient", "ticket_invalid", "unlock_denied");

    final Plugin plugin;
    final ExecutionDispatcher dispatcher;
    final MessageService messages;
    final LanguageLoader lang;
    final DebugLogger debug;
    final PartyService parties;
    final InstanceSlotAllocator allocator;
    private final WorldPool pool;
    private final InstanceRegistry registry;
    private final SnapshotManager snapshots;
    final ChallengeGuiService gui;
    final Function<String, DungeonConfig> configOf;
    private final Consumer<UUID> teleportPermit;

    final Map<UUID, ChallengeSession> sessions = new ConcurrentHashMap<>();
    final Map<UUID, String> scoreboardObjective = new ConcurrentHashMap<>();
    TaskToken tickTask = TaskToken.UNAVAILABLE;
    volatile ActionBridge actionBridge;
    volatile Function<DungeonInstance,
            FlowRuntime> runtimeFactory;
    volatile PlayerStateService stateService;
    volatile TempItemManager tempItems;
    volatile LimitService limitService;
    volatile ScriptRuntimeManager scriptManager;
    volatile AuditService audit;
    volatile PerformanceMonitor monitor;
    volatile TicketService tickets;

    public void bindAudit(AuditService audit) { new ChallengeServiceDelegate(this).bindAudit(audit); }

    public ChallengeService(Plugin plugin,
            ExecutionDispatcher dispatcher,
            MessageService messages,
            LanguageLoader lang,
            DebugLogger debug,
            PartyService parties,
            InstanceSlotAllocator allocator,
            WorldPool pool,
            InstanceRegistry registry,
            SnapshotManager snapshots,
            ChallengeGuiService gui,
            Function<String, DungeonConfig> configOf,
            Consumer<UUID> teleportPermit) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.messages = messages;
        this.lang = lang;
        this.debug = debug;
        this.parties = parties;
        this.allocator = allocator;
        this.pool = pool;
        this.registry = registry;
        this.snapshots = snapshots;
        this.gui = gui;
        this.configOf = configOf;
        this.teleportPermit = teleportPermit;
    }

    public void start() {
        tickTask = dispatcher.runGlobalTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public void bindActionBridge(ActionBridge actionBridge) { new ChallengeServiceDelegate(this).bindActionBridge(actionBridge); }

    public void bindInstanceHooks(
            Function<DungeonInstance,
                    FlowRuntime> runtimeFactory,
            PlayerStateService stateService,
            TempItemManager tempItems) { new ChallengeServiceDelegate(this).bindInstanceHooks(runtimeFactory, stateService, tempItems); }

    public void bindLimitService(LimitService limitService) { new ChallengeServiceDelegate(this).bindLimitService(limitService); }

    public void bindScriptManager(ScriptRuntimeManager scriptManager) { new ChallengeServiceDelegate(this).bindScriptManager(scriptManager); }

    public void bindMonitor(PerformanceMonitor monitor) { new ChallengeServiceDelegate(this).bindMonitor(monitor); }

    public void bindTicketService(TicketService tickets) { new ChallengeServiceDelegate(this).bindTicketService(tickets); }

    void fireTrigger(TriggerType type,
            String dungeonId, Map<String, String> context) { new ChallengeServiceDelegate(this).fireTrigger(type, dungeonId, context); }

    public void shutdown() { new ChallengeServiceDelegate(this).shutdown(); }

    public void startEnter(Player leader, String dungeonId) {
        DungeonConfig config = configOf.apply(dungeonId);
        if (config == null) {
            messages.send(leader, "challenge.enter.dungeon_missing", Map.of("dungeon", dungeonId));
            return;
        }
        if (!leader.hasPermission("emakidungeon.dungeon." + dungeonId)) {
            messages.send(leader, "challenge.enter.no_dungeon_permission", Map.of("dungeon", dungeonId));
            return;
        }
        Party party = parties.partyOf(leader.getUniqueId());
        if (party == null) {
            PartyService.InviteResult created = parties.create(leader);
            if (created != PartyService.InviteResult.OK) {
                messages.send(leader, "challenge.enter.party_error", Map.of("code", created.name()));
                return;
            }
            party = parties.partyOf(leader.getUniqueId());
        }
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            messages.send(leader, "challenge.enter.not_leader", Map.of());
            return;
        }
        ChallengeSession existing = sessions.get(party.id());
        if (existing != null) {
            messages.send(leader, "challenge.enter.already_started", Map.of());
            return;
        }
        if (allocator.isPartyBusy(party.id())) {
            messages.send(leader, "challenge.enter.party_busy", Map.of());
            return;
        }
        if (limitService != null && !leader.hasPermission("emakidungeon.bypass.limit")) {
            var verdict = limitService.check(leader.getUniqueId(), dungeonId);
            if (verdict.verdict() != LimitService.Verdict.ALLOWED) {
                messages.send(leader, limitMessageKey(verdict.verdict()), Map.of(
                        "player", leader.getName(),
                        "seconds", String.valueOf(verdict.retrySeconds())));
                debug.log("dungeon", leader, "limit_denied", Map.of(
                        "dungeon", dungeonId,
                        "member", String.valueOf(leader.getUniqueId()),
                        "verdict", verdict.verdict().name()));
                return;
            }
        }
        ChallengeSession session = new ChallengeSession(party.id(), dungeonId, leader.getUniqueId());
        sessions.put(party.id(), session);
        gui.openOptionSelect(leader, "edg_difficulty", "gui.difficulty.title", "gui.difficulty",
                config.difficulties(), option -> onDifficultyPicked(session, leader, option));
        debug.log("dungeon", leader, "challenge_opened", Map.of(
                "dungeon", dungeonId,
                "party", String.valueOf(party.id())
        ));
    }

    private void onDifficultyPicked(ChallengeSession session, Player leader, String option) { new ChallengeServiceDelegate(this).onDifficultyPicked(session, leader, option); }

    void onModePicked(ChallengeSession session, Player leader, String mode) {
        if (sessions.get(session.partyId()) != session) {
            return;
        }
        DungeonConfig config = configOf.apply(session.dungeonId());
        if (config == null || !config.supportsDropMode(mode)) {
            messages.send(leader, "challenge.enter.invalid_mode", Map.of("mode", mode));
            abort(session, "invalid_mode");
            return;
        }
        session.setDropMode(mode);
        var startEvent = new emaki.jiuwu.craft.dungeon.api.event.DungeonChallengeStartEvent(
                session.dungeonId(), config.prepareWindowSeconds(),
                session.difficulty(), session.dropMode());
        Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) {
            abort(session, "challenge_start_cancelled");
            return;
        }
        if (!initialChecks(session, leader)) {
            abort(session, "initial_failed");
            return;
        }
        session.setPhase(ChallengePhase.PREPARING);
        session.setWindowDeadline(System.currentTimeMillis() + config.prepareWindowSeconds() * 1000L);
        session.ready().clear();
        Party party = parties.partyById(session.partyId());
        if (party == null) {
            abort(session, "party_missing");
            return;
        }
        debug.log("dungeon", leader, "challenge_start", Map.of(
                "dungeon", session.dungeonId(),
                "difficulty", session.difficulty(),
                "mode", session.dropMode()
        ));
        for (UUID memberId : party.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                continue;
            }
            messages.send(member, "challenge.preparing", Map.of(
                    "dungeon", session.dungeonId(),
                    "seconds", String.valueOf(config.prepareWindowSeconds())
            ));
            gui.openConfirm(member, action -> onReadyRequested(session, member));
        }
        if (party.size() == 1) {
            onReadyRequested(session, leader);
        }
    }

    private boolean initialChecks(ChallengeSession session, Player leader) {
        Party party = parties.partyById(session.partyId());
        if (party == null) {
            messages.send(leader, "challenge.enter.party_missing", Map.of());
            return false;
        }
        for (UUID memberId : party.members()) {
            if (Bukkit.getPlayer(memberId) == null) {
                messages.send(leader, "challenge.enter.member_offline", Map.of());
                return false;
            }
        }
        DungeonConfig config = configOf.apply(session.dungeonId());
        if (config == null) {
            messages.send(leader, "challenge.enter.dungeon_missing", Map.of("dungeon", session.dungeonId()));
            return false;
        }
        if (pool.reserveCount(session.dungeonId()) <= 0) {
            int worlds = allocator.worldsOf(session.dungeonId());
            if (worlds >= config.poolMaxWorlds()) {
                messages.send(leader, "challenge.enter.dungeon_full", Map.of("dungeon", session.dungeonId()));
                debug.log("dungeon", leader, "instance_full", Map.of("dungeon", session.dungeonId()));
                fireTrigger(TriggerType.onInstanceFull,
                        session.dungeonId(), Map.of("dungeon", session.dungeonId()));
                return false;
            }
        }
        return true;
    }

    public void onReadyRequested(ChallengeSession session, Player player) { new ChallengeServiceDelegate(this).onReadyRequested(session, player); }

    public Result<Object> apiEnter(Player player, String dungeonId) {
        return apiEnter(player, dungeonId, false);
    }

    public Result<Object> apiEnter(Player player, String dungeonId, boolean test) {
        DungeonConfig config = configOf.apply(dungeonId);
        if (config == null) {
            return Result.err(
                    ErrorCodes.DUNGEON_NOT_FOUND,
                    "api.dungeon_not_found", Map.of("dungeon", dungeonId));
        }
        if (!player.hasPermission("emakidungeon.dungeon." + dungeonId)) {
            return Result.err(
                    ErrorCodes.DENY_PERMISSION,
                    "api.denied_permission", Map.of("dungeon", dungeonId));
        }
        Party party = parties.partyOf(player.getUniqueId());
        boolean solo = party == null || party.size() == 1;
        if (party == null) {
            if (parties.create(player) != PartyService.InviteResult.OK) {
                return Result.err(
                        ErrorCodes.DENY_PARTY_INVALID,
                        "api.party_invalid");
            }
            party = parties.partyOf(player.getUniqueId());
        }
        if (party == null) {
            return Result.err(
                    ErrorCodes.DENY_PARTY_INVALID,
                    "api.party_invalid");
        }
        if (!solo && !party.isLeader(player.getUniqueId())) {
            return Result.err(
                    ErrorCodes.DENY_NOT_LEADER,
                    "api.not_leader");
        }
        if (sessions.get(party.id()) != null || allocator.isPartyBusy(party.id())) {
            return Result.err(
                    ErrorCodes.DENY_PARTY_INVALID,
                    "api.challenge_active");
        }
        if (!solo) {
            startEnter(player, dungeonId);
            if (sessions.get(party.id()) == null) {
                return Result.err(
                        ErrorCodes.DENY_PARTY_INVALID,
                        "api.challenge_active");
            }
            sessions.get(party.id()).setTest(test);
            return Result.ok(
                    new emaki.jiuwu.craft.dungeon.api.ChallengeId(String.valueOf(party.id())));
        }
        if (limitService != null && !player.hasPermission("emakidungeon.bypass.limit")) {
            var verdict = limitService.check(player.getUniqueId(), dungeonId);
            if (verdict.verdict() != LimitService.Verdict.ALLOWED) {
                String code = limitErrorCode(verdict.verdict());
                return Result.err(
                        code,
                        "api.denied_" + code.substring(5).toLowerCase(Locale.ROOT),
                        Map.of("seconds", String.valueOf(verdict.retrySeconds())));
            }
        }
        ChallengeSession session = new ChallengeSession(party.id(), dungeonId, player.getUniqueId());
        sessions.put(party.id(), session);
        session.setTest(test);
        session.setDifficulty(config.difficulties().get(0));
        String mode = config.dropDefaultMode();
        if (!config.supportsDropMode(mode)) {
            mode = config.dropModeAllowed().isEmpty() ? "free" : config.dropModeAllowed().get(0);
        }
        session.setDropMode(mode);
        var startEvent = new emaki.jiuwu.craft.dungeon.api.event.DungeonChallengeStartEvent(
                dungeonId, config.prepareWindowSeconds(), session.difficulty(), mode);
        Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) {
            abort(session, "challenge_start_cancelled");
            return Result.err(
                    ErrorCodes.DENY_PARTY_INVALID,
                    "api.challenge_cancelled");
        }
        session.setPhase(ChallengePhase.PREPARING);
        session.setWindowDeadline(System.currentTimeMillis() + config.prepareWindowSeconds() * 1000L);
        session.ready().clear();
        String reason = finalizeReady(session, party);
        if (reason != null) {
            return Result.err(mapAbortReason(reason),
                    "api.enter_failed", Map.of("reason", reason));
        }
        for (DungeonInstance instance : registry.all()) {
            if (party.id().equals(instance.partyId())) {
                return Result.ok(
                        new emaki.jiuwu.craft.dungeon.api.InstanceId(instance.instanceId()));
            }
        }
        return Result.ok(
                new emaki.jiuwu.craft.dungeon.api.ChallengeId(String.valueOf(party.id())));
    }

    private static String mapAbortReason(String reason) { return ChallengeServiceDelegate.mapAbortReason(reason); }

    private static String limitErrorCode(LimitService.Verdict verdict) { return ChallengeServiceDelegate.limitErrorCode(verdict); }

    static String limitErrorCodeByName(String verdictName) { return ChallengeServiceDelegate.limitErrorCodeByName(verdictName); }

    private static String limitMessageKey(LimitService.Verdict verdict) { return ChallengeServiceDelegate.limitMessageKey(verdict); }

    String finalizeReady(ChallengeSession session, Party party) {
        Player leader = Bukkit.getPlayer(session.leaderId());
        DungeonConfig config = configOf.apply(session.dungeonId());
        if (config == null || leader == null) {
            abort(session, "config_missing");
            return "config_missing";
        }
        for (UUID memberId : party.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                abort(session, "member_offline");
                return "member_offline";
            }
            if (!member.hasPermission("emakidungeon.dungeon." + session.dungeonId())
                    && !member.hasPermission("emakidungeon.bypass.limit")) {
                messages.send(leader, "challenge.enter.no_dungeon_permission", Map.of("dungeon", session.dungeonId()));
                abort(session, "permission");
                return "permission";
            }
            if (limitService != null && !member.hasPermission("emakidungeon.bypass.limit")) {
                var verdict = limitService.check(memberId, session.dungeonId());
                if (verdict.verdict() != LimitService.Verdict.ALLOWED) {
                    String key = limitMessageKey(verdict.verdict());
                    messages.send(leader, key, Map.of(
                            "player", member.getName(),
                            "seconds", String.valueOf(verdict.retrySeconds())
                    ));
                    debug.log("dungeon", leader, "limit_denied", Map.of(
                            "dungeon", session.dungeonId(),
                            "member", String.valueOf(memberId),
                            "verdict", verdict.verdict().name()
                    ));
                    String reason = "limit_" + verdict.verdict().name();
                    abort(session, reason);
                    return reason;
                }
            }
        }
        if (config.unlock() != null && config.unlock().configured()) {
            for (UUID memberId : party.members()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member == null) {
                    continue;
                }
                if (!ConditionEvaluator.evaluate(config.unlock(),
                        text -> resolvePapi(member, text), ConditionContext.of(member))) {
                    messages.send(member, "challenge.enter.unlock_denied",
                            Map.of("dungeon", session.dungeonId()));
                    abort(session, "unlock_denied");
                    return "unlock_denied";
                }
            }
        }
        DungeonConfig.TicketRule ticketRule = config.ticket();
        if (ticketRule != null && ticketRule.enabled() && !session.isTest() && tickets != null) {
            TicketService.TicketTemplate template = tickets.resolve(ticketRule);
            if (template == null) {
                messages.send(leader, "challenge.enter.ticket_invalid",
                        Map.of("dungeon", session.dungeonId(), "item", ticketRule.item()));
                abort(session, "ticket_invalid");
                return "ticket_invalid";
            }
            for (UUID memberId : party.members()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member == null || !tickets.canAfford(member, template, ticketRule.amount())) {
                    messages.send(leader, "challenge.enter.ticket_insufficient", Map.of(
                            "dungeon", session.dungeonId(),
                            "player", member == null ? String.valueOf(memberId) : member.getName(),
                            "item", ticketRule.item(),
                            "amount", String.valueOf(ticketRule.amount())));
                    abort(session, "ticket_insufficient");
                    return "ticket_insufficient";
                }
            }
            for (UUID memberId : party.members()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    session.ticketItems().put(memberId,
                            tickets.deduct(member, template, ticketRule.amount()));
                }
            }
        }

        debug.log("dungeon", leader, "ready_all", Map.of(
                "dungeon", session.dungeonId(),
                "party", String.valueOf(party.id())
        ));
        fireTrigger(TriggerType.onReadyAll,
                session.dungeonId(),
                Map.of("dungeon", session.dungeonId(),
                        "party", String.valueOf(party.id())));
        DenyReason deny = allocator.tryAcquireParty(session.dungeonId(), party.id(),
                pool.reserveCount(session.dungeonId()) > 0);
        if (deny != null) {
            handleDeny(leader, session.dungeonId(), deny);
            String reason = "deny_" + deny.name();
            abort(session, reason);
            return reason;
        }
        if (monitor != null) {
            monitor.markEnterPhase();
        }
        String worldName = pool.takeReserve(session.dungeonId());
        if (worldName != null) {
            beginEnter(session, party, worldName);
            return null;
        }
        if (allocator.worldsOf(session.dungeonId()) >= config.poolMaxWorlds()) {
            allocator.releaseParty(party.id());
            messages.send(leader, "challenge.enter.dungeon_full", Map.of("dungeon", session.dungeonId()));
            debug.log("dungeon", leader, "instance_full", Map.of("dungeon", session.dungeonId()));
            fireTrigger(TriggerType.onInstanceFull,
                    session.dungeonId(), Map.of("dungeon", session.dungeonId()));
            abort(session, "deny_DUNGEON_FULL");
            return "deny_DENY_DUNGEON_FULL";
        }
        enterWait(session, party);
        return null;
    }

    private void handleDeny(Player leader, String dungeonId, DenyReason deny) { new ChallengeServiceDelegate(this).handleDeny(leader, dungeonId, deny); }

    private void enterWait(ChallengeSession session, Party party) {
        DungeonConfig config = configOf.apply(session.dungeonId());
        if (config == null) {
            allocator.releaseParty(party.id());
            abort(session, "config_missing");
            return;
        }
        session.setPhase(ChallengePhase.ENTERING_WAIT);
        session.setWaitDeadline(System.currentTimeMillis() + config.waitTimeoutSeconds() * 1000L);
        if (monitor != null) {
            monitor.markEnterPhase();
        }
        List<Player> online = onlineMembers(party);
        String channel = config.waitChannel();
        if ("bossbar".equals(channel)) {
            BossBar bar = Bukkit.createBossBar(
                    MiniMessages.plainText(render("challenge.wait.bossbar", Map.of("dungeon", session.dungeonId()))),
                    BarColor.BLUE, BarStyle.SOLID);
            for (Player member : online) {
                bar.addPlayer(member);
            }
            session.setWaitBar(bar);
        } else if (SCOREBOARD_CHANNELS.contains(channel)) {
            showScoreboard(session, online);
        } else {
            for (Player member : online) {
                messages.send(member, "challenge.wait.chat", Map.of("dungeon", session.dungeonId()));
            }
        }
        for (Player member : online) {
            gui.openWaitCancel(member, action -> cancelBy(member, "member_cancel"));
        }
    }

    private void showScoreboard(ChallengeSession session, List<Player> online) { new ChallengeServiceDelegate(this).showScoreboard(session, online); }

    void clearScoreboard(ChallengeSession session) { new ChallengeServiceDelegate(this).clearScoreboard(session); }

    private void beginEnter(ChallengeSession session, Party party, String worldName) {
        DungeonInstance instance = registry.get(worldName);
        if (instance == null) {
            allocator.releaseParty(party.id());
            abort(session, "instance_missing");
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            allocator.releaseParty(party.id());
            abort(session, "world_missing");
            return;
        }
        instance.setPartyId(party.id());
        instance.setDifficulty(session.difficulty());
        instance.setDropMode(session.dropMode());
        DungeonConfig typeConfig = configOf.apply(session.dungeonId());
        instance.setType(typeConfig == null ? "normal" : typeConfig.type());
        instance.setTest(session.isTest());
        instance.markStarted();
        session.commitTicket();
        if (runtimeFactory != null) {
            var runtime = runtimeFactory.apply(instance);
            if (runtime != null) {
                instance.setFlowRuntime(runtime);
            }
        }
        if (stateService != null) {
            stateService.register(instance);
        }
        if (instance.flowRuntime() != null) {
            var flowRuntime = instance.flowRuntime();
            instance.startLoop(plugin, dispatcher, event -> {
                flowRuntime.onEvent(event);
                var scriptSession = instance.scriptSession();
                if (scriptSession != null) {
                    scriptSession.onIngress(event);
                }
            }, () -> {
            }, throwable -> {
                messages.warning("console.instance_crashed", Map.of(
                        "instance", instance.instanceId(),
                        "error", String.valueOf(throwable.getMessage())
                ));
                if (stateService != null) {
                    stateService.fail(instance, "crash");
                }
            });
        }
        String instanceId = instance.instanceId();
        Location entryLocation = world.getSpawnLocation();
        var entryRuntime = instance.flowRuntime();
        if (entryRuntime != null && entryRuntime.definition().spawnPoint() != null
                && entryRuntime.definition().spawnPoint().point() != null) {
            var entryPoint = entryRuntime.definition().spawnPoint().point();
            entryLocation = new org.bukkit.Location(world, entryPoint.x(), entryPoint.y(),
                    entryPoint.z(), entryPoint.yaw(), entryPoint.pitch());
        }
        Location target = entryLocation;
        preloadEntryChunks(world, target).whenComplete((ignored, error) ->
                dispatcher.runGlobal(plugin, () -> prepareEnter(
                        session, party, instance, instanceId, target)));
    }

    private void prepareEnter(ChallengeSession session, Party party, DungeonInstance instance,
            String instanceId, Location entryLocation) {
        List<UUID> members = List.copyOf(party.members());
        String partyKey = String.valueOf(party.id());
        String dungeonId = session.dungeonId();
        boolean test = session.isTest();
        List<FlowModules.TempItemDef> entryItems = entryItemsOf(instance);
        AtomicInteger remaining = new AtomicInteger(members.size());
        if (members.isEmpty()) {
            finishEnter(session, instanceId);
            return;
        }
        for (UUID memberId : members) {
            AtomicBoolean settled = new AtomicBoolean();
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                warnEnter(instanceId, memberId, "offline");
                finishIfSettled(session, instanceId, remaining, settled);
                continue;
            }
            TaskToken token = dispatcher.runEntity(plugin, member,
                    () -> enterMember(session, instanceId, entryLocation, partyKey, dungeonId, test,
                            entryItems, memberId, member, remaining, settled),
                    () -> {
                        warnEnter(instanceId, memberId, "retired");
                        finishIfSettled(session, instanceId, remaining, settled);
                    });
            if (token == null) {
                warnEnter(instanceId, memberId, "schedule_rejected");
                finishIfSettled(session, instanceId, remaining, settled);
            }
        }
    }

    private void enterMember(ChallengeSession session, String instanceId, Location entryLocation,
            String partyKey, String dungeonId, boolean test,
            List<FlowModules.TempItemDef> entryItems, UUID memberId, Player member,
            AtomicInteger remaining, AtomicBoolean settled) {
        try {
            if (!member.isOnline()) {
                warnEnter(instanceId, memberId, "offline");
                return;
            }
            if (!test && limitService != null && !member.hasPermission("emakidungeon.bypass.limit")) {
                limitService.record(memberId, dungeonId);
            }
            teleportPermit.accept(memberId);
            snapshots.captureInitial(member, instanceId, dungeonId, partyKey);
            member.teleport(entryLocation);
            Bukkit.getPluginManager().callEvent(
                    new DungeonEnterEvent(instanceId, dungeonId, memberId, partyKey));
            messages.send(member, "challenge.entered", Map.of(
                    "dungeon", dungeonId,
                    "instance", instanceId
            ));
            if (audit != null) {
                audit.write("player", Map.of(
                        "event", "enter",
                        "player", String.valueOf(memberId),
                        "instance", instanceId,
                        "dungeon", dungeonId,
                        "test", String.valueOf(test)));
            }
            if (actionBridge != null) {
                actionBridge.dispatch("dungeon_enter", memberId, Map.of(
                        "instanceId", instanceId,
                        "dungeonId", dungeonId,
                        "player", member.getName(),
                        "partyId", partyKey
                ));
            }
            for (FlowModules.TempItemDef tempItem : entryItems) {
                tempItems.grant(instanceId, member, tempItem.itemId(), tempItem.amount());
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[EmakiDungeon] challenge enter failed | instance=" + instanceId
                    + " | player=" + memberId + " | error=" + exception.getMessage());
        } finally {
            finishIfSettled(session, instanceId, remaining, settled);
        }
    }

    private void finishIfSettled(ChallengeSession session, String instanceId,
            AtomicInteger remaining, AtomicBoolean settled) { new ChallengeServiceDelegate(this).finishIfSettled(session, instanceId, remaining, settled); }

    void finishEnter(ChallengeSession session, String instanceId) { new ChallengeServiceDelegate(this).finishEnter(session, instanceId); }

    private List<FlowModules.TempItemDef> entryItemsOf(DungeonInstance instance) { return new ChallengeServiceDelegate(this).entryItemsOf(instance); }

    private void warnEnter(String instanceId, UUID memberId, String reason) { new ChallengeServiceDelegate(this).warnEnter(instanceId, memberId, reason); }

    private CompletableFuture<Void> preloadEntryChunks(World world, Location entry) { return new ChallengeServiceDelegate(this).preloadEntryChunks(world, entry); }

    public void readyBy(Player player) { new ChallengeServiceDelegate(this).readyBy(player); }

    public void cancelBy(Player player, String reason) { new ChallengeServiceDelegate(this).cancelBy(player, reason); }

    public void onPartyChanged(UUID partyId) { new ChallengeServiceDelegate(this).onPartyChanged(partyId); }

    public boolean hasActiveSession(UUID partyId) { return new ChallengeServiceDelegate(this).hasActiveSession(partyId); }

    public boolean sessionContains(UUID playerId) { return new ChallengeServiceDelegate(this).sessionContains(playerId); }

    public void abort(ChallengeSession session, String reason) { new ChallengeServiceDelegate(this).abort(session, reason); }

    void cleanup(ChallengeSession session, String reason) { new ChallengeServiceDelegate(this).cleanup(session, reason); }

    void closeSessionUi(ChallengeSession session) { new ChallengeServiceDelegate(this).closeSessionUi(session); }

    void settleTicketOnAbort(ChallengeSession session, String reason) { new ChallengeServiceDelegate(this).settleTicketOnAbort(session, reason); }

    static boolean isPlayerSide(String reason) { return ChallengeServiceDelegate.isPlayerSide(reason); }

    private String resolvePapi(Player player, String text) { return new ChallengeServiceDelegate(this).resolvePapi(player, text); }

    private void tick() {
        long now = System.currentTimeMillis();
        for (ChallengeSession session : List.copyOf(sessions.values())) {
            ChallengePhase phase = session.phase();
            if (phase == ChallengePhase.PREPARING && now > session.windowDeadline()) {
                abort(session, "window_timeout");
                continue;
            }
            if (phase == ChallengePhase.ENTERING_WAIT) {
                if (now > session.waitDeadline()) {
                    abort(session, "wait_timeout");
                    continue;
                }
                if (pool.reserveCount(session.dungeonId()) > 0) {
                    Party party = parties.partyById(session.partyId());
                    if (party == null) {
                        abort(session, "party_missing");
                        continue;
                    }
                    Player leader = Bukkit.getPlayer(session.leaderId());
                    if (leader == null) {
                        continue;
                    }
                    if (!party.members().stream().allMatch(memberId -> Bukkit.getPlayer(memberId) != null)) {
                        abort(session, "member_offline");
                        continue;
                    }
                    if (monitor != null) {
                        monitor.markEnterPhase();
                    }
                    String worldName = pool.takeReserve(session.dungeonId());
                    if (worldName != null) {
                        beginEnter(session, party, worldName);
                    }
                }
            }
        }
    }

    UUID partyIdOf(Player player) { return new ChallengeServiceDelegate(this).partyIdOf(player); }

    List<UUID> sessionMembers(ChallengeSession session) { return new ChallengeServiceDelegate(this).sessionMembers(session); }

    private List<Player> onlineMembers(Party party) { return new ChallengeServiceDelegate(this).onlineMembers(party); }

    String render(String key, Map<String, ?> replacements) { return new ChallengeServiceDelegate(this).render(key, replacements); }
}
