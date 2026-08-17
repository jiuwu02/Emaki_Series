package emaki.jiuwu.craft.gem.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.api.diagnostics.Anchors;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.craft.CraftOperationJournal;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;

public final class GemOperationJournal {

    public enum Phase {
        PREPARED,
        CHARGED,
        STATE_COMMITTED,
        REWARD_PENDING,
        REWARDED,
        COMPLETED,
        COMPENSATION_PENDING
    }

    private static final Map<EmakiGemPlugin, GemOperationJournal> INSTANCES = new ConcurrentHashMap<>();
    private static final String DEBUG_JOURNAL_MODULE = "state";

    private final EmakiGemPlugin plugin;
    private final EmakiScheduling scheduling;
    private final CraftOperationJournal<GemPayload> journal;

    private GemOperationJournal(EmakiGemPlugin plugin, EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
        Path root = plugin.getDataFolder().toPath().resolve("data/operation-journal");
        this.journal = CraftOperationJournal.ofPersisted(Integer.MAX_VALUE, new GemCodec(), plugin, root);
    }

    public static GemOperationJournal forPlugin(EmakiGemPlugin plugin,
            EmakiScheduling scheduling) {
        return INSTANCES.computeIfAbsent(plugin,
                ignored -> new GemOperationJournal(plugin, scheduling));
    }

    public String begin(String kind, UUID playerId) {
        return begin(UUID.randomUUID().toString(), kind, playerId);
    }

    public String begin(String operationId, String kind, UUID playerId) {
        String resolvedOperationId = operationId == null || operationId.isBlank()
                ? UUID.randomUUID().toString()
                : operationId;
        save(new Entry(resolvedOperationId, kind, playerId, Phase.PREPARED, List.of(), List.of(), ""));
        return resolvedOperationId;
    }

    public void charged(String operationId, GemEconomyService.ChargeResult result) {
        Entry current = load(operationId);
        if (current == null) {
            return;
        }
        save(new Entry(current.operationId(), current.kind(), current.playerId(), Phase.CHARGED,
                encodeCurrencies(result == null ? List.of() : result.chargedCurrencies()),
                encodeMaterials(result == null ? List.of() : result.chargedMaterials()), ""));
    }

    public void advance(String operationId, Phase phase) {
        advanceAsync(operationId, phase);
    }

