package emaki.jiuwu.craft.gem.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.model.GemRerollSessionView;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;

/** In-memory candidate state machine for full/value gem rerolls. */
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
        private volatile TerminalState terminalState;

        private Session(UUID operatorId,
                String operationId,
                GemItemInstance original,
                GemItemInstance candidate,
                OperationType operationType,
                GemEconomyService.ChargeResult chargeResult,
                long createdAt,
                long expiryAt) {
            this.operatorId = operatorId;
            this.operationId = operationId;
            this.instanceId = original.instanceId();
            this.original = original;
            this.candidate = candidate;
            this.operationType = operationType;
            this.chargeResult = chargeResult;
            this.createdAt = createdAt;
            this.expiryAt = expiryAt;
            this.terminalState = TerminalState.OPEN;
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
        public TerminalState terminalState() { return terminalState; }
        private void terminal(TerminalState state) { terminalState = state; }
    }

    private static final long DEFAULT_TTL_MILLIS = 120_000L;

    private final EmakiGemPlugin plugin;
    private final GemRerollCandidateGenerator generator;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Session> recentConfirmed = new ConcurrentHashMap<>();
    private final Map<UUID, Object> sessionLocks = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public GemRerollSessionService(EmakiGemPlugin plugin) {
        this(plugin, DEFAULT_TTL_MILLIS);
    }

    public GemRerollSessionService(EmakiGemPlugin plugin, long ttlMillis) {
        this.plugin = plugin;
        this.generator = new GemRerollCandidateGenerator();
        this.ttlMillis = Math.max(1_000L, ttlMillis);
    }

    public OpenResult open(Player player, OperationType operationType) {
        if (player == null || operationType == null) {
            return OpenResult.failure("gem.reroll.player_required");
        }
        if (!player.isOnline() || plugin.scheduling() == null || !plugin.scheduling().ownsEntity(player)) {
            return OpenResult.failure("gem.reroll.wrong_thread");
        }
        UUID playerId = player.getUniqueId();
        Object lock = sessionLocks.computeIfAbsent(playerId, ignored -> new Object());
        synchronized (lock) {
            Session existing = active(playerId);
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
            String operationId = GemOperationJournal.forPlugin(plugin, plugin.scheduling())
                    .begin("gem-reroll-" + operationType.name().toLowerCase(), playerId);
            GemEconomyService.ChargeResult charge = plugin.economyService().charge(player, cost,
                    Map.of("gem_id", original.gemId(), "stage", original.stage(), "level", original.level()));
            if (!charge.success()) {
                GemOperationJournal.forPlugin(plugin, plugin.scheduling()).failedCharge(operationId, charge);
                return OpenResult.failure(charge.errorKey());
            }
            GemOperationJournal journal = GemOperationJournal.forPlugin(plugin, plugin.scheduling());
            journal.charged(operationId, charge);
            journal.advance(operationId, GemOperationJournal.Phase.CANDIDATE_OPEN);
            long now = System.currentTimeMillis();
            Session created = new Session(playerId, operationId, original, generated.candidate(), operationType,
                    charge, now, now + ttlMillis);
            sessions.put(playerId, created);
            recentConfirmed.remove(playerId);
            return OpenResult.success(created);
        }
    }

    public ActionResult confirm(Player player) {
        if (player == null) {
            return ActionResult.failure("gem.reroll.player_required", null);
        }
        if (!player.isOnline() || plugin.scheduling() == null || !plugin.scheduling().ownsEntity(player)) {
            return ActionResult.failure("gem.reroll.wrong_thread", null);
        }
        Session session = active(player.getUniqueId());
        if (session == null) {
            Session confirmed = recentConfirmed.get(player.getUniqueId());
            return confirmed == null
                    ? ActionResult.failure("gem.reroll.session_missing", null)
                    : ActionResult.success(confirmed);
        }
        synchronized (session) {
            if (session.terminalState() == TerminalState.CONFIRMED) {
                return ActionResult.success(session);
            }
            if (session.terminalState() != TerminalState.OPEN) {
                return ActionResult.failure("gem.reroll.session_closed", session);
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            GemItemInstance current = plugin.itemMatcher().readStoredGemInstance(held);
            if (current == null || !session.instanceId().equals(current.instanceId())
                    || current.updatedAt() != session.original().updatedAt()) {
                session.terminal(TerminalState.FAILED);
                return ActionResult.failure("gem.reroll.instance_changed", session);
            }
            if (!plugin.itemFactory().applyInstance(held, session.candidate())) {
                session.terminal(TerminalState.FAILED);
                return ActionResult.failure("gem.reroll.write_failed", session);
            }
            player.getInventory().setItemInMainHand(held);
            session.terminal(TerminalState.CONFIRMED);
            GemOperationJournal journal = GemOperationJournal.forPlugin(plugin, plugin.scheduling());
            journal.advance(session.operationId(), GemOperationJournal.Phase.STATE_COMMITTED);
            journal.advance(session.operationId(), GemOperationJournal.Phase.COMPLETED);
            sessions.remove(player.getUniqueId(), session);
            recentConfirmed.put(player.getUniqueId(), session);
            return ActionResult.success(session);
        }
    }

    public boolean abandon(UUID playerId) {
        Session session = sessions.get(playerId);
        if (session == null) {
            return false;
        }
        synchronized (session) {
            if (session.terminalState() != TerminalState.OPEN) {
                return false;
            }
            session.terminal(TerminalState.ABANDONED);
            GemOperationJournal.forPlugin(plugin, plugin.scheduling())
                    .advance(session.operationId(), GemOperationJournal.Phase.COMPLETED);
            sessions.remove(playerId, session);
            return true;
        }
    }

    public Optional<Session> session(UUID playerId) {
        return Optional.ofNullable(active(playerId));
    }

    public Optional<GemRerollSessionView> view(UUID playerId) {
        Session session = active(playerId);
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(new GemRerollSessionView(
                session.operatorId(),
                session.instanceId(),
                session.operationType().name().toLowerCase(),
                session.original().affixes(),
                session.candidate().affixes(),
                session.original().stage(),
                session.original().updatedAt(),
                session.createdAt(),
                session.expiryAt(),
                session.terminalState().name().toLowerCase()
        ));
    }

    public void clearAll() {
        for (UUID playerId : sessions.keySet()) {
            abandon(playerId);
        }
        sessions.clear();
        recentConfirmed.clear();
        sessionLocks.clear();
    }

    private Session active(UUID playerId) {
        Session session = sessions.get(playerId);
        if (session == null) {
            return null;
        }
        if (session.terminalState() == TerminalState.OPEN && System.currentTimeMillis() >= session.expiryAt()) {
            synchronized (session) {
                if (session.terminalState() == TerminalState.OPEN) {
                    session.terminal(TerminalState.EXPIRED);
                    GemOperationJournal.forPlugin(plugin, plugin.scheduling())
                            .advance(session.operationId(), GemOperationJournal.Phase.COMPLETED);
                }
            }
        }
        return session.terminalState() == TerminalState.OPEN || session.terminalState() == TerminalState.CONFIRMED
                ? session : null;
    }
}
