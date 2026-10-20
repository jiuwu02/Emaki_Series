package emaki.jiuwu.craft.dungeon.challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Bukkit;
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
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.flow.FlowModules;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.EventIngress.IngressEvent;
import emaki.jiuwu.craft.dungeon.orchestration.DenyReason;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceSlotAllocator;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.snapshot.SnapshotManager;
import emaki.jiuwu.craft.dungeon.world.WorldPool;

public final class ChallengeService {

    private static final long TICK_PERIOD = 10L;
    private static final List<String> SCOREBOARD_CHANNELS = List.of("scoreboard");

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final MessageService messages;
    private final LanguageLoader lang;
    private final DebugLogger debug;
    private final PartyService parties;
    private final InstanceSlotAllocator allocator;
    private final WorldPool pool;
    private final InstanceRegistry registry;
    private final SnapshotManager snapshots;
    private final ChallengeGuiService gui;
    private final Function<String, DungeonConfig> configOf;
    private final Consumer<UUID> teleportPermit;

    private final Map<UUID, ChallengeSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> scoreboardObjective = new ConcurrentHashMap<>();
    private TaskToken tickTask = TaskToken.UNAVAILABLE;
    private volatile emaki.jiuwu.craft.dungeon.action.ActionBridge actionBridge;
    private volatile java.util.function.Function<DungeonInstance,
            emaki.jiuwu.craft.dungeon.flow.FlowRuntime> runtimeFactory;
    private volatile emaki.jiuwu.craft.dungeon.state.PlayerStateService stateService;
    private volatile emaki.jiuwu.craft.dungeon.tempitem.TempItemManager tempItems;

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

    public void bindActionBridge(emaki.jiuwu.craft.dungeon.action.ActionBridge actionBridge) {
        this.actionBridge = actionBridge;
    }

    public void bindInstanceHooks(
            java.util.function.Function<DungeonInstance,
                    emaki.jiuwu.craft.dungeon.flow.FlowRuntime> runtimeFactory,
            emaki.jiuwu.craft.dungeon.state.PlayerStateService stateService,
            emaki.jiuwu.craft.dungeon.tempitem.TempItemManager tempItems) {
        this.runtimeFactory = runtimeFactory;
        this.stateService = stateService;
        this.tempItems = tempItems;
    }

