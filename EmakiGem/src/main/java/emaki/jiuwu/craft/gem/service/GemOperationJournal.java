package emaki.jiuwu.craft.gem.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.api.diagnostics.Anchors;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.craft.CraftOperationJournal;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;

public final class GemOperationJournal {

    public enum Phase {
        PREPARED,
        CHARGED,
        CANDIDATE_OPEN,
        STATE_COMMITTED,
        REWARD_PENDING,
        REWARDED,
        CONFIRMED,
        ABANDONED,
        EXPIRED,
        INSTANCE_CHANGED,
        WRITE_FAILED,
        FAILED,
        COMPLETED,
        COMPENSATION_PENDING
    }

    public record RerollSnapshot(
            String operationId,
            UUID playerId,
            String operationType,
            GemItemInstance original,
            GemItemInstance candidate,
            String configFingerprint,
            String instanceFingerprint,
            long createdAt,
            long expiryAt,
            String terminalReason,
            boolean compensationPending,
            List<GemDefinition.CurrencyCost> currencies,
            List<GemDefinition.MaterialCost> materials) {

        public boolean complete() {
            return operationId != null && !operationId.isBlank()
                    && playerId != null
                    && operationType != null && !operationType.isBlank()
                    && original != null
                    && candidate != null
                    && configFingerprint != null && !configFingerprint.isBlank()
                    && instanceFingerprint != null && !instanceFingerprint.isBlank()
                    && createdAt > 0L
                    && expiryAt > createdAt;
        }
    }

    private static final Map<EmakiGemPlugin, GemOperationJournal> INSTANCES = new ConcurrentHashMap<>();
    private static final String DEBUG_JOURNAL_MODULE = "state";

    private final EmakiGemPlugin plugin;
    private final EmakiScheduling scheduling;
    private final CraftOperationJournal<GemPayload> journal;
    private final Set<String> compensationGates = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean recovering = new AtomicBoolean();
    private final AtomicBoolean recoveryStarted = new AtomicBoolean();

    private GemOperationJournal(EmakiGemPlugin plugin, EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
        Path root = plugin.getDataFolder().toPath().resolve("data/operation-journal");
        this.journal = CraftOperationJournal.ofPersisted(Integer.MAX_VALUE, new GemCodec(), plugin, root);
    }

    public static GemOperationJournal forPlugin(EmakiGemPlugin plugin, EmakiScheduling scheduling) {
        return INSTANCES.computeIfAbsent(plugin, ignored -> new GemOperationJournal(plugin, scheduling));
    }

    public String begin(String kind, UUID playerId) {
        return begin(UUID.randomUUID().toString(), kind, playerId);
    }

    public String begin(String operationId, String kind, UUID playerId) {
        String resolvedOperationId = operationId == null || operationId.isBlank()
                ? UUID.randomUUID().toString()
                : operationId;
        save(new Entry(resolvedOperationId, kind, playerId, Phase.PREPARED, GemPayload.empty()));
        return resolvedOperationId;
    }

    public boolean contains(String operationId) {
        return operationId != null && journal.contains(operationId);
    }

    public void charged(String operationId, GemEconomyService.ChargeResult result) {
        Entry current = load(operationId);
        if (current == null) {
            return;
        }
        GemPayload payload = current.payload().withCosts(
                encodeCurrencies(result == null ? List.of() : result.chargedCurrencies()),
                encodeMaterials(result == null ? List.of() : result.chargedMaterials()));
        save(current.withPhase(Phase.CHARGED, payload));
    }

    public void rerollGenerated(String operationId,
            GemRerollSessionService.OperationType operationType,
            GemItemInstance original,
            GemItemInstance candidate,
            String configFingerprint,
            String instanceFingerprint,
            long createdAt,
            long expiryAt) {
        Entry current = load(operationId);
        if (current == null || operationType == null || original == null || candidate == null) {
            return;
        }
        GemPayload payload = current.payload().withReroll(
                original.toMap(),
                candidate.toMap(),
                operationType.name().toLowerCase(Locale.ROOT),
                configFingerprint,
                instanceFingerprint,
                createdAt,
                expiryAt).withEvent("generated");
        save(current.withPhase(Phase.CANDIDATE_OPEN, payload));
    }