    private CompletableFuture<Void> advanceAsync(String operationId, Phase phase) {
        Entry current = load(operationId);
        if (current == null || phase == null) {
            return CompletableFuture.completedFuture(null);
        }
        Entry updated = new Entry(current.operationId(), current.kind(), current.playerId(), phase,
                current.currencies(), current.materials(), current.error());
        long startedAt = anchorsEnabled() ? System.nanoTime() : 0L;
        Phase from = current.phase();
        save(updated);
        if (anchorsEnabled()) {
            anchorTransition(updated.operationId(), from, phase, startedAt, null);
        }
        if (phase != Phase.COMPLETED) {
            return CompletableFuture.completedFuture(null);
        }
        return archive(updated);
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
        save(new Entry(current.operationId(), current.kind(), current.playerId(), Phase.COMPENSATION_PENDING,
                current.currencies(), current.materials(), error == null ? "" : error));
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
                ? decodeCurrencies(current.currencies())
                : result.remainingCurrencies();
        List<GemDefinition.MaterialCost> materials = result == null
                ? decodeMaterials(current.materials())
                : result.remainingMaterials();
        if (currencies.isEmpty() && materials.isEmpty()) {
            advance(operationId, Phase.COMPLETED);
            return;
        }
        save(new Entry(current.operationId(), current.kind(), current.playerId(), Phase.COMPENSATION_PENDING,
                encodeCurrencies(currencies), encodeMaterials(materials), error == null ? "" : error));
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
                    .thenCompose(_ -> advanceAsync(operationId, Phase.COMPLETED))
                    .whenComplete((_, failure) -> {
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
        save(new Entry(current.operationId(), current.kind(), current.playerId(), Phase.REWARD_PENDING,
                current.currencies(), current.materials(), error == null ? "" : error));
    }

    public void recover(GemEconomyService economyService) {
        journal.loadActive().thenAccept(recovered -> {
            if (recovered.isEmpty()) {
                return;
            }
            List<Entry> entries = new ArrayList<>();
            for (CraftOperationJournal.Entry<GemPayload> e : recovered) {
                journal.restore(e.operationId(), e.kind(), e.playerId(), e.phase(), e.payload());
                entries.add(toGemEntry(e));
            }
            if (!entries.isEmpty()) {
                plugin.getLogger().info("Recoverable gem operations: " + String.join(", ",
                        entries.stream().map(e -> e.operationId() + "(" + e.phase().name() + ")").toList()));
            }
            Runnable apply = () -> applyRecovery(entries, economyService);
            if (scheduling == null) {
                apply.run();
                return;
            }
            scheduling.runGlobal(plugin, apply);
        });
    }

    private void applyRecovery(List<Entry> entries, GemEconomyService economyService) {
        for (Entry entry : entries) {
            switch (entry.phase()) {
                case PREPARED, REWARDED -> advance(entry.operationId(), Phase.COMPLETED);
                case STATE_COMMITTED, REWARD_PENDING -> rewardPending(entry.operationId(),
                        entry.error().isBlank() ? "reward_completion_unknown" : entry.error());
                case CHARGED, COMPENSATION_PENDING -> recoverCompensation(entry, economyService);
                case COMPLETED -> archive(entry);
            }
        }
    }

    private void recoverCompensation(Entry entry, GemEconomyService economyService) {
        Player player = entry.playerId() == null ? null : Bukkit.getPlayer(entry.playerId());
        Runnable recovery = () -> completeAfterRefund(entry.operationId(), "refund_failed",
                economyService.refundPersistedDetailed(player,
                        decodeCurrencies(entry.currencies()), decodeMaterials(entry.materials())));
        if (scheduling.ownsEntity(player)) {
            recovery.run();
            return;
        }
        try {
            scheduling.runForEntity(
                    plugin,
                    player,
                    recovery,
                    () -> compensationPending(entry.operationId(), "owner_schedule_retired")
            );
        } catch (Throwable throwable) {
            compensationPending(entry.operationId(), throwable.getMessage());
        }
    }

    private void save(Entry entry) {
        GemPayload payload = new GemPayload(entry.currencies(), entry.materials(), entry.error());
        if (!journal.contains(entry.operationId())) {
            journal.begin(entry.operationId(), entry.kind(), entry.playerId(),
                    entry.phase().name(), payload);
        } else {
            journal.update(entry.operationId(), entry.phase().name(), payload);
        }
    }

    private Entry load(String operationId) {
        if (operationId == null) {
            return null;
        }
        CraftOperationJournal.Entry<GemPayload> e = journal.get(operationId);
        return e == null ? null : toGemEntry(e);
    }

    private CompletableFuture<Void> archive(Entry entry) {
        return journal.archive(entry.operationId());
    }

    private Entry toGemEntry(CraftOperationJournal.Entry<GemPayload> e) {
        Phase phase;
        try {
            phase = Phase.valueOf(e.phase().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            phase = Phase.COMPENSATION_PENDING;
        }
        GemPayload p = e.payload() != null ? e.payload() : new GemPayload(List.of(), List.of(), "");
        return new Entry(e.operationId(), e.kind(), e.playerId(), phase,
                p.currencies(), p.materials(), p.error());
    }

    private record GemPayload(
            List<Map<String, Object>> currencies,
            List<Map<String, Object>> materials,
            String error) {
    }

    private static final class GemCodec implements CraftOperationJournal.Codec<GemPayload> {

        @Override
        public Map<String, Object> encode(GemPayload payload) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("currencies", payload.currencies());
            values.put("materials", payload.materials());
            values.put("error", payload.error());
            return values;
        }

        @Override
        public GemPayload decode(YamlSection section) {
            return new GemPayload(
                    maps(section.get("currencies")),
                    maps(section.get("materials")),
                    section.getString("error", ""));
        }

        private static List<Map<String, Object>> maps(Object value) {
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof YamlSection section) {
                    result.add(section.asMap());
                } else if (item instanceof Map<?, ?> map) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    map.forEach((key, entry) -> normalized.put(String.valueOf(key), entry));
                    result.add(normalized);
                }
            }
            return List.copyOf(result);
        }
    }

    private List<Map<String, Object>> encodeCurrencies(List<GemDefinition.CurrencyCost> costs) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (GemDefinition.CurrencyCost cost : costs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("provider", cost.provider());
            m.put("currency_id", cost.currencyId());
            m.put("amount", cost.amount());
            values.add(m);
        }
        return List.copyOf(values);
    }

    private List<Map<String, Object>> encodeMaterials(List<GemDefinition.MaterialCost> costs) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (GemDefinition.MaterialCost cost : costs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item", ItemSourceUtil.toShorthand(cost.itemSource()));
            m.put("amount", cost.amount());
            values.add(m);
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
                    0D, "", ""));
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
        if (value instanceof Number n) {
            return n;
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
            List<Map<String, Object>> currencies,
            List<Map<String, Object>> materials,
            String error) {
    }
}