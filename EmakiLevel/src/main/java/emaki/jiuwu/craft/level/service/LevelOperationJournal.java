package emaki.jiuwu.craft.level.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.cost.CostReceipt;
import emaki.jiuwu.craft.corelib.cost.CostTransaction;
import emaki.jiuwu.craft.corelib.craft.CraftOperationJournal;

final class LevelOperationJournal {

    enum Phase {
        PREPARED,
        CHARGED,
        STATE_COMMITTED,
        REWARD_PENDING,
        REWARDED,
        COMPLETED,
        COMPENSATION_PENDING
    }

    private final JavaPlugin plugin;
    private final EmakiScheduling scheduling;
    private final CraftOperationJournal<LevelPayload> journal;

    LevelOperationJournal(JavaPlugin plugin, EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
        Path root = plugin.getDataFolder().toPath().resolve("data/operation-journal");
        this.journal = CraftOperationJournal.ofPersisted(Integer.MAX_VALUE, new LevelCodec(), plugin, root);
    }

    String begin(String kind, UUID playerId) {
        String operationId = UUID.randomUUID().toString();
        save(new Entry(operationId, kind, playerId, Phase.PREPARED, LevelPayload.empty()));
        return operationId;
    }

    void preparedCosts(String operationId,
            List<CostTransaction.CurrencyCharge> currencies,
            List<CostTransaction.MaterialSource> materials) {
        Entry current = load(operationId);
        if (current != null) {
            save(current.withPhase(current.phase(), current.payload().withCosts(
                    encodeCurrencies(currencies), encodePlannedMaterials(materials))));
        }
    }

    void charged(String operationId, CostReceipt receipt) {
        Entry current = load(operationId);
        if (current == null || receipt == null || !receipt.success()) {
            return;
        }
        save(current.withPhase(Phase.CHARGED, current.payload()
                .withCosts(encodeCurrencyRecords(receipt.chargedCurrencies()),
                        encodeMaterialRecords(receipt.chargedMaterials()))
                .withError("")));
    }

    void advance(String operationId, Phase phase) {
        advanceAsync(operationId, phase);
    }

    private CompletableFuture<Void> advanceAsync(String operationId, Phase phase) {
        Entry current = load(operationId);
        if (current == null || phase == null) {
            return CompletableFuture.completedFuture(null);
        }
        Entry updated = current.withPhase(phase, current.payload());
        save(updated);
        if (phase != Phase.COMPLETED) {
            return CompletableFuture.completedFuture(null);
        }
        return archive(updated);
    }

    void failedCharge(String operationId, CostReceipt receipt) {
        if (receipt == null || receipt.compensationComplete()) {
            advance(operationId, Phase.COMPLETED);
            return;
        }
        Entry current = load(operationId);
        if (current == null) {
            return;
        }
        save(current.withPhase(Phase.COMPENSATION_PENDING, current.payload()
                .withCosts(encodeCurrencyRecords(receipt.remainingCurrencies()),
                        encodeMaterialRecords(receipt.remainingMaterials()))
                .withError(receipt.failureReason().name().toLowerCase(Locale.ROOT))));
    }

    void compensationPending(String operationId, String error) {
        Entry current = load(operationId);
        if (current != null) {
            save(current.withPhase(Phase.COMPENSATION_PENDING,
                    current.payload().withError(error == null ? "" : error)));
        }
    }

    void completeAfterActions(String operationId, CompletionStage<Boolean> actions) {
        advance(operationId, Phase.REWARD_PENDING);
        if (actions == null) {
            rewardPending(operationId, "action_stage_missing");
            return;
        }
        actions.whenComplete((success, throwable) -> {
            if (throwable != null) {
                rewardPending(operationId, throwable.getMessage());
                return;
            }
            if (!Boolean.TRUE.equals(success)) {
                rewardPending(operationId, "action_failed");
                return;
            }
            advanceAsync(operationId, Phase.REWARDED)
                    .thenCompose(_ -> advanceAsync(operationId, Phase.COMPLETED))
                    .whenComplete((_, failure) -> {
                        if (failure != null) {
                            plugin.getLogger().severe("Level operation " + operationId
                                    + " completed its actions but the journal write failed; the entry stays in active"
                                    + " for the next recovery pass: " + rootCauseMessage(failure));
                        }
                    });
        });
    }

