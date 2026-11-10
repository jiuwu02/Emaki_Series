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

final class GemOperationJournalDelegate {

    private final GemOperationJournal ownerRef;

    GemOperationJournalDelegate(GemOperationJournal ownerRef) {
        this.ownerRef = ownerRef;
    }

    static GemOperationJournal forPlugin(EmakiGemPlugin plugin, EmakiScheduling scheduling) {
        return GemOperationJournal.INSTANCES.computeIfAbsent(plugin, ignored -> new GemOperationJournal(plugin, scheduling));
    }

    static void release(EmakiGemPlugin plugin) {
        if (plugin != null) {
            GemOperationJournal.INSTANCES.remove(plugin);
        }
    }

    String begin(String kind, UUID playerId) {
        return ownerRef.begin(UUID.randomUUID().toString(), kind, playerId);
    }

    String begin(String operationId, String kind, UUID playerId) {
        String resolvedOperationId = operationId == null || operationId.isBlank()
                ? UUID.randomUUID().toString()
                : operationId;
        ownerRef.save(new GemOperationJournal.Entry(resolvedOperationId, kind, playerId, GemOperationJournal.Phase.PREPARED, GemOperationJournal.GemPayload.empty()));
        return resolvedOperationId;
    }

    boolean contains(String operationId) {
        return operationId != null && ownerRef.journal.contains(operationId);
    }

    void rerollRefreshDegraded(String operationId, String reason) {
        GemOperationJournal.Entry current = ownerRef.load(operationId);
        if (current == null) {
            return;
        }
        String resolved = reason == null || reason.isBlank() ? "refresh_unknown" : reason;
        ownerRef.save(current.withPhase(current.phase(), current.payload()
                .withError(resolved)
                .withEvent("refresh_degraded:" + resolved)));
    }

    void advance(String operationId, GemOperationJournal.Phase phase) {
        ownerRef.advanceAsync(operationId, phase);
    }

    void anchorTransition(String operationId, GemOperationJournal.Phase from, GemOperationJournal.Phase to, long startedAt, Throwable failure) {
        if (!ownerRef.anchorsEnabled()) {
            return;
        }
        Anchors.Builder anchor = Anchors.of()
                .op(operationId)
                .phase(from + "->" + to)
                .elapsedMs((System.nanoTime() - startedAt) / 1_000_000L);
        if (failure != null) {
            anchor.cause(AsyncFailures.unwrap(failure));
        }
        String entry = anchor.render();
        ownerRef.plugin.debugLogger().log(GemOperationJournal.DEBUG_JOURNAL_MODULE, (UUID) null, "journal.transition",
                Map.of("entry", entry == null ? "" : entry));
    }

    boolean isReroll(GemOperationJournal.Entry entry) {
        return entry.kind() != null && entry.kind().startsWith("gem-reroll-");
    }

    CompletableFuture<Void> archive(GemOperationJournal.Entry entry) {
        ownerRef.compensationGates.remove(entry.operationId());
        return ownerRef.journal.archive(entry.operationId());
    }

    List<Map<String, Object>> encodeCurrencies(List<GemDefinition.CurrencyCost> costs) {
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

    List<Map<String, Object>> encodeMaterials(List<GemDefinition.MaterialCost> costs) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (GemDefinition.MaterialCost cost : costs) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("item", ItemSourceUtil.toShorthand(cost.itemSource()));
            value.put("amount", cost.amount());
            values.add(value);
        }
        return List.copyOf(values);
    }

    List<GemDefinition.CurrencyCost> decodeCurrencies(List<Map<String, Object>> values) {
        List<GemDefinition.CurrencyCost> costs = new ArrayList<>();
        for (Map<String, Object> value : values) {
            costs.add(new GemDefinition.CurrencyCost(
                    ownerRef.text(value.get("provider")),
                    ownerRef.text(value.get("currency_id")),
                    ownerRef.number(value.get("amount")).doubleValue(),
                    0D,
                    "",
                    ""));
        }
        return List.copyOf(costs);
    }

    List<GemDefinition.MaterialCost> decodeMaterials(List<Map<String, Object>> values) {
        List<GemDefinition.MaterialCost> costs = new ArrayList<>();
        for (Map<String, Object> value : values) {
            ItemSourceRef source = ItemSourceUtil.parse(ownerRef.text(value.get("item")));
            if (source != null) {
                costs.add(new GemDefinition.MaterialCost(source, number(value.get("amount")).intValue()));
            }
        }
        return List.copyOf(costs);
    }

    String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        try {
            return Double.parseDouble(ownerRef.text(value));
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

}
