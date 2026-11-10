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

final class ChallengeServiceDelegate {

    private final ChallengeService ownerRef;

    ChallengeServiceDelegate(ChallengeService ownerRef) {
        this.ownerRef = ownerRef;
    }

    void bindAudit(AuditService audit) {
        ownerRef.audit = audit;
    }

    void bindActionBridge(ActionBridge actionBridge) {
        ownerRef.actionBridge = actionBridge;
    }

    void bindInstanceHooks( Function<DungeonInstance, FlowRuntime> runtimeFactory, PlayerStateService stateService, TempItemManager tempItems) {
        ownerRef.runtimeFactory = runtimeFactory;
        ownerRef.stateService = stateService;
        ownerRef.tempItems = tempItems;
    }

    void bindLimitService(LimitService limitService) {
        ownerRef.limitService = limitService;
    }

    void bindScriptManager(ScriptRuntimeManager scriptManager) {
        ownerRef.scriptManager = scriptManager;
    }

    void bindMonitor(PerformanceMonitor monitor) {
        ownerRef.monitor = monitor;
    }

    void bindTicketService(TicketService tickets) {
        ownerRef.tickets = tickets;
    }

    void fireTrigger(TriggerType type, String dungeonId, Map<String, String> context) {
        if (ownerRef.scriptManager != null) {
            ownerRef.scriptManager.fireTrigger(type, null, dungeonId, context);
        }
    }

    void shutdown() {
        ownerRef.tickTask.cancel();
        for (ChallengeSession session : List.copyOf(ownerRef.sessions.values())) {
            ownerRef.cleanup(session, "shutdown");
        }
    }

    void onDifficultyPicked(ChallengeSession session, Player leader, String option) {
        if (ownerRef.sessions.get(session.partyId()) != session) {
            return;
        }
        DungeonConfig config = ownerRef.configOf.apply(session.dungeonId());
        if (config == null || !config.supportsDifficulty(option)) {
            ownerRef.messages.send(leader, "challenge.enter.invalid_difficulty", Map.of("difficulty", option));
            ownerRef.abort(session, "invalid_difficulty");
            return;
        }
        session.setDifficulty(option);
        session.setPhase(ChallengePhase.SELECT_MODE);
        ownerRef.gui.openOptionSelect(leader, "edg_mode", "gui.mode.title", "gui.mode",
                config.dropModeAllowed(), mode -> ownerRef.onModePicked(session, leader, mode));
    }