    public void shutdown() {
        tickTask.cancel();
        for (ChallengeSession session : List.copyOf(sessions.values())) {
            cleanup(session, "shutdown");
        }
    }

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
        ChallengeSession session = new ChallengeSession(party.id(), dungeonId, leader.getUniqueId());
        sessions.put(party.id(), session);
        gui.openOptionSelect(leader, "edg_difficulty", "gui.difficulty.title", "gui.difficulty",
                config.difficulties(), option -> onDifficultyPicked(session, leader, option));
        debug.log("dungeon", leader, "challenge_opened", Map.of(
                "dungeon", dungeonId,
                "party", String.valueOf(party.id())
        ));
    }

    private void onDifficultyPicked(ChallengeSession session, Player leader, String option) {
        if (sessions.get(session.partyId()) != session) {
            return;
        }
        DungeonConfig config = configOf.apply(session.dungeonId());
        if (config == null || !config.supportsDifficulty(option)) {
            messages.send(leader, "challenge.enter.invalid_difficulty", Map.of("difficulty", option));
            abort(session, "invalid_difficulty");
            return;
        }
        session.setDifficulty(option);
        session.setPhase(ChallengePhase.SELECT_MODE);
        gui.openOptionSelect(leader, "edg_mode", "gui.mode.title", "gui.mode",
                config.dropModeAllowed(), mode -> onModePicked(session, leader, mode));
    }

    private void onModePicked(ChallengeSession session, Player leader, String mode) {
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
                return false;
            }
        }
        return true;
    }

    public void onReadyRequested(ChallengeSession session, Player player) {
        if (sessions.get(session.partyId()) != session) {
            return;
        }
        if (session.phase() != ChallengePhase.PREPARING) {
            return;
        }
        Party party = parties.partyById(session.partyId());
        if (party == null || !party.contains(player.getUniqueId())) {
            return;
        }
        if (System.currentTimeMillis() > session.windowDeadline()) {
            messages.send(player, "challenge.preparing_expired", Map.of());
            abort(session, "window_timeout");
            return;
        }
        if (!session.ready().add(player.getUniqueId())) {
            return;
        }
        int onlineMembers = 0;
        for (UUID memberId : party.members()) {
            if (Bukkit.getPlayer(memberId) != null) {
                onlineMembers++;
            }
        }
        if (session.ready().size() < onlineMembers) {
            Map<String, Object> replacements = Map.of(
                    "player", player.getName(),
                    "ready", String.valueOf(session.ready().size()),
                    "total", String.valueOf(onlineMembers)
            );
            for (UUID memberId : party.members()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    messages.send(member, "challenge.member_ready", replacements);
                }
            }
            return;
        }
        finalizeReady(session, party);
    }

    private void finalizeReady(ChallengeSession session, Party party) {
        Player leader = Bukkit.getPlayer(session.leaderId());
        DungeonConfig config = configOf.apply(session.dungeonId());
        if (config == null || leader == null) {
            abort(session, "config_missing");
            return;
        }
        for (UUID memberId : party.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                abort(session, "member_offline");
                return;
            }
            if (!member.hasPermission("emakidungeon.dungeon." + session.dungeonId())
                    && !member.hasPermission("emakidungeon.bypass.limit")) {
                messages.send(leader, "challenge.enter.no_dungeon_permission", Map.of("dungeon", session.dungeonId()));
                abort(session, "permission");
                return;
            }
        }
        debug.log("dungeon", leader, "ready_all", Map.of(
                "dungeon", session.dungeonId(),
                "party", String.valueOf(party.id())
        ));
        DenyReason deny = allocator.tryAcquireParty(session.dungeonId(), party.id(),
                pool.reserveCount(session.dungeonId()) > 0);
        if (deny != null) {
            handleDeny(leader, session.dungeonId(), deny);
            abort(session, "deny_" + deny.name());
            return;
        }
        String worldName = pool.takeReserve(session.dungeonId());
        if (worldName != null) {
            beginEnter(session, party, worldName);
            return;
        }
        if (allocator.worldsOf(session.dungeonId()) >= config.poolMaxWorlds()) {
            allocator.releaseParty(party.id());
            messages.send(leader, "challenge.enter.dungeon_full", Map.of("dungeon", session.dungeonId()));
            debug.log("dungeon", leader, "instance_full", Map.of("dungeon", session.dungeonId()));
            abort(session, "deny_DUNGEON_FULL");
            return;
        }
        enterWait(session, party);
    }

    private void handleDeny(Player leader, String dungeonId, DenyReason deny) {
        if (deny == DenyReason.DENY_DUNGEON_FULL || deny == DenyReason.DENY_GLOBAL_FULL) {
            messages.send(leader, "challenge.enter.full", Map.of("dungeon", dungeonId));
            debug.log("dungeon", leader, "instance_full", Map.of("dungeon", dungeonId));
            return;
        }
        messages.send(leader, "challenge.enter.party_busy", Map.of());
    }

    private void enterWait(ChallengeSession session, Party party) {
        DungeonConfig config = configOf.apply(session.dungeonId());
        if (config == null) {
            allocator.releaseParty(party.id());
            abort(session, "config_missing");
            return;
        }
        session.setPhase(ChallengePhase.ENTERING_WAIT);
        session.setWaitDeadline(System.currentTimeMillis() + config.waitTimeoutSeconds() * 1000L);
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

    private void showScoreboard(ChallengeSession session, List<Player> online) {
        if (online.isEmpty()) {
            return;
        }
        org.bukkit.scoreboard.Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        String name = "emd_wait_" + session.partyId().toString().substring(0, 8);
        Objective objective = board.registerNewObjective(name, Criteria.DUMMY,
                MiniMessages.parse(render("challenge.wait.sidebar", Map.of("dungeon", session.dungeonId()))));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.getScore("waiting").setScore(1);
        for (Player member : online) {
            member.setScoreboard(board);
            scoreboardObjective.put(member.getUniqueId(), name);
        }
    }

    private void clearScoreboard(ChallengeSession session) {
        for (UUID memberId : sessionMembers(session)) {
            scoreboardObjective.remove(memberId);
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
    }

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
            instance.startLoop(plugin, dispatcher, flowRuntime::onEvent, () -> {
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
        for (UUID memberId : party.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                continue;
            }
            teleportPermit.accept(memberId);
            snapshots.captureInitial(member, instanceId, session.dungeonId(), String.valueOf(party.id()));
            member.teleport(world.getSpawnLocation());
            messages.send(member, "challenge.entered", Map.of(
                    "dungeon", session.dungeonId(),
                    "instance", instanceId
            ));
            instance.ingress().post(new IngressEvent("onEnter", String.valueOf(memberId), Map.of(
                    "dungeonId", session.dungeonId(),
                    "instanceId", instanceId
            )));
            if (actionBridge != null) {
                actionBridge.dispatch("dungeon_enter", memberId, Map.of(
                        "instanceId", instanceId,
                        "dungeonId", session.dungeonId(),
                        "player", member.getName(),
                        "partyId", String.valueOf(party.id())
                ));
            }
        }
        if (tempItems != null && instance.flowRuntime() != null) {
            for (FlowModules.TempItemDef tempItem : instance.flowRuntime().definition().tempItems().values()) {
                if (!tempItem.head().enabled() || !"entry".equals(tempItem.timing())) {
                    continue;
                }
                for (UUID memberId : party.members()) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null) {
                        tempItems.grant(instanceId, member, tempItem.itemId(), tempItem.amount());
                    }
                }
            }
        }
        debug.log("dungeon", Bukkit.getPlayer(session.leaderId()), "instance_assigned", Map.of(
                "instance", instanceId,
                "dungeon", session.dungeonId()
        ));
        closeSessionUi(session);
        sessions.remove(session.partyId());
    }

    public void readyBy(Player player) {
        ChallengeSession session = sessions.get(partyIdOf(player));
        if (session == null) {
            messages.send(player, "challenge.cancel.no_session", Map.of());
            return;
        }
        onReadyRequested(session, player);
    }

    public void cancelBy(Player player, String reason) {
        ChallengeSession session = sessions.get(partyIdOf(player));
        if (session == null) {
            messages.send(player, "challenge.cancel.no_session", Map.of());
            return;
        }
        Party party = parties.partyById(session.partyId());
        if (party == null || !party.contains(player.getUniqueId())) {
            return;
        }
        abort(session, reason);
    }

    public void onPartyChanged(UUID partyId) {
        ChallengeSession session = sessions.get(partyId);
        if (session != null
                && (session.phase() == ChallengePhase.PREPARING
                || session.phase() == ChallengePhase.ENTERING_WAIT)) {
            abort(session, "member_left");
        }
    }

    public boolean hasActiveSession(UUID partyId) {
        return partyId != null && sessions.containsKey(partyId);
    }

    public boolean sessionContains(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        for (ChallengeSession session : sessions.values()) {
            Party party = parties.partyById(session.partyId());
            if (party != null && party.contains(playerId)) {
                return true;
            }
        }
        return false;
    }

    public void abort(ChallengeSession session, String reason) {
        if (sessions.remove(session.partyId(), session)) {
            cleanup(session, reason);
        }
    }

    private void cleanup(ChallengeSession session, String reason) {
        Party party = parties.partyById(session.partyId());
        if (party != null && session.phase() == ChallengePhase.ENTERING_WAIT) {
            allocator.releaseParty(party.id());
        } else if (party != null && session.phase() == ChallengePhase.PREPARING) {
            allocator.releaseParty(party.id());
        }
        closeSessionUi(session);
        debug.logRaw("dungeon", (java.util.UUID) null, "challenge cancelled | party=" + session.partyId()
                + " | dungeon=" + session.dungeonId() + " | reason=" + reason);
        if (party != null) {
            for (UUID memberId : party.members()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    messages.send(member, "challenge.cancelled", Map.of("reason", reason));
                }
            }
        }
    }

    private void closeSessionUi(ChallengeSession session) {
        BossBar bar = session.waitBar();
        if (bar != null) {
            bar.removeAll();
            session.setWaitBar(null);
        }
        clearScoreboard(session);
        for (UUID memberId : sessionMembers(session)) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                gui.close(member);
            }
        }
    }

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
                    String worldName = pool.takeReserve(session.dungeonId());
                    if (worldName != null) {
                        beginEnter(session, party, worldName);
                    }
                }
            }
        }
    }

    private UUID partyIdOf(Player player) {
        Party party = parties.partyOf(player.getUniqueId());
        return party == null ? null : party.id();
    }

    private List<UUID> sessionMembers(ChallengeSession session) {
        Party party = parties.partyById(session.partyId());
        return party == null ? List.of() : party.members();
    }

    private List<Player> onlineMembers(Party party) {
        List<Player> online = new ArrayList<>(party.size());
        for (UUID memberId : party.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                online.add(member);
            }
        }
        return online;
    }

    private String render(String key, Map<String, ?> replacements) {
        String template = lang.getMessage(key);
        if (template == null || template.equals(key)) {
            return key;
        }
        return replacements == null || replacements.isEmpty()
                ? template
                : Texts.formatTemplate(template, replacements);
    }
}