    void rewardPending(String operationId, String error) {
        Entry current = load(operationId);
        if (current != null) {
            save(current.withPhase(Phase.REWARD_PENDING,
                    current.payload().withError(error == null ? "" : error)));
        }
    }

    void recover(EconomyManager economyManager, ItemSourceService itemSourceService) {
        journal.loadActive().thenAccept(recovered -> {
            if (recovered.isEmpty()) {
                return;
            }
            List<Entry> entries = new ArrayList<>();
            for (CraftOperationJournal.Entry<LevelPayload> item : recovered) {
                if (!journal.contains(item.operationId())) {
                    journal.restore(item.operationId(), item.kind(), item.playerId(), item.phase(), item.payload());
                }
                entries.add(toLevelEntry(item));
            }
            logRecoverable(entries);
            Runnable apply = () -> applyRecovery(entries, economyManager, itemSourceService);
            scheduling.runGlobal(plugin, apply);
        });
    }

    private void applyRecovery(List<Entry> entries,
            EconomyManager economyManager,
            ItemSourceService itemSourceService) {
        for (Entry entry : entries) {
            switch (entry.phase()) {
                case PREPARED, REWARDED -> advance(entry.operationId(), Phase.COMPLETED);
                case STATE_COMMITTED, REWARD_PENDING -> rewardPending(entry.operationId(),
                        entry.payload().error().isBlank()
                                ? "reward_completion_unknown"
                                : entry.payload().error());
                case CHARGED, COMPENSATION_PENDING -> recoverCompensation(entry, economyManager, itemSourceService);
                case COMPLETED -> archive(entry);
            }
        }
    }

    private void recoverCompensation(Entry entry, EconomyManager economyManager, ItemSourceService itemSourceService) {
        Player player = entry.playerId() == null ? null : Bukkit.getPlayer(entry.playerId());
        if (player == null || !player.isOnline()) {
            compensationPending(entry.operationId(), "player_offline");
            return;
        }
        Runnable recovery = () -> {
            RefundResult result = refund(player, economyManager, itemSourceService, entry);
            if (result.complete()) {
                advance(entry.operationId(), Phase.COMPLETED);
            } else {
                save(entry.withPhase(Phase.COMPENSATION_PENDING, entry.payload()
                        .withCosts(result.remainingCurrencies(), result.remainingMaterials())
                        .withError("refund_failed")));
            }
        };
        if (scheduling.ownsEntity(player)) {
            recovery.run();
            return;
        }
        try {
            scheduling.runForEntity(plugin, player, recovery, null);
        } catch (Throwable throwable) {
            compensationPending(entry.operationId(), throwable.getMessage());
        }
    }

    private RefundResult refund(Player player,
            EconomyManager economyManager,
            ItemSourceService itemSourceService,
            Entry entry) {
        List<Map<String, Object>> remainingCurrencies = new ArrayList<>();
        for (Map<String, Object> currency : entry.payload().currencies()) {
            boolean restored = false;
            if (economyManager != null) {
                try {
                    var result = economyManager.add(player,
                            text(currency.get("provider")),
                            text(currency.get("currency_id")),
                            number(currency.get("amount")).doubleValue());
                    restored = result != null && result.success();
                } catch (RuntimeException | LinkageError ignored) {
                    restored = false;
                }
            }
            if (!restored) {
                remainingCurrencies.add(currency);
            }
        }
        List<Map<String, Object>> remainingMaterials = new ArrayList<>();
        for (Map<String, Object> material : entry.payload().materials()) {
            long amount = number(material.get("amount")).longValue();
            boolean restored = false;
            Object sourcesValue = material.get("item_sources");
            if (sourcesValue instanceof List<?> sources) {
                for (Object sourceValue : sources) {
                    ItemSourceRef source = ItemSourceUtil.parse(text(sourceValue));
                    if (source == null || itemSourceService == null) {
                        continue;
                    }
                    var item = itemSourceService.createItem(
                            source, (int) Math.min(Integer.MAX_VALUE, Math.max(1L, amount)));
                    if (item != null) {
                        InventoryItemUtil.giveOrDrop(player, item);
                        restored = true;
                        break;
                    }
                }
            }
            if (!restored) {
                remainingMaterials.add(material);
            }
        }
        return new RefundResult(remainingCurrencies, remainingMaterials);
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
        CraftOperationJournal.Entry<LevelPayload> entry = journal.get(operationId);
        return entry == null ? null : toLevelEntry(entry);
    }