    void onReadyRequested(ChallengeSession session, Player player) {
        if (ownerRef.sessions.get(session.partyId()) != session) {
            return;
        }
        if (session.phase() != ChallengePhase.PREPARING) {
            return;
        }
        Party party = ownerRef.parties.partyById(session.partyId());
        if (party == null || !party.contains(player.getUniqueId())) {
            return;
        }
        if (System.currentTimeMillis() > session.windowDeadline()) {
            ownerRef.messages.send(player, "challenge.preparing_expired", Map.of());
            ownerRef.abort(session, "window_timeout");
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
                    ownerRef.messages.send(member, "challenge.member_ready", replacements);
                }
            }
            return;
        }
        String abortReason = ownerRef.finalizeReady(session, party);
        if (abortReason != null) {
            ownerRef.debug.logRaw("dungeon", (UUID) null,
                    "ready finalize rejected | party=" + party.id() + " | reason=" + abortReason);
        }
    }

    static String mapAbortReason(String reason) {
        if (reason == null) {
            return ErrorCodes.DENY_PARTY_INVALID;
        }
        if (reason.startsWith("limit_")) {
            return ChallengeService.limitErrorCodeByName(reason.substring("limit_".length()));
        }
        return switch (reason) {
            case "config_missing" -> ErrorCodes.DUNGEON_NOT_FOUND;
            case "permission" -> ErrorCodes.DENY_PERMISSION;
            case "deny_DENY_GLOBAL_FULL" -> ErrorCodes.DENY_GLOBAL_FULL;
            case "deny_DENY_DUNGEON_FULL" -> ErrorCodes.DENY_DUNGEON_FULL;
            default -> ErrorCodes.DENY_PARTY_INVALID;
        };
    }

    static String limitErrorCode(LimitService.Verdict verdict) {
        return switch (verdict) {
            case DENY_COOLDOWN -> ErrorCodes.DENY_COOLDOWN;
            case DENY_DAILY_LIMIT -> ErrorCodes.DENY_DAILY_LIMIT;
            case DENY_WEEKLY_LIMIT -> ErrorCodes.DENY_WEEKLY_LIMIT;
            case DENY_MONTHLY_LIMIT -> ErrorCodes.DENY_MONTHLY_LIMIT;
            default -> ErrorCodes.DENY_WINDOW;
        };
    }

    static String limitErrorCodeByName(String verdictName) {
        return switch (verdictName) {
            case "DENY_COOLDOWN" -> ErrorCodes.DENY_COOLDOWN;
            case "DENY_DAILY_LIMIT" -> ErrorCodes.DENY_DAILY_LIMIT;
            case "DENY_WEEKLY_LIMIT" -> ErrorCodes.DENY_WEEKLY_LIMIT;
            case "DENY_MONTHLY_LIMIT" -> ErrorCodes.DENY_MONTHLY_LIMIT;
            case "DENY_DAILY_WINDOW", "DENY_WEEKLY_DAY", "DENY_MONTHLY_WEEK" ->
                    ErrorCodes.DENY_WINDOW;
            default -> verdictName;
        };
    }

    static String limitMessageKey(LimitService.Verdict verdict) {
        return switch (verdict) {
            case DENY_COOLDOWN -> "challenge.enter.cooldown";
            case DENY_DAILY_LIMIT -> "challenge.enter.daily_limit";
            case DENY_WEEKLY_LIMIT -> "challenge.enter.weekly_limit";
            case DENY_MONTHLY_LIMIT -> "challenge.enter.monthly_limit";
            case DENY_DAILY_WINDOW -> "challenge.enter.daily_window";
            case DENY_WEEKLY_DAY -> "challenge.enter.weekly_day";
            case DENY_MONTHLY_WEEK -> "challenge.enter.monthly_week";
            default -> "challenge.enter.daily_limit";
        };
    }

    void handleDeny(Player leader, String dungeonId, DenyReason deny) {
        if (deny == DenyReason.DENY_DUNGEON_FULL || deny == DenyReason.DENY_GLOBAL_FULL) {
            ownerRef.messages.send(leader, "challenge.enter.full", Map.of("dungeon", dungeonId));
            ownerRef.debug.log("dungeon", leader, "instance_full", Map.of("dungeon", dungeonId));
            ownerRef.fireTrigger(TriggerType.onInstanceFull,
                    dungeonId, Map.of("dungeon", dungeonId));
            return;
        }
        ownerRef.messages.send(leader, "challenge.enter.party_busy", Map.of());
    }

    void showScoreboard(ChallengeSession session, List<Player> online) {
        if (online.isEmpty()) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        String name = "emd_wait_" + session.partyId().toString().substring(0, 8);
        Objective objective = board.registerNewObjective(name, Criteria.DUMMY,
                MiniMessages.parse(ownerRef.render("challenge.wait.sidebar", Map.of("dungeon", session.dungeonId()))));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.getScore("waiting").setScore(1);
        for (Player member : online) {
            member.setScoreboard(board);
            ownerRef.scoreboardObjective.put(member.getUniqueId(), name);
        }
    }

    void clearScoreboard(ChallengeSession session) {
        for (UUID memberId : ownerRef.sessionMembers(session)) {
            ownerRef.scoreboardObjective.remove(memberId);
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
    }

    void finishIfSettled(ChallengeSession session, String instanceId, AtomicInteger remaining, AtomicBoolean settled) {
        if (!settled.compareAndSet(false, true)) {
            return;
        }
        if (remaining.decrementAndGet() > 0) {
            return;
        }
        ownerRef.dispatcher.runGlobal(ownerRef.plugin, () -> ownerRef.finishEnter(session, instanceId));
    }

    void finishEnter(ChallengeSession session, String instanceId) {
        ownerRef.debug.log("dungeon", Bukkit.getPlayer(session.leaderId()), "instance_assigned", Map.of(
                "instance", instanceId,
                "dungeon", session.dungeonId()
        ));
        if (ownerRef.monitor != null) {
            ownerRef.monitor.markEnterPhase();
        }
        ownerRef.closeSessionUi(session);
        ownerRef.sessions.remove(session.partyId());
    }

    List<FlowModules.TempItemDef> entryItemsOf(DungeonInstance instance) {
        if (ownerRef.tempItems == null) {
            return List.of();
        }
        FlowRuntime runtime = instance.flowRuntime();
        if (runtime == null) {
            return List.of();
        }
        List<FlowModules.TempItemDef> entryItems = new ArrayList<>();
        for (FlowModules.TempItemDef tempItem : runtime.definition().tempItems().values()) {
            if (tempItem.head().enabled() && "entry".equals(tempItem.timing())) {
                entryItems.add(tempItem);
            }
        }
        return entryItems;
    }

    void warnEnter(String instanceId, UUID memberId, String reason) {
        ownerRef.messages.warning("console.challenge_enter_skipped", Map.of(
                "instance", String.valueOf(instanceId),
                "player", String.valueOf(memberId),
                "reason", String.valueOf(reason)));
    }

    CompletableFuture<Void> preloadEntryChunks(World world, Location entry) {
        int radius = 8;
        int chunkX = entry.getBlockX() >> 4;
        int chunkZ = entry.getBlockZ() >> 4;
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                futures.add(world.getChunkAtAsync(chunkX + dx, chunkZ + dz));
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .exceptionally(error -> null);
    }

    void readyBy(Player player) {
        ChallengeSession session = ownerRef.sessions.get(ownerRef.partyIdOf(player));
        if (session == null) {
            ownerRef.messages.send(player, "challenge.ready.no_session", Map.of());
            return;
        }
        ownerRef.onReadyRequested(session, player);
    }

    void cancelBy(Player player, String reason) {
        ChallengeSession session = ownerRef.sessions.get(ownerRef.partyIdOf(player));
        if (session == null) {
            ownerRef.messages.send(player, "challenge.cancel.no_session", Map.of());
            return;
        }
        Party party = ownerRef.parties.partyById(session.partyId());
        if (party == null || !party.contains(player.getUniqueId())) {
            return;
        }
        ownerRef.abort(session, reason);
    }

    void onPartyChanged(UUID partyId) {
        ChallengeSession session = ownerRef.sessions.get(partyId);
        if (session != null
                && (session.phase() == ChallengePhase.PREPARING
                || session.phase() == ChallengePhase.ENTERING_WAIT)) {
            ownerRef.abort(session, "member_left");
        }
    }

    boolean hasActiveSession(UUID partyId) {
        return partyId != null && ownerRef.sessions.containsKey(partyId);
    }

    boolean sessionContains(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        for (ChallengeSession session : ownerRef.sessions.values()) {
            Party party = ownerRef.parties.partyById(session.partyId());
            if (party != null && party.contains(playerId)) {
                return true;
            }
        }
        return false;
    }

    void abort(ChallengeSession session, String reason) {
        if (ownerRef.sessions.remove(session.partyId(), session)) {
            Bukkit.getPluginManager().callEvent(
                    new emaki.jiuwu.craft.dungeon.api.event.DungeonChallengeCancelEvent(
                            session.dungeonId(), reason));
            ownerRef.cleanup(session, reason);
        }
    }

    void cleanup(ChallengeSession session, String reason) {
        Party party = ownerRef.parties.partyById(session.partyId());
        if (party != null && session.phase() == ChallengePhase.ENTERING_WAIT) {
            ownerRef.allocator.releaseParty(party.id());
        } else if (party != null && session.phase() == ChallengePhase.PREPARING) {
            ownerRef.allocator.releaseParty(party.id());
        }
        ownerRef.closeSessionUi(session);
        ownerRef.debug.logRaw("dungeon", (UUID) null, "challenge cancelled | party=" + session.partyId()
                + " | dungeon=" + session.dungeonId() + " | reason=" + reason);
        if (party != null) {
            for (UUID memberId : party.members()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    ownerRef.messages.send(member, "challenge.cancelled", Map.of("reason", reason));
                }
            }
        }
        ownerRef.settleTicketOnAbort(session, reason);
    }

    void closeSessionUi(ChallengeSession session) {
        BossBar bar = session.waitBar();
        if (bar != null) {
            bar.removeAll();
            session.setWaitBar(null);
        }
        ownerRef.clearScoreboard(session);
        for (UUID memberId : ownerRef.sessionMembers(session)) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                ownerRef.gui.close(member);
            }
        }
    }

    void settleTicketOnAbort(ChallengeSession session, String reason) {
        if (session.isTicketCommitted() || session.ticketItems().isEmpty()) {
            return;
        }
        boolean playerSide = ChallengeService.isPlayerSide(reason);
        String segment = playerSide
                && !"member_cancel".equals(reason) && !"member_left".equals(reason)
                ? "dungeon_fail" : "dungeon_exit";
        if (!playerSide && ownerRef.tickets != null) {
            for (var entry : session.ticketItems().entrySet()) {
                try {
                    ownerRef.tickets.refund(entry.getKey(), entry.getValue());
                } catch (RuntimeException ignored) {
                    FailureRecorder.recordIgnoredFailure("EmakiDungeon", "ChallengeService.java#1", ignored);
                }
            }
        }
        String safeReason = reason == null ? "" : reason;
        for (UUID memberId : ownerRef.sessionMembers(session)) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                ownerRef.messages.send(member, playerSide
                        ? "challenge.ticket.consumed" : "challenge.ticket.refunded",
                        Map.of("dungeon", session.dungeonId(), "reason", safeReason));
            }
            if (ownerRef.actionBridge != null) {
                ownerRef.actionBridge.dispatch(segment, memberId, Map.of(
                        "reason", safeReason,
                        "ticket_refunded", Boolean.toString(!playerSide),
                        "dungeon", session.dungeonId()));
            }
        }
        session.ticketItems().clear();
    }

    static boolean isPlayerSide(String reason) {
        return reason != null && (ChallengeService.PLAYER_SIDE_REASONS.contains(reason)
                || reason.startsWith("limit_"));
    }

    String resolvePapi(Player player, String text) {
        if (player == null || text == null
                || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (RuntimeException ignored) {
            return text;
        }
    }

    UUID partyIdOf(Player player) {
        Party party = ownerRef.parties.partyOf(player.getUniqueId());
        return party == null ? null : party.id();
    }

    List<UUID> sessionMembers(ChallengeSession session) {
        Party party = ownerRef.parties.partyById(session.partyId());
        return party == null ? List.of() : party.members();
    }

    List<Player> onlineMembers(Party party) {
        List<Player> online = new ArrayList<>(party.size());
        for (UUID memberId : party.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                online.add(member);
            }
        }
        return online;
    }

    String render(String key, Map<String, ?> replacements) {
        String template = ownerRef.lang.getMessage(key);
        if (template == null || template.equals(key)) {
            return key;
        }
        return replacements == null || replacements.isEmpty()
                ? template
                : Texts.formatTemplate(template, replacements);
    }

}