    public void rerollTerminal(String operationId, Phase phase, String reason, boolean compensationPending) {
        Entry current = load(operationId);
        if (current == null || phase == null) {
            return;
        }
        String event = switch (phase) {
            case CONFIRMED -> "confirmed";
            case ABANDONED -> "abandoned";
            case EXPIRED -> "expired";
            case INSTANCE_CHANGED -> "instance_changed";
            case WRITE_FAILED -> "write_failed";
            case FAILED -> "failed";
            default -> phase.name().toLowerCase(Locale.ROOT);
        };
        GemPayload payload = current.payload()
                .withTerminal(reason, compensationPending)
                .withEvent(event);
        save(current.withPhase(compensationPending ? Phase.COMPENSATION_PENDING : phase, payload));
    }

    public void duplicateConfirm(String operationId) {
        Entry current = load(operationId);
        if (current == null) {
            return;
        }
        save(current.withPhase(current.phase(), current.payload().withEvent("duplicate_confirm")));
    }

    public boolean beginCompensation(String operationId, String reason) {
        Entry current = load(operationId);
        if (current == null || !compensationGates.add(operationId)) {
            return false;
        }
        GemPayload payload = current.payload()
                .withTerminal(reason, true)
                .withCompensationAttempted(true)
                .withEvent("compensation_started");
        save(current.withPhase(Phase.COMPENSATION_PENDING, payload));
        return true;
    }

    public void finishRerollCompensation(String operationId,
            Phase terminalPhase,
            String reason,
            GemEconomyService.RefundResult result) {
        Entry current = load(operationId);
        if (current == null) {
            return;
        }
        List<GemDefinition.CurrencyCost> currencies = result == null
                ? decodeCurrencies(current.payload().currencies())
                : result.remainingCurrencies();
        List<GemDefinition.MaterialCost> materials = result == null
                ? decodeMaterials(current.payload().materials())
                : result.remainingMaterials();
        boolean complete = currencies.isEmpty() && materials.isEmpty();
        GemPayload payload = current.payload()
                .withCosts(encodeCurrencies(currencies), encodeMaterials(materials))
                .withTerminal(reason, !complete)
                .withEvent(complete ? "compensated" : "compensation_pending");
        if (!complete) {
            save(current.withPhase(Phase.COMPENSATION_PENDING, payload));
            return;
        }
        Phase resolved = terminalPhase == null ? Phase.ABANDONED : terminalPhase;
        Entry terminal = current.withPhase(resolved, payload);
        save(terminal);
        archive(terminal);
    }

    public void archiveTerminal(String operationId) {
        Entry current = load(operationId);
        if (current != null) {
            archive(current);
        }
    }

    public void advance(String operationId, Phase phase) {
        advanceAsync(operationId, phase);
    }

    private CompletableFuture<Void> advanceAsync(String operationId, Phase phase) {
        Entry current = load(operationId);
        if (current == null || phase == null) {
            return CompletableFuture.completedFuture(null);
        }
        long startedAt = anchorsEnabled() ? System.nanoTime() : 0L;
        Phase from = current.phase();
        Entry updated = current.withPhase(phase, current.payload());
        save(updated);
        if (anchorsEnabled()) {
            anchorTransition(updated.operationId(), from, phase, startedAt, null);
        }
        return phase == Phase.COMPLETED ? archive(updated) : CompletableFuture.completedFuture(null);
    }

    private boolean anchorsEnabled() {
        return plugin.debugLogger() != null
                && plugin.debugLogger().shouldLog(DEBUG_JOURNAL_MODULE, (UUID) null);
    }

    private void anchorTransition(String operationId,
            Phase from,
            Phase to,
            long startedAt,
            Throwable failure) {
        if (!anchorsEnabled()) {
            return;
        }
        Anchors.Builder anchor = Anchors.of()
                .op(operationId)
                .phase(from + "->" + to)
                .elapsedMs((System.nanoTime() - startedAt) / 1_000_000L);
        if (failure != null) {
            anchor.cause(AsyncFailures.unwrap(failure));
        }
        plugin.debugLogger().logRaw(DEBUG_JOURNAL_MODULE, (UUID) null, "journal " + anchor.render());
    }