    private CompletableFuture<Void> archive(Entry entry) {
        return journal.archive(entry.operationId());
    }

    private Entry toLevelEntry(CraftOperationJournal.Entry<LevelPayload> entry) {
        Phase phase;
        try {
            phase = Phase.valueOf(entry.phase().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            phase = Phase.COMPENSATION_PENDING;
        }
        LevelPayload payload = entry.payload() == null ? LevelPayload.empty() : entry.payload();
        return new Entry(entry.operationId(), entry.kind(), entry.playerId(), phase, payload);
    }

    private void logRecoverable(List<Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        List<String> operationIds = new ArrayList<>();
        for (Entry entry : entries) {
            operationIds.add(entry.operationId() + "(" + entry.phase().name() + ")");
        }
        plugin.getLogger().info("Recoverable level operations: " + String.join(", ", operationIds));
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = AsyncFailures.unwrap(throwable);
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank()
                ? current == null ? "unknown error" : current.getClass().getSimpleName()
                : message;
    }

    private List<Map<String, Object>> encodeCurrencies(List<CostTransaction.CurrencyCharge> costs) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (costs != null) {
            for (CostTransaction.CurrencyCharge cost : costs) {
                values.add(Map.of("provider", cost.provider(), "currency_id", cost.currencyId(), "amount", cost.amount()));
            }
        }
        return List.copyOf(values);
    }

    private List<Map<String, Object>> encodePlannedMaterials(List<CostTransaction.MaterialSource> sources) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (sources != null) {
            for (CostTransaction.MaterialSource source : sources) {
                values.add(Map.of("item_sources", source.itemTokens(), "amount", source.amount()));
            }
        }
        return List.copyOf(values);
    }

    private List<Map<String, Object>> encodeCurrencyRecords(List<CostReceipt.CurrencyRecord> records) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (records != null) {
            for (CostReceipt.CurrencyRecord record : records) {
                values.add(Map.of("provider", record.provider(), "currency_id", record.currencyId(), "amount", record.amount()));
            }
        }
        return List.copyOf(values);
    }

    private List<Map<String, Object>> encodeMaterialRecords(List<CostReceipt.MaterialRecord> records) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (records != null) {
            for (CostReceipt.MaterialRecord record : records) {
                values.add(Map.of("item_sources", record.itemTokens(), "amount", record.amount()));
            }
        }
        return List.copyOf(values);
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

    private record RefundResult(List<Map<String, Object>> remainingCurrencies,
            List<Map<String, Object>> remainingMaterials) {

        private RefundResult {
            remainingCurrencies = remainingCurrencies == null ? List.of() : List.copyOf(remainingCurrencies);
            remainingMaterials = remainingMaterials == null ? List.of() : List.copyOf(remainingMaterials);
        }

        private boolean complete() {
            return remainingCurrencies.isEmpty() && remainingMaterials.isEmpty();
        }
    }

    private record LevelPayload(List<Map<String, Object>> currencies,
            List<Map<String, Object>> materials,
            String error) {

        private LevelPayload {
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
            materials = materials == null ? List.of() : List.copyOf(materials);
            error = error == null ? "" : error;
        }

        private static LevelPayload empty() {
            return new LevelPayload(List.of(), List.of(), "");
        }

        private LevelPayload withCosts(List<Map<String, Object>> nextCurrencies,
                List<Map<String, Object>> nextMaterials) {
            return new LevelPayload(nextCurrencies, nextMaterials, error);
        }

        private LevelPayload withError(String nextError) {
            return new LevelPayload(currencies, materials, nextError);
        }
    }

    private static final class LevelCodec implements CraftOperationJournal.Codec<LevelPayload> {

        @Override
        public Map<String, Object> encode(LevelPayload payload) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("currencies", payload.currencies());
            values.put("materials", payload.materials());
            values.put("error", payload.error());
            return values;
        }

        @Override
        public LevelPayload decode(YamlSection section) {
            return new LevelPayload(maps(section.get("currencies")), maps(section.get("materials")),
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

    private record Entry(String operationId, String kind, UUID playerId, Phase phase, LevelPayload payload) {

        private Entry withPhase(Phase nextPhase, LevelPayload nextPayload) {
            return new Entry(operationId, kind, playerId, nextPhase, nextPayload);
        }
    }
}
