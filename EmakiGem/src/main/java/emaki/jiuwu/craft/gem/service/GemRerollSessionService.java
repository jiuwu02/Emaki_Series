package emaki.jiuwu.craft.gem.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.model.GemRerollSessionView;
import emaki.jiuwu.craft.gem.listener.GemItemObtainListener;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;

public final class GemRerollSessionService {

    public enum OperationType {
        FULL,
        VALUE
    }

    public enum TerminalState {
        OPEN,
        CONFIRMED,
        ABANDONED,
        EXPIRED,
        FAILED
    }

    public enum TerminationReason {
        NONE(""),
        CONFIRMED("confirmed"),
        USER_CANCEL("user_cancel"),
        GUI_CLOSE("gui_close"),
        PLAYER_QUIT("player_quit"),
        PLAYER_KICK("player_kick"),
        RELOAD("reload"),
        DISABLE("disable"),
        EXPIRED("expired"),
        INSTANCE_CHANGED("instance_changed"),
        WRITE_FAILED("write_failed"),
        CONFIG_INCOMPATIBLE("config_incompatible"),
        RECOVERY_UNSAFE("recovery_unsafe"),
        RECOVERY_CONFLICT("recovery_conflict");

        private final String key;

        TerminationReason(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public record OpenResult(boolean success, String errorKey, Session session) {
        public static OpenResult failure(String errorKey) {
            return new OpenResult(false, errorKey, null);
        }

        public static OpenResult success(Session session) {
            return new OpenResult(true, "", session);
        }
    }

    public record ActionResult(boolean success, String errorKey, Session session) {
        public static ActionResult failure(String errorKey, Session session) {
            return new ActionResult(false, errorKey, session);
        }

        public static ActionResult success(Session session) {
            return new ActionResult(true, "", session);
        }
    }

    public static final class Session {
        private final UUID operatorId;
        private final String operationId;
        private final String instanceId;
        private final GemItemInstance original;
        private final GemItemInstance candidate;
        private final OperationType operationType;
        private final GemEconomyService.ChargeResult chargeResult;
        private final long createdAt;
        private final long expiryAt;
        private final String configFingerprint;
        private final String instanceFingerprint;
        private volatile TerminalState terminalState;
        private volatile TerminationReason terminationReason;
        private volatile boolean compensationPending;

        private Session(UUID operatorId,
                String operationId,
                GemItemInstance original,
                GemItemInstance candidate,
                OperationType operationType,
                GemEconomyService.ChargeResult chargeResult,
                long createdAt,
                long expiryAt,
                String configFingerprint,
                String instanceFingerprint) {
            this.operatorId = operatorId;
            this.operationId = operationId;
            this.instanceId = original.instanceId();
            this.original = original;
            this.candidate = candidate;
            this.operationType = operationType;
            this.chargeResult = chargeResult;
            this.createdAt = createdAt;
            this.expiryAt = expiryAt;
            this.configFingerprint = configFingerprint;
            this.instanceFingerprint = instanceFingerprint;
            this.terminalState = TerminalState.OPEN;
            this.terminationReason = TerminationReason.NONE;
        }

        public UUID operatorId() { return operatorId; }
        public String operationId() { return operationId; }
        public String instanceId() { return instanceId; }
        public GemItemInstance original() { return original; }
        public GemItemInstance candidate() { return candidate; }
        public OperationType operationType() { return operationType; }
        public GemEconomyService.ChargeResult chargeResult() { return chargeResult; }
        public long createdAt() { return createdAt; }
        public long expiryAt() { return expiryAt; }
        public String configFingerprint() { return configFingerprint; }
        public String instanceFingerprint() { return instanceFingerprint; }
        public TerminalState terminalState() { return terminalState; }
        public TerminationReason terminationReason() { return terminationReason; }
        public boolean compensationPending() { return compensationPending; }

        private void terminal(TerminalState state, TerminationReason reason, boolean pending) {
            terminalState = state;
            terminationReason = reason == null ? TerminationReason.NONE : reason;
            compensationPending = pending;
        }
    }

    private record ConfirmedEntry(Session session, long confirmedAt, long expiryAt) {
    }

    private static final long DEFAULT_CONFIRMED_TTL_MILLIS = 30_000L;
    private static final int DEFAULT_CONFIRMED_CAPACITY = 256;
    private static final String DEBUG_REROLL_MODULE = "state";

    private final EmakiGemPlugin plugin;
    private final GemRerollCandidateGenerator generator;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, ConfirmedEntry> recentConfirmed = new ConcurrentHashMap<>();
    private final Map<UUID, Object> sessionLocks = new ConcurrentHashMap<>();
    private final long confirmedTtlMillis;
    private final int confirmedCapacity;

    public GemRerollSessionService(EmakiGemPlugin plugin) {
        this(plugin, DEFAULT_CONFIRMED_TTL_MILLIS, DEFAULT_CONFIRMED_CAPACITY);
    }

    GemRerollSessionService(EmakiGemPlugin plugin,
            long confirmedTtlMillis,
            int confirmedCapacity) {
        this.plugin = plugin;
        this.generator = new GemRerollCandidateGenerator();
        this.confirmedTtlMillis = Math.max(1_000L, confirmedTtlMillis);
        this.confirmedCapacity = Math.max(1, confirmedCapacity);
    }

    private long sessionTtlMillis() {
        return Math.max(1_000L, plugin.appConfig().reroll().sessionTtlMillis());
    }

    public OpenResult open(Player player, OperationType operationType) {
        return open(player, operationType, null);
    }

    public OpenResult open(Player player, OperationType operationType, String requestedOperationId) {
        if (player == null || operationType == null) {
            return OpenResult.failure("gem.reroll.player_required");
        }
        if (!owned(player)) {
            return OpenResult.failure("gem.reroll.wrong_thread");
        }
        pruneRecentConfirmed();
        UUID playerId = player.getUniqueId();
        Object lock = sessionLocks.computeIfAbsent(playerId, ignored -> new Object());
        synchronized (lock) {
            Session existing = activeOwned(player);
            if (existing != null) {
                return OpenResult.success(existing);
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            GemItemInstance original = plugin.itemMatcher().readStoredGemInstance(held);
            if (original == null) {
                return OpenResult.failure("gem.reroll.gem_required");
            }
            GemDefinition definition = plugin.gemLoader().get(original.gemId());
            if (definition == null || !definition.reroll().enabled()) {
                return OpenResult.failure("gem.reroll.disabled");
            }
            GemRerollCandidateGenerator.GenerationResult generated = operationType == OperationType.FULL
                    ? generator.fullReroll(original, definition, definition.reroll().group())
                    : generator.valueReroll(original, definition, definition.reroll().group());
            if (!generated.success()) {
                return OpenResult.failure(generated.errorKey());
            }
            GemDefinition.CostConfig cost = operationType == OperationType.FULL
                    ? definition.reroll().fullCost()
                    : definition.reroll().valueCost();
            String operationId = journal().begin(requestedOperationId,
                    "gem-reroll-" + operationType.name().toLowerCase(), playerId);
            GemEconomyService.ChargeResult charge = plugin.economyService().charge(player, cost,
                    Map.of("gem_id", original.gemId(), "stage", original.stage(), "level", original.level()));
            if (!charge.success()) {
                journal().failedCharge(operationId, charge);
                return OpenResult.failure(charge.errorKey());
            }
            journal().charged(operationId, charge);
            long now = System.currentTimeMillis();
            String configFingerprint = configFingerprint(definition, operationType);
            String instanceFingerprint = instanceFingerprint(original);
            Session created = new Session(playerId, operationId, original, generated.candidate(), operationType,
                    charge, now, now + sessionTtlMillis(), configFingerprint, instanceFingerprint);
            journal().rerollGenerated(operationId, operationType, original, generated.candidate(),
                    configFingerprint, instanceFingerprint, created.createdAt(), created.expiryAt());
            sessions.put(playerId, created);
            ConfirmedEntry removed = recentConfirmed.remove(playerId);
            if (removed != null) {
                journal().archiveTerminal(removed.session().operationId());
            }
            return OpenResult.success(created);
        }
    }

    public ActionResult confirm(Player player) {
        if (player == null) {
            return ActionResult.failure("gem.reroll.player_required", null);
        }
        if (!owned(player)) {
            return ActionResult.failure("gem.reroll.wrong_thread", null);
        }
        pruneRecentConfirmed();
        Session session = activeOwned(player);
        if (session == null) {
            ConfirmedEntry confirmed = recentConfirmed.get(player.getUniqueId());
            if (confirmed == null) {
                return ActionResult.failure("gem.reroll.session_missing", null);
            }
            journal().duplicateConfirm(confirmed.session().operationId());
            return ActionResult.success(confirmed.session());
        }
        synchronized (session) {
            if (session.terminalState() == TerminalState.CONFIRMED) {
                journal().duplicateConfirm(session.operationId());
                return ActionResult.success(session);
            }
            if (session.terminalState() != TerminalState.OPEN) {
                return ActionResult.failure("gem.reroll.session_closed", session);
            }
            GemDefinition definition = plugin.gemLoader().get(session.original().gemId());
            if (definition == null
                    || !session.configFingerprint().equals(configFingerprint(definition, session.operationType()))) {
                terminateOwned(player, session, TerminalState.FAILED, TerminationReason.CONFIG_INCOMPATIBLE);
                return ActionResult.failure("gem.reroll.config_incompatible", session);
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            GemItemInstance current = plugin.itemMatcher().readStoredGemInstance(held);
            if (!matches(session, current)) {
                terminateOwned(player, session, TerminalState.FAILED, TerminationReason.INSTANCE_CHANGED);
                return ActionResult.failure("gem.reroll.instance_changed", session);
            }
            if (!plugin.itemFactory().applyInstance(held, session.candidate())) {
                terminateOwned(player, session, TerminalState.FAILED, TerminationReason.WRITE_FAILED);
                return ActionResult.failure("gem.reroll.write_failed", session);
            }
            player.getInventory().setItemInMainHand(held);
            observeCandidateRefresh(player, session,
                    GemItemObtainListener.refreshInventory(plugin, player));
            session.terminal(TerminalState.CONFIRMED, TerminationReason.CONFIRMED, false);
            sessions.remove(player.getUniqueId(), session);
            journal().rerollTerminal(session.operationId(), GemOperationJournal.Phase.CONFIRMED,
                    TerminationReason.CONFIRMED.key(), false);
            long confirmedAt = System.currentTimeMillis();
            recentConfirmed.put(player.getUniqueId(),
                    new ConfirmedEntry(session, confirmedAt, confirmedAt + confirmedTtlMillis));
            pruneRecentConfirmed();
            return ActionResult.success(session);
        }
    }

    public boolean abandon(UUID playerId) {
        return abandon(playerId, TerminationReason.USER_CANCEL);
    }

    public boolean abandon(UUID playerId, TerminationReason reason) {
        if (playerId == null) {
            return false;
        }
        Session session = sessions.get(playerId);
        if (session == null) {
            return false;
        }
        Player player = Bukkit.getPlayer(playerId);
        return terminate(session, player, TerminalState.ABANDONED,
                reason == null ? TerminationReason.USER_CANCEL : reason, null);
    }

    public CompletableFuture<Void> clearAllAsync(TerminationReason reason) {
        List<CompletableFuture<Void>> completions = new ArrayList<>();
        for (Session session : List.copyOf(sessions.values())) {
            CompletableFuture<Void> completion = new CompletableFuture<>();
            boolean transitioned = terminate(session, Bukkit.getPlayer(session.operatorId()), TerminalState.ABANDONED,
                    reason == null ? TerminationReason.RELOAD : reason, completion);
            if (transitioned) {
                completions.add(completion);
            }
        }
        sessions.clear();
        sessionLocks.clear();
        clearRecentConfirmed();
        return CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new));
    }

    public void clearAll() {
        clearAll(TerminationReason.DISABLE);
    }

    public void clearAll(TerminationReason reason) {
        clearAllAsync(reason);
    }

    public Optional<Session> session(UUID playerId) {
        return Optional.ofNullable(active(playerId));
    }

    public Optional<GemRerollSessionView> view(UUID playerId) {
        Session session = active(playerId);
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(toView(session));
    }

    public GemRerollSessionView toView(Session session) {
        return new GemRerollSessionView(
                session.operatorId(),
                session.instanceId(),
                session.operationType().name().toLowerCase(),
                session.original().affixes(),
                session.candidate().affixes(),
                session.original().stage(),
                session.original().updatedAt(),
                session.createdAt(),
                session.expiryAt(),
                session.terminalState().name().toLowerCase());
    }

    public boolean matchesCurrentTarget(Player player, Session session) {
        if (player == null || session == null || !owned(player)) {
            return false;
        }
        return matches(session, plugin.itemMatcher().readStoredGemInstance(
                player.getInventory().getItemInMainHand()));
    }

    public CompletableFuture<Void> recover(GemOperationJournal.RerollSnapshot snapshot) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        if (snapshot == null || !snapshot.complete()) {
            completion.complete(null);
            return completion;
        }
        OperationType operationType = operationType(snapshot.operationType());
        GemDefinition definition = plugin.gemLoader().get(snapshot.original().gemId());
        if (operationType == null || definition == null
                || !snapshot.configFingerprint().equals(configFingerprint(definition, operationType))) {
            return compensateRecovered(snapshot, TerminalState.FAILED,
                    TerminationReason.CONFIG_INCOMPATIBLE);
        }
        Player player = Bukkit.getPlayer(snapshot.playerId());
        if (player == null || !player.isOnline()) {
            journal().rerollTerminal(snapshot.operationId(), GemOperationJournal.Phase.FAILED,
                    TerminationReason.RECOVERY_UNSAFE.key(), true);
            completion.complete(null);
            return completion;
        }
        Runnable restore = () -> {
            if (System.currentTimeMillis() >= snapshot.expiryAt()) {
                compensateRecoveredOwned(snapshot, player, TerminalState.EXPIRED, TerminationReason.EXPIRED)
                        .whenComplete((ignored, failure) -> complete(completion, failure));
                return;
            }
            GemItemInstance current = plugin.itemMatcher().readStoredGemInstance(
                    player.getInventory().getItemInMainHand());
            if (current == null
                    || !snapshot.original().instanceId().equals(current.instanceId())
                    || !snapshot.instanceFingerprint().equals(instanceFingerprint(current))) {
                compensateRecoveredOwned(snapshot, player, TerminalState.FAILED, TerminationReason.INSTANCE_CHANGED)
                        .whenComplete((ignored, failure) -> complete(completion, failure));
                return;
            }
            Object lock = sessionLocks.computeIfAbsent(snapshot.playerId(), ignored -> new Object());
            synchronized (lock) {
                Session existing = sessions.get(snapshot.playerId());
                if (existing != null && !existing.operationId().equals(snapshot.operationId())) {
                    compensateRecoveredOwned(snapshot, player, TerminalState.FAILED, TerminationReason.RECOVERY_CONFLICT)
                            .whenComplete((ignored, failure) -> complete(completion, failure));
                    return;
                }
                GemEconomyService.ChargeResult charge = GemEconomyService.ChargeResult.success(
                        snapshot.currencies(), snapshot.materials(), null);
                Session restored = new Session(snapshot.playerId(), snapshot.operationId(),
                        snapshot.original(), snapshot.candidate(), operationType, charge,
                        snapshot.createdAt(), snapshot.expiryAt(), snapshot.configFingerprint(),
                        snapshot.instanceFingerprint());
                sessions.put(snapshot.playerId(), restored);
            }
            completion.complete(null);
        };
        scheduleOwned(player, restore, () -> {
            journal().rerollTerminal(snapshot.operationId(), GemOperationJournal.Phase.FAILED,
                    TerminationReason.RECOVERY_UNSAFE.key(), true);
            completion.complete(null);
        });
        return completion;
    }

    private void observeCandidateRefresh(Player player,
            Session session,
            GemItemObtainListener.RefreshOutcome outcome) {
        String candidateFingerprint = instanceFingerprint(session.candidate());
        GemItemInstance presented = plugin.itemMatcher().readStoredGemInstance(
                player.getInventory().getItemInMainHand());
        String presentedFingerprint = instanceFingerprint(presented);
        boolean matched = candidateFingerprint.equals(presentedFingerprint);
        if (outcome.refreshed() && matched) {
            return;
        }
        String reason = outcome.refreshed() ? "candidate_not_presented" : outcome.key();
        journal().rerollRefreshDegraded(session.operationId(), reason);
        Map<String, Object> fields = Map.of(
                "operation_id", session.operationId(),
                "instance_id", session.instanceId(),
                "outcome", outcome.key(),
                "reason", reason,
                "expected_fingerprint", candidateFingerprint,
                "presented_fingerprint", presentedFingerprint
        );
        if (plugin.messageService() != null) {
            plugin.messageService().warning("console.reroll_refresh_degraded", fields);
        }
        if (plugin.debugLogger() != null) {
            plugin.debugLogger().log(DEBUG_REROLL_MODULE, player, "state.reroll_refresh_degraded", fields);
        }
    }

    private Session active(UUID playerId) {
        pruneRecentConfirmed();
        Session session = sessions.get(playerId);
        if (session == null) {
            return null;
        }
        if (session.terminalState() != TerminalState.OPEN) {
            return null;
        }
        if (System.currentTimeMillis() < session.expiryAt()) {
            return session;
        }
        terminate(session, Bukkit.getPlayer(playerId), TerminalState.EXPIRED,
                TerminationReason.EXPIRED, null);
        return null;
    }

    private Session activeOwned(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.terminalState() != TerminalState.OPEN) {
            return null;
        }
        if (System.currentTimeMillis() < session.expiryAt()) {
            return session;
        }
        terminateOwned(player, session, TerminalState.EXPIRED, TerminationReason.EXPIRED);
        return null;
    }

    private boolean terminate(Session session,
            Player player,
            TerminalState terminalState,
            TerminationReason reason,
            CompletableFuture<Void> completion) {
        if (session == null) {
            if (completion != null) {
                completion.complete(null);
            }
            return false;
        }
        synchronized (session) {
            if (session.terminalState() != TerminalState.OPEN) {
                if (completion != null) {
                    completion.complete(null);
                }
                return false;
            }
            session.terminal(terminalState, reason, true);
            sessions.remove(session.operatorId(), session);
            journal().rerollTerminal(session.operationId(), terminalPhase(terminalState, reason), reason.key(), true);
        }
        Runnable refund = () -> {
            compensateOwned(player, session, terminalState, reason);
            if (completion != null) {
                completion.complete(null);
            }
        };
        if (player == null || !player.isOnline()) {
            journal().compensationPending(session.operationId(), reason.key());
            if (completion != null) {
                completion.complete(null);
            }
            return true;
        }
        scheduleOwned(player, refund, () -> {
            journal().compensationPending(session.operationId(), "owner_schedule_retired");
            if (completion != null) {
                completion.complete(null);
            }
        });
        return true;
    }

    private void terminateOwned(Player player,
            Session session,
            TerminalState terminalState,
            TerminationReason reason) {
        terminate(session, player, terminalState, reason, null);
    }

    private void compensateOwned(Player player,
            Session session,
            TerminalState terminalState,
            TerminationReason reason) {
        if (!journal().beginCompensation(session.operationId(), reason.key())) {
            return;
        }
        GemEconomyService.RefundResult refund = session.chargeResult() != null
                && session.chargeResult().receipt() != null
                ? plugin.economyService().refundDetailed(player, session.chargeResult())
                : plugin.economyService().refundPersistedDetailed(player,
                        session.chargeResult() == null ? List.of() : session.chargeResult().chargedCurrencies(),
                        session.chargeResult() == null ? List.of() : session.chargeResult().chargedMaterials());
        session.compensationPending = !refund.success();
        journal().finishRerollCompensation(session.operationId(), terminalPhase(terminalState, reason),
                reason.key(), refund);
    }

    private CompletableFuture<Void> compensateRecovered(GemOperationJournal.RerollSnapshot snapshot,
            TerminalState terminalState,
            TerminationReason reason) {
        Player player = snapshot.playerId() == null ? null : Bukkit.getPlayer(snapshot.playerId());
        if (player == null || !player.isOnline()) {
            journal().rerollTerminal(snapshot.operationId(), terminalPhase(terminalState, reason), reason.key(), true);
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        scheduleOwned(player,
                () -> compensateRecoveredOwned(snapshot, player, terminalState, reason)
                        .whenComplete((ignored, failure) -> complete(completion, failure)),
                () -> {
                    journal().compensationPending(snapshot.operationId(), "owner_schedule_retired");
                    completion.complete(null);
                });
        return completion;
    }

    private CompletableFuture<Void> compensateRecoveredOwned(GemOperationJournal.RerollSnapshot snapshot,
            Player player,
            TerminalState terminalState,
            TerminationReason reason) {
        journal().rerollTerminal(snapshot.operationId(), terminalPhase(terminalState, reason), reason.key(), true);
        if (journal().beginCompensation(snapshot.operationId(), reason.key())) {
            GemEconomyService.RefundResult refund = plugin.economyService().refundPersistedDetailed(player,
                    snapshot.currencies(), snapshot.materials());
            journal().finishRerollCompensation(snapshot.operationId(), terminalPhase(terminalState, reason),
                    reason.key(), refund);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void scheduleOwned(Player player, Runnable task, Runnable retired) {
        if (plugin.scheduling() == null || plugin.scheduling().ownsEntity(player)) {
            task.run();
            return;
        }
        try {
            var scheduled = plugin.scheduling().runForEntity(plugin, player, task, retired);
            if (scheduled.cancelled() && retired != null) {
                retired.run();
            }
        } catch (Throwable throwable) {
            if (retired != null) {
                retired.run();
            }
        }
    }

    private void clearRecentConfirmed() {
        for (ConfirmedEntry entry : recentConfirmed.values()) {
            journal().archiveTerminal(entry.session().operationId());
        }
        recentConfirmed.clear();
    }

    private void pruneRecentConfirmed() {
        long now = System.currentTimeMillis();
        List<Map.Entry<UUID, ConfirmedEntry>> entries = new ArrayList<>(recentConfirmed.entrySet());
        for (Map.Entry<UUID, ConfirmedEntry> entry : entries) {
            if (now >= entry.getValue().expiryAt()
                    && recentConfirmed.remove(entry.getKey(), entry.getValue())) {
                journal().archiveTerminal(entry.getValue().session().operationId());
            }
        }
        int overflow = recentConfirmed.size() - confirmedCapacity;
        if (overflow <= 0) {
            return;
        }
        entries = new ArrayList<>(recentConfirmed.entrySet());
        entries.sort(Comparator.comparingLong(entry -> entry.getValue().confirmedAt()));
        for (int index = 0; index < overflow && index < entries.size(); index++) {
            Map.Entry<UUID, ConfirmedEntry> entry = entries.get(index);
            if (recentConfirmed.remove(entry.getKey(), entry.getValue())) {
                journal().archiveTerminal(entry.getValue().session().operationId());
            }
        }
    }

    private boolean matches(Session session, GemItemInstance current) {
        return current != null
                && session.instanceId().equals(current.instanceId())
                && session.instanceFingerprint().equals(instanceFingerprint(current));
    }

    private boolean owned(Player player) {
        return player != null
                && player.isOnline()
                && plugin.scheduling() != null
                && plugin.scheduling().ownsEntity(player);
    }

    private OperationType operationType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OperationType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private GemOperationJournal.Phase terminalPhase(TerminalState state, TerminationReason reason) {
        if (state == TerminalState.EXPIRED || reason == TerminationReason.EXPIRED) {
            return GemOperationJournal.Phase.EXPIRED;
        }
        if (reason == TerminationReason.INSTANCE_CHANGED) {
            return GemOperationJournal.Phase.INSTANCE_CHANGED;
        }
        if (reason == TerminationReason.WRITE_FAILED) {
            return GemOperationJournal.Phase.WRITE_FAILED;
        }
        if (state == TerminalState.FAILED) {
            return GemOperationJournal.Phase.FAILED;
        }
        return state == TerminalState.CONFIRMED
                ? GemOperationJournal.Phase.CONFIRMED
                : GemOperationJournal.Phase.ABANDONED;
    }

    private String instanceFingerprint(GemItemInstance instance) {
        if (instance == null) {
            return "";
        }
        StringBuilder value = new StringBuilder();
        value.append(instance.gemId()).append('|')
                .append(instance.level()).append('|')
                .append(instance.updatedAt()).append('|')
                .append(instance.instanceId()).append('|')
                .append(instance.stage()).append('|')
                .append(instance.dataVersion()).append('|');
        instance.affixes().forEach(affix -> value.append(affix).append(';'));
        instance.matrices().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> value.append(entry.getKey()).append('=').append(entry.getValue()).append(';'));
        instance.extensions().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(namespace -> {
            value.append(namespace.getKey()).append('{');
            namespace.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> value.append(entry.getKey()).append('=').append(entry.getValue()).append(';'));
            value.append('}');
        });
        return digest(value.toString());
    }

    private String configFingerprint(GemDefinition definition, OperationType operationType) {
        if (definition == null || operationType == null) {
            return "";
        }
        GemDefinition.RerollConfig reroll = definition.reroll();
        StringBuilder value = new StringBuilder();
        value.append(definition.id()).append('|')
                .append(operationType.name()).append('|')
                .append(reroll.enabled()).append('|')
                .append(reroll.group()).append('|')
                .append(reroll.maxAffixes()).append('|');
        reroll.pools().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(pool -> {
            value.append(pool.getKey()).append('[');
            pool.getValue().stream().sorted(Comparator.comparing(GemDefinition.AffixPoolEntry::id)
                    .thenComparingInt(GemDefinition.AffixPoolEntry::minStage)
                    .thenComparingInt(GemDefinition.AffixPoolEntry::maxStage))
                    .forEach(entry -> value.append(entry.id()).append(':')
                            .append(entry.weight()).append(':')
                            .append(entry.minStage()).append(':')
                            .append(entry.maxStage()).append(':')
                            .append(entry.minValue()).append(':')
                            .append(entry.maxValue()).append(';'));
            value.append(']');
        });
        GemDefinition.CostConfig cost = operationType == OperationType.FULL
                ? reroll.fullCost()
                : reroll.valueCost();
        cost.currencies().stream().sorted(Comparator.comparing(GemDefinition.CurrencyCost::provider)
                .thenComparing(GemDefinition.CurrencyCost::currencyId))
                .forEach(currency -> value.append("c:").append(currency.provider()).append(':')
                        .append(currency.currencyId()).append(':').append(currency.amount()).append(';'));
        cost.materials().stream().sorted(Comparator.comparing(material -> ItemSourceUtil.toShorthand(material.itemSource())))
                .forEach(material -> value.append("m:").append(ItemSourceUtil.toShorthand(material.itemSource()))
                        .append(':').append(material.amount()).append(';'));
        return digest(value.toString());
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private GemOperationJournal journal() {
        return GemOperationJournal.forPlugin(plugin, plugin.scheduling());
    }

    private void complete(CompletableFuture<Void> completion, Throwable failure) {
        if (failure == null) {
            completion.complete(null);
        } else {
            completion.completeExceptionally(failure);
        }
    }
}