    public void compensationPending(String operationId, String error) {
        Entry current = load(operationId);
        if (current == null) {
            return;
        }
        GemPayload payload = current.payload()
                .withError(error)
                .withTerminal(error, true)
                .withEvent("compensation_pending");
        save(current.withPhase(Phase.COMPENSATION_PENDING, payload));
    }

    public void completeAfterRefund(String operationId,
            String error,
            GemEconomyService.RefundResult result) {
        if (result != null && result.success()) {
            advance(operationId, Phase.COMPLETED);
            return;
        }
        Entry current = load(operationId);
        if (current == null) {
            return;
        }
        List<GemDefinition.CurrencyCost> currencies = result == null
                ? decodeCurrencies(current.payload().currencies())
                : result.remainingCurrencies();
        List<GemDefinition.MaterialCost> materials = result == null
                ? decodeMaterials(current.payload().materials())
                : result.remainingMaterials();
        if (currencies.isEmpty() && materials.isEmpty()) {
            advance(operationId, Phase.COMPLETED);
            return;
        }
        GemPayload payload = current.payload()
                .withCosts(encodeCurrencies(currencies), encodeMaterials(materials))
                .withError(error)
                .withTerminal(error, true);
        save(current.withPhase(Phase.COMPENSATION_PENDING, payload));
    }

    public void failedCharge(String operationId, GemEconomyService.ChargeResult result) {
        if (result == null || result.compensationComplete()) {
            advance(operationId, Phase.COMPLETED);
            return;
        }
        charged(operationId, result);
        compensationPending(operationId, result.errorKey());
    }

    public CompletionStage<Boolean> completeAfterActions(String operationId,
            CompletionStage<GemActionCoordinator.ExecutionResult> actions) {
        advance(operationId, Phase.REWARD_PENDING);
        if (actions == null) {
            rewardPending(operationId, "action_stage_missing");
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        actions.whenComplete((result, throwable) -> {
            if (throwable != null) {
                rewardPending(operationId, throwable.getMessage());
                completion.complete(false);
                return;
            }
            if (result == null || !result.success()) {
                rewardPending(operationId, result == null ? "action_result_missing" : result.message());
                completion.complete(false);
                return;
            }
            advanceAsync(operationId, Phase.REWARDED)
                    .thenCompose(ignored -> advanceAsync(operationId, Phase.COMPLETED))
                    .whenComplete((ignored, failure) -> {
                        if (failure == null) {
                            completion.complete(true);
                        } else {
                            completion.completeExceptionally(failure);
                        }
                    });
        });
        return completion;
    }

    public void rewardPending(String operationId, String error) {
        Entry current = load(operationId);
        if (current == null) {
            return;
        }
        save(current.withPhase(Phase.REWARD_PENDING, current.payload().withError(error)));
    }

    public CompletableFuture<Void> recover(GemEconomyService economyService) {
        return recover(economyService, plugin.rerollSessionService());
    }

    public CompletableFuture<Void> recover(GemEconomyService economyService,
            GemRerollSessionService rerollSessionService) {
        if (recoveryStarted.get() || !recovering.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        return journal.loadActive()
                .thenCompose(recovered -> restoreAndRecover(recovered, economyService, rerollSessionService))
                .whenComplete((ignored, failure) -> {
                    recovering.set(false);
                    if (failure == null) {
                        recoveryStarted.set(true);
                    }
                });
    }

    private CompletableFuture<Void> restoreAndRecover(
            List<CraftOperationJournal.Entry<GemPayload>> recovered,
            GemEconomyService economyService,
            GemRerollSessionService rerollSessionService) {
        if (recovered.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<Entry> entries = new ArrayList<>();
        for (CraftOperationJournal.Entry<GemPayload> item : recovered) {
            if (!journal.contains(item.operationId())) {
                journal.restore(item.operationId(), item.kind(), item.playerId(), item.phase(), item.payload());
            }
            entries.add(toGemEntry(item));
        }
        plugin.getLogger().info("Recoverable gem operations: " + String.join(", ",
                entries.stream().map(entry -> entry.operationId() + "(" + entry.phase().name() + ")").toList()));
        CompletableFuture<Void> result = new CompletableFuture<>();
        Runnable apply = () -> applyRecovery(entries, economyService, rerollSessionService)
                .whenComplete((ignored, failure) -> {
                    if (failure == null) {
                        result.complete(null);
                    } else {
                        result.completeExceptionally(failure);
                    }
                });
        if (scheduling == null) {
            apply.run();
        } else {
            try {
                scheduling.runGlobal(plugin, apply);
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        }
        return result;
    }

    private CompletableFuture<Void> applyRecovery(List<Entry> entries,
            GemEconomyService economyService,
            GemRerollSessionService rerollSessionService) {
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (Entry entry : entries) {
            if (isReroll(entry)) {
                tasks.add(recoverReroll(entry, economyService, rerollSessionService));
                continue;
            }
            switch (entry.phase()) {
                case PREPARED, CANDIDATE_OPEN, REWARDED, CONFIRMED, ABANDONED, EXPIRED,
                        INSTANCE_CHANGED, WRITE_FAILED, FAILED -> advance(entry.operationId(), Phase.COMPLETED);
                case STATE_COMMITTED, REWARD_PENDING -> rewardPending(entry.operationId(),
                        entry.payload().error().isBlank() ? "reward_completion_unknown" : entry.payload().error());
                case CHARGED, COMPENSATION_PENDING -> tasks.add(recoverCompensation(entry, economyService));
                case COMPLETED -> archive(entry);
            }
        }
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> recoverReroll(Entry entry,
            GemEconomyService economyService,
            GemRerollSessionService rerollSessionService) {
        if (entry.phase() == Phase.CANDIDATE_OPEN && rerollSessionService != null) {
            RerollSnapshot snapshot = snapshot(entry);
            if (snapshot.complete()) {
                return rerollSessionService.recover(snapshot);
            }
            return recoverRerollCompensation(entry, economyService, Phase.FAILED, "recovery_snapshot_incomplete");
        }
        if (entry.phase() == Phase.PREPARED) {
            archive(entry);
            return CompletableFuture.completedFuture(null);
        }
        if (entry.phase() == Phase.CONFIRMED || entry.phase() == Phase.STATE_COMMITTED) {
            archive(entry);
            return CompletableFuture.completedFuture(null);
        }
        if (entry.phase() == Phase.CHARGED) {
            return recoverRerollCompensation(entry, economyService, Phase.FAILED, "recovery_candidate_missing");
        }
        if (entry.phase() == Phase.COMPENSATION_PENDING || entry.payload().compensationPending()) {
            Phase terminal = terminalPhase(entry.payload().terminalReason());
            return recoverRerollCompensation(entry, economyService, terminal, entry.payload().terminalReason());
        }
        archive(entry);
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> recoverRerollCompensation(Entry entry,
            GemEconomyService economyService,
            Phase terminalPhase,
            String reason) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Player player = entry.playerId() == null ? null : Bukkit.getPlayer(entry.playerId());
        if (player == null || !player.isOnline()) {
            compensationPending(entry.operationId(), reason == null || reason.isBlank()
                    ? "recovery_player_offline"
                    : reason);
            completion.complete(null);
            return completion;
        }
        Runnable recovery = () -> {
            if (!beginCompensation(entry.operationId(), reason)) {
                completion.complete(null);
                return;
            }
            GemEconomyService.RefundResult result = economyService.refundPersistedDetailed(player,
                    decodeCurrencies(entry.payload().currencies()), decodeMaterials(entry.payload().materials()));
            finishRerollCompensation(entry.operationId(), terminalPhase, reason, result);
            completion.complete(null);
        };
        if (scheduling == null || scheduling.ownsEntity(player)) {
            recovery.run();
            return completion;
        }
        try {
            var task = scheduling.runForEntity(plugin, player, recovery, () -> {
                compensationPending(entry.operationId(), "owner_schedule_retired");
                completion.complete(null);
            });
            if (task.cancelled()) {
                compensationPending(entry.operationId(), "owner_schedule_rejected");
                completion.complete(null);
            }
        } catch (Throwable throwable) {
            compensationPending(entry.operationId(), throwable.getMessage());
            completion.complete(null);
        }
        return completion;
    }

    private CompletableFuture<Void> recoverCompensation(Entry entry, GemEconomyService economyService) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Player player = entry.playerId() == null ? null : Bukkit.getPlayer(entry.playerId());
        if (player == null || !player.isOnline()) {
            compensationPending(entry.operationId(), "recovery_player_offline");
            completion.complete(null);
            return completion;
        }
        Runnable recovery = () -> {
            completeAfterRefund(entry.operationId(), "refund_failed",
                    economyService.refundPersistedDetailed(player,
                            decodeCurrencies(entry.payload().currencies()),
                            decodeMaterials(entry.payload().materials())));
            completion.complete(null);
        };
        if (scheduling == null || scheduling.ownsEntity(player)) {
            recovery.run();
            return completion;
        }
        try {
            var task = scheduling.runForEntity(plugin, player, recovery, () -> {
                compensationPending(entry.operationId(), "owner_schedule_retired");
                completion.complete(null);
            });
            if (task.cancelled()) {
                compensationPending(entry.operationId(), "owner_schedule_rejected");
                completion.complete(null);
            }
        } catch (Throwable throwable) {
            compensationPending(entry.operationId(), throwable.getMessage());
            completion.complete(null);
        }
        return completion;
    }

    private RerollSnapshot snapshot(Entry entry) {
        GemPayload payload = entry.payload();
        return new RerollSnapshot(
                entry.operationId(),
                entry.playerId(),
                payload.operationType(),
                GemItemInstance.fromMap(payload.original()),
                GemItemInstance.fromMap(payload.candidate()),
                payload.configFingerprint(),
                payload.instanceFingerprint(),
                payload.createdAt(),
                payload.expiryAt(),
                payload.terminalReason(),
                payload.compensationPending(),
                decodeCurrencies(payload.currencies()),
                decodeMaterials(payload.materials()));
    }

    private boolean isReroll(Entry entry) {
        return entry.kind() != null && entry.kind().startsWith("gem-reroll-");
    }

    private Phase terminalPhase(String reason) {
        if (reason == null) {
            return Phase.ABANDONED;
        }
        return switch (reason) {
            case "expired" -> Phase.EXPIRED;
            case "instance_changed", "reload_instance_changed" -> Phase.INSTANCE_CHANGED;
            case "write_failed" -> Phase.WRITE_FAILED;
            case "recovery_snapshot_incomplete", "recovery_candidate_missing", "config_incompatible" -> Phase.FAILED;
            default -> Phase.ABANDONED;
        };
    }

    private void save(Entry entry) {
        if (!journal.contains(entry.operationId())) {
            journal.begin(entry.operationId(), entry.kind(), entry.playerId(), entry.phase().name(), entry.payload());
        } else {
            journal.update(entry.operationId(), entry.phase().name(), entry.payload());
        }
    }

    private Entry load(String operationId) {
        if (operationId == null) {
            return null;
        }
        CraftOperationJournal.Entry<GemPayload> entry = journal.get(operationId);
        return entry == null ? null : toGemEntry(entry);
    }

    private CompletableFuture<Void> archive(Entry entry) {
        compensationGates.remove(entry.operationId());
        return journal.archive(entry.operationId());
    }

    private Entry toGemEntry(CraftOperationJournal.Entry<GemPayload> entry) {
        Phase phase;
        try {
            phase = Phase.valueOf(entry.phase().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            phase = Phase.COMPENSATION_PENDING;
        }
        GemPayload payload = entry.payload() == null ? GemPayload.empty() : entry.payload();
        return new Entry(entry.operationId(), entry.kind(), entry.playerId(), phase, payload);
    }

    private record GemPayload(
            List<Map<String, Object>> currencies,
            List<Map<String, Object>> materials,
            String error,
            Map<String, Object> original,
            Map<String, Object> candidate,
            String operationType,
            String configFingerprint,
            String instanceFingerprint,
            long createdAt,
            long expiryAt,
            String terminalReason,
            boolean compensationPending,
            boolean compensationAttempted,
            List<String> events) {

        private GemPayload {
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
            materials = materials == null ? List.of() : List.copyOf(materials);
            error = error == null ? "" : error;
            original = original == null ? Map.of() : Map.copyOf(original);
            candidate = candidate == null ? Map.of() : Map.copyOf(candidate);
            operationType = operationType == null ? "" : operationType;
            configFingerprint = configFingerprint == null ? "" : configFingerprint;
            instanceFingerprint = instanceFingerprint == null ? "" : instanceFingerprint;
            terminalReason = terminalReason == null ? "" : terminalReason;
            events = events == null ? List.of() : List.copyOf(events);
        }

        private static GemPayload empty() {
            return new GemPayload(List.of(), List.of(), "", Map.of(), Map.of(), "", "", "",
                    0L, 0L, "", false, false, List.of());
        }

        private GemPayload withCosts(List<Map<String, Object>> nextCurrencies,
                List<Map<String, Object>> nextMaterials) {
            return new GemPayload(nextCurrencies, nextMaterials, error, original, candidate, operationType,
                    configFingerprint, instanceFingerprint, createdAt, expiryAt, terminalReason,
                    compensationPending, compensationAttempted, events);
        }

        private GemPayload withError(String nextError) {
            return new GemPayload(currencies, materials, nextError, original, candidate, operationType,
                    configFingerprint, instanceFingerprint, createdAt, expiryAt, terminalReason,
                    compensationPending, compensationAttempted, events);
        }

        private GemPayload withReroll(Map<String, Object> nextOriginal,
                Map<String, Object> nextCandidate,
                String nextOperationType,
                String nextConfigFingerprint,
                String nextInstanceFingerprint,
                long nextCreatedAt,
                long nextExpiryAt) {
            return new GemPayload(currencies, materials, error, nextOriginal, nextCandidate, nextOperationType,
                    nextConfigFingerprint, nextInstanceFingerprint, nextCreatedAt, nextExpiryAt, terminalReason,
                    compensationPending, compensationAttempted, events);
        }

        private GemPayload withTerminal(String nextReason, boolean nextCompensationPending) {
            return new GemPayload(currencies, materials, error, original, candidate, operationType,
                    configFingerprint, instanceFingerprint, createdAt, expiryAt, nextReason,
                    nextCompensationPending, compensationAttempted, events);
        }

        private GemPayload withCompensationAttempted(boolean attempted) {
            return new GemPayload(currencies, materials, error, original, candidate, operationType,
                    configFingerprint, instanceFingerprint, createdAt, expiryAt, terminalReason,
                    compensationPending, attempted, events);
        }

        private GemPayload withEvent(String event) {
            if (event == null || event.isBlank()) {
                return this;
            }
            List<String> next = new ArrayList<>(events);
            next.add(event);
            return new GemPayload(currencies, materials, error, original, candidate, operationType,
                    configFingerprint, instanceFingerprint, createdAt, expiryAt, terminalReason,
                    compensationPending, compensationAttempted, List.copyOf(next));
        }
    }

    private static final class GemCodec implements CraftOperationJournal.Codec<GemPayload> {

        @Override
        public Map<String, Object> encode(GemPayload payload) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("currencies", payload.currencies());
            values.put("materials", payload.materials());
            values.put("error", payload.error());
            values.put("original", payload.original());
            values.put("candidate", payload.candidate());
            values.put("operation_type", payload.operationType());
            values.put("config_fingerprint", payload.configFingerprint());
            values.put("instance_fingerprint", payload.instanceFingerprint());
            values.put("created_at", payload.createdAt());
            values.put("expiry_at", payload.expiryAt());
            values.put("terminal_reason", payload.terminalReason());
            values.put("compensation_pending", payload.compensationPending());
            values.put("compensation_attempted", payload.compensationAttempted());
            values.put("events", payload.events());
            return values;
        }

        @Override
        public GemPayload decode(YamlSection section) {
            return new GemPayload(
                    maps(section.get("currencies")),
                    maps(section.get("materials")),
                    section.getString("error", ""),
                    map(section.get("original")),
                    map(section.get("candidate")),
                    section.getString("operation_type", ""),
                    section.getString("config_fingerprint", ""),
                    section.getString("instance_fingerprint", ""),
                    number(section.get("created_at")).longValue(),
                    number(section.get("expiry_at")).longValue(),
                    section.getString("terminal_reason", ""),
                    section.getBoolean("compensation_pending", false),
                    section.getBoolean("compensation_attempted", false),
                    strings(section.get("events")));
        }

        private static List<Map<String, Object>> maps(Object value) {
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                Map<String, Object> normalized = map(item);
                if (!normalized.isEmpty()) {
                    result.add(normalized);
                }
            }
            return List.copyOf(result);
        }

        private static Map<String, Object> map(Object value) {
            if (value instanceof YamlSection section) {
                return normalizeMap(section.asMap());
            }
            if (value instanceof Map<?, ?> source) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                source.forEach((key, entry) -> normalized.put(String.valueOf(key), normalize(entry)));
                return Map.copyOf(normalized);
            }
            return Map.of();
        }

        private static Map<String, Object> normalizeMap(Map<String, Object> source) {
            if (source == null || source.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            source.forEach((key, value) -> normalized.put(key, normalize(value)));
            return Map.copyOf(normalized);
        }

        private static Object normalize(Object value) {
            if (value instanceof YamlSection section) {
                return normalizeMap(section.asMap());
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, entry) -> normalized.put(String.valueOf(key), normalize(entry)));
                return Map.copyOf(normalized);
            }
            if (value instanceof List<?> list) {
                return list.stream().map(GemCodec::normalize).toList();
            }
            return value;
        }

        private static List<String> strings(Object value) {
            if (!(value instanceof Iterable<?> iterable)) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (Object entry : iterable) {
                if (entry != null && !String.valueOf(entry).isBlank()) {
                    result.add(String.valueOf(entry));
                }
            }
            return List.copyOf(result);
        }

        private static Number number(Object value) {
            if (value instanceof Number number) {
                return number;
            }
            try {
                return Double.parseDouble(value == null ? "0" : String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
    }

    private List<Map<String, Object>> encodeCurrencies(List<GemDefinition.CurrencyCost> costs) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (GemDefinition.CurrencyCost cost : costs) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("provider", cost.provider());
            value.put("currency_id", cost.currencyId());
            value.put("amount", cost.amount());
            values.add(value);
        }
        return List.copyOf(values);
    }

    private List<Map<String, Object>> encodeMaterials(List<GemDefinition.MaterialCost> costs) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (GemDefinition.MaterialCost cost : costs) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("item", ItemSourceUtil.toShorthand(cost.itemSource()));
            value.put("amount", cost.amount());
            values.add(value);
        }
        return List.copyOf(values);
    }

