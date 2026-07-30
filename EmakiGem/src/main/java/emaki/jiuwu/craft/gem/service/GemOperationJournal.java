package emaki.jiuwu.craft.gem.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
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

    private final EmakiGemPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;
    private final Path activeDirectory;
    private final Path completedDirectory;
    private final Object ioLock = new Object();

    private GemOperationJournal(EmakiGemPlugin plugin,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
        Path root = plugin.getDataFolder().toPath().resolve("data/operation-journal");
        this.activeDirectory = root.resolve("active");
        this.completedDirectory = root.resolve("completed");
    }

    public static GemOperationJournal forPlugin(EmakiGemPlugin plugin,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        return INSTANCES.computeIfAbsent(plugin,
                ignored -> new GemOperationJournal(plugin, executionDispatcher, threadOwnership));
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
        Entry current = load(operationId);
        if (current == null || phase == null) {
            return;
        }
        Entry updated = new Entry(current.operationId(), current.kind(), current.playerId(), phase,
                current.currencies(), current.materials(), current.error());
        save(updated);
        if (phase == Phase.COMPLETED) {
            archive(updated);
        }
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
            try {
                advance(operationId, Phase.REWARDED);
                advance(operationId, Phase.COMPLETED);
                completion.complete(true);
            } catch (Throwable failure) {
                completion.completeExceptionally(failure);
            }
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
        for (Entry entry : loadActive()) {
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
        if (player == null || !player.isOnline() || economyService == null) {
            compensationPending(entry.operationId(), "player_offline");
            return;
        }
        Runnable recovery = () -> completeAfterRefund(entry.operationId(), "refund_failed",
                economyService.refundPersistedDetailed(player,
                        decodeCurrencies(entry.currencies()), decodeMaterials(entry.materials())));
        if (threadOwnership.isEntityOwned(player)) {
            recovery.run();
            return;
        }
        try {
            TaskHandle scheduled = executionDispatcher.runEntity(
                    plugin,
                    player,
                    recovery,
                    () -> compensationPending(entry.operationId(), "owner_schedule_retired")
            );
            if (scheduled == null) {
                compensationPending(entry.operationId(), "owner_schedule_rejected");
            }
        } catch (Throwable throwable) {
            compensationPending(entry.operationId(), throwable.getMessage());
        }
    }

    private void save(Entry entry) {
        synchronized (ioLock) {
            try {
                YamlFiles.save(activePath(entry.operationId()).toFile(), encode(entry));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to persist gem operation " + entry.operationId(), exception);
            }
        }
    }

    private Entry load(String operationId) {
        Path path = activePath(operationId);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        return decode(YamlFiles.load(path.toFile()));
    }

    private List<Entry> loadActive() {
        synchronized (ioLock) {
            try {
                Files.createDirectories(activeDirectory);
                try (var stream = Files.list(activeDirectory)) {
                    return stream.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                            .map(path -> decode(YamlFiles.load(path.toFile())))
                            .toList();
                }
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().severe("Failed to recover gem operation journal: " + exception.getMessage());
                return List.of();
            }
        }
    }

    private void archive(Entry entry) {
        synchronized (ioLock) {
            try {
                Files.createDirectories(completedDirectory);
                Files.move(activePath(entry.operationId()), completedDirectory.resolve(entry.operationId() + ".yml"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to archive gem operation " + entry.operationId() + ": " + exception.getMessage());
            }
        }
    }

    private Path activePath(String operationId) {
        return activeDirectory.resolve(operationId + ".yml");
    }

    private Map<String, Object> encode(Entry entry) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("operation_id", entry.operationId());
        values.put("kind", entry.kind());
        values.put("player_id", entry.playerId() == null ? "" : entry.playerId().toString());
        values.put("phase", entry.phase().name());
        values.put("currencies", entry.currencies());
        values.put("materials", entry.materials());
        values.put("error", entry.error());
        return values;
    }

    private Entry decode(YamlSection section) {
        UUID playerId = null;
        try {
            playerId = UUID.fromString(section.getString("player_id", ""));
        } catch (IllegalArgumentException ignored) {
        }
        return new Entry(section.getString("operation_id", ""), section.getString("kind", ""), playerId,
                Phase.valueOf(section.getString("phase", Phase.COMPENSATION_PENDING.name()).toUpperCase(Locale.ROOT)),
                maps(section.get("currencies")), maps(section.get("materials")), section.getString("error", ""));
    }

    private List<Map<String, Object>> encodeCurrencies(List<GemDefinition.CurrencyCost> costs) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (GemDefinition.CurrencyCost cost : costs) {
            values.add(Map.of("provider", cost.provider(), "currency_id", cost.currencyId(), "amount", cost.amount()));
        }
        return List.copyOf(values);
    }

    private List<Map<String, Object>> encodeMaterials(List<GemDefinition.MaterialCost> costs) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (GemDefinition.MaterialCost cost : costs) {
            values.add(Map.of("item", ItemSourceUtil.toShorthand(cost.itemSource()), "amount", cost.amount()));
        }
        return List.copyOf(values);
    }

    private List<GemDefinition.CurrencyCost> decodeCurrencies(List<Map<String, Object>> values) {
        List<GemDefinition.CurrencyCost> costs = new ArrayList<>();
        for (Map<String, Object> value : values) {
            costs.add(new GemDefinition.CurrencyCost(text(value.get("provider")), text(value.get("currency_id")),
                    number(value.get("amount")).doubleValue(), 0D, "", ""));
        }
        return List.copyOf(costs);
    }

    private List<GemDefinition.MaterialCost> decodeMaterials(List<Map<String, Object>> values) {
        List<GemDefinition.MaterialCost> costs = new ArrayList<>();
        for (Map<String, Object> value : values) {
            ItemSource source = ItemSourceUtil.parse(text(value.get("item")));
            if (source != null) {
                costs.add(new GemDefinition.MaterialCost(source, number(value.get("amount")).intValue()));
            }
        }
        return List.copyOf(costs);
    }

    private List<Map<String, Object>> maps(Object value) {
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

    private record Entry(String operationId, String kind, UUID playerId, Phase phase,
            List<Map<String, Object>> currencies, List<Map<String, Object>> materials, String error) {
    }
}