    private List<GemDefinition.CurrencyCost> decodeCurrencies(List<Map<String, Object>> values) {
        List<GemDefinition.CurrencyCost> costs = new ArrayList<>();
        for (Map<String, Object> value : values) {
            costs.add(new GemDefinition.CurrencyCost(
                    text(value.get("provider")),
                    text(value.get("currency_id")),
                    number(value.get("amount")).doubleValue(),
                    0D,
                    "",
                    ""));
        }
        return List.copyOf(costs);
    }

    private List<GemDefinition.MaterialCost> decodeMaterials(List<Map<String, Object>> values) {
        List<GemDefinition.MaterialCost> costs = new ArrayList<>();
        for (Map<String, Object> value : values) {
            ItemSourceRef source = ItemSourceUtil.parse(text(value.get("item")));
            if (source != null) {
                costs.add(new GemDefinition.MaterialCost(source, number(value.get("amount")).intValue()));
            }
        }
        return List.copyOf(costs);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        try {
            return Double.parseDouble(text(value));
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    private record Entry(
            String operationId,
            String kind,
            UUID playerId,
            Phase phase,
            GemPayload payload) {

        private Entry withPhase(Phase nextPhase, GemPayload nextPayload) {
            return new Entry(operationId, kind, playerId, nextPhase, nextPayload);
        }
    }
}
