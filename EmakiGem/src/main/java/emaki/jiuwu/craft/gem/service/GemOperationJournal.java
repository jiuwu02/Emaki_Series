package emaki.jiuwu.craft.gem.service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.api.diagnostics.Anchors;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
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

    /** Debug module the journal anchors are filed under; must stay within EmakiGemPlugin's set. */
    private static final String DEBUG_JOURNAL_MODULE = "state";

    private final EmakiGemPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;
    private final Path activeDirectory;
    private final Path completedDirectory;
    private final Path quarantineDirectory;
    private final Map<String, Entry> activeEntries = new ConcurrentHashMap<>();
    private volatile AsyncYamlFiles asyncYamlFiles;
    private volatile FileScope fileScope;
    private volatile boolean asyncFilesUnavailableLogged;

    private GemOperationJournal(EmakiGemPlugin plugin,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
        Path root = plugin.getDataFolder().toPath().resolve("data/operation-journal");
        this.activeDirectory = root.resolve("active");
        this.completedDirectory = root.resolve("completed");
        this.quarantineDirectory = root.resolve("quarantine");
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
        advanceAsync(operationId, phase);
    }

    /**
     * Persists the phase transition and, for {@link Phase#COMPLETED}, archives the entry afterwards.
     * The returned future completes once the journal is durable; archiving only runs when the write
     * succeeded, so a failed write leaves the entry recoverable in {@code active}.
     */
    private CompletableFuture<Void> advanceAsync(String operationId, Phase phase) {
        Entry current = load(operationId);
        if (current == null || phase == null) {
            return CompletableFuture.completedFuture(null);
        }
        Entry updated = new Entry(current.operationId(), current.kind(), current.playerId(), phase,
                current.currencies(), current.materials(), current.error());
        long startedAt = anchorsEnabled() ? System.nanoTime() : 0L;
        Phase from = current.phase();
        CompletableFuture<Void> persisted = save(updated);
        if (anchorsEnabled()) {
            // Anchor on completion rather than on submit, so the line carries the real durability
            // outcome and elapsed time instead of just "a write was queued".
            persisted.whenComplete((ignored, failure) -> anchorTransition(
                    updated.operationId(), from, phase, startedAt, failure));
        }
        if (phase != Phase.COMPLETED) {
            return persisted;
        }
        return persisted.thenCompose(_ -> archive(updated));
    }

    /** {@return whether journal anchors (W6-2a) are currently wanted} */
    private boolean anchorsEnabled() {
        return plugin.debugLogger() != null
                && plugin.debugLogger().shouldLog(DEBUG_JOURNAL_MODULE, (UUID) null);
    }

    /**
     * Records one journal state transition: operationId, the phase edge, elapsed time and the
     * failure cause when the write did not become durable.
     */
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

    /**
     * Reads the journal off the calling thread and applies recovery on the global thread, because
     * compensation touches Bukkit state. Returns immediately; recovery continues in the background.
     */
    public void recover(GemEconomyService economyService) {
        loadActive().thenAccept(entries -> {
            if (entries.isEmpty()) {
                return;
            }
            Runnable apply = () -> applyRecovery(entries, economyService);
            if (executionDispatcher == null) {
                apply.run();
                return;
            }
            if (executionDispatcher.runGlobal(plugin, apply) == null) {
                plugin.getLogger().warning("Gem operation journal recovery was rejected by the scheduler; "
                        + entries.size() + " entries stay pending in active");
            }
        });
    }

    private void applyRecovery(List<Entry> entries, GemEconomyService economyService) {
        for (Entry entry : entries) {
            activeEntries.putIfAbsent(entry.operationId(), entry);
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

    /**
     * Records the entry in memory and persists it off the calling thread. The returned future
     * completes only after the write lands, so callers can gate operation completion on durability.
     * Writes for one operation id share a physical path and are therefore ordered by the file scope.
     */
    private CompletableFuture<Void> save(Entry entry) {
        activeEntries.put(entry.operationId(), entry);
        AsyncYamlFiles files = asyncYamlFiles();
        if (files == null) {
            return logPersistFailure(entry.operationId(), asyncFilesUnavailable("save"));
        }
        return files.save(activePath(entry.operationId()).toFile(), encode(entry))
                .exceptionallyCompose(throwable -> logPersistFailure(entry.operationId(), throwable));
    }

    private CompletableFuture<Void> logPersistFailure(String operationId, Throwable throwable) {
        plugin.getLogger().severe("Failed to persist gem operation " + operationId + ": "
                + rootCauseMessage(throwable));
        return CompletableFuture.failedFuture(new IllegalStateException(
                "Failed to persist gem operation " + operationId, AsyncFailures.unwrap(throwable)));
    }

    /**
     * Returns the in-memory entry for the operation. Active entries are seeded by {@link #recover}
     * at startup and by {@link #begin}, so no blocking read is needed on the calling thread.
     */
    private Entry load(String operationId) {
        return operationId == null ? null : activeEntries.get(operationId);
    }

    /**
     * Lists and decodes every active entry off the calling thread. A single corrupt file is
     * quarantined and skipped instead of discarding the whole journal.
     */
    private CompletableFuture<List<Entry>> loadActive() {
        AsyncYamlFiles files = asyncYamlFiles();
        if (files == null) {
            plugin.getLogger().severe("Cannot recover gem operation journal: async file service is unavailable");
            return CompletableFuture.completedFuture(List.of());
        }
        return files.read("gem-journal-list-active", this::listActiveFiles)
                .thenCompose(this::loadEach)
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to list gem operation journal directory: "
                            + rootCauseMessage(throwable));
                    return List.of();
                });
    }

    private CompletableFuture<List<Entry>> loadEach(List<Path> paths) {
        CompletableFuture<List<Entry>> chain = CompletableFuture.completedFuture(new ArrayList<>());
        for (Path path : paths) {
            chain = chain.thenCompose(entries -> loadOne(path).thenApply(entry -> {
                if (entry != null) {
                    entries.add(entry);
                }
                return entries;
            }));
        }
        return chain.thenApply(entries -> {
            logRecoverable(entries);
            return List.copyOf(entries);
        });
    }

    private CompletableFuture<Entry> loadOne(Path path) {
        AsyncYamlFiles files = asyncYamlFiles();
        if (files == null) {
            return CompletableFuture.completedFuture(null);
        }
        return files.load(path.toFile())
                .thenApply(this::decode)
                .exceptionallyCompose(throwable -> quarantineCorruptFile(path, throwable));
    }

    private List<Path> listActiveFiles() {
        try {
            Files.createDirectories(activeDirectory);
            List<Path> paths = new ArrayList<>();
            try (var stream = Files.newDirectoryStream(activeDirectory, "*.{yml,yaml}")) {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) {
                        paths.add(path);
                    }
                }
            }
            paths.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
            return List.copyOf(paths);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    /**
     * Moves an undecodable file into the quarantine directory on the file scope and resolves to
     * {@code null} so the remaining entries keep loading.
     */
    private CompletableFuture<Entry> quarantineCorruptFile(Path source, Throwable throwable) {
        String fileName = source.getFileName() == null ? "unknown.yml" : source.getFileName().toString();
        String stem = fileName.replaceFirst("(?i)\\.ya?ml$", "");
        Path target = quarantineDirectory.resolve(sanitize(stem) + "-corrupt-" + System.currentTimeMillis() + ".yml");
        FileScope scope = fileScope();
        if (scope == null) {
            plugin.getLogger().warning("Failed to load gem operation journal '" + fileName + "': "
                    + rootCauseMessage(throwable) + "; quarantine skipped because the file scope is unavailable");
            return CompletableFuture.completedFuture(null);
        }
        return scope.write(source, "gem-journal-quarantine:" + fileName, () -> {
            try {
                Files.createDirectories(quarantineDirectory);
                moveReplacing(source, target);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).handle((_, moveFailure) -> {
            if (moveFailure == null) {
                plugin.getLogger().warning("Quarantined corrupt gem operation journal '" + fileName + "' to '"
                        + target.getFileName() + "': " + rootCauseMessage(throwable));
            } else {
                plugin.getLogger().warning("Failed to load gem operation journal '" + fileName + "': "
                        + rootCauseMessage(throwable) + "; quarantine move failed: "
                        + rootCauseMessage(moveFailure));
            }
            return null;
        });
    }

    private void logRecoverable(List<Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        List<String> operationIds = new ArrayList<>();
        for (Entry entry : entries) {
            operationIds.add(entry.operationId() + "(" + entry.phase().name() + ")");
        }
        plugin.getLogger().info("Recoverable gem operations: " + String.join(", ", operationIds));
    }

    private String sanitize(String value) {
        String normalized = value == null ? "operation" : value.replaceAll("[^a-zA-Z0-9._-]+", "_");
        return normalized.isBlank() ? "operation" : normalized;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = AsyncFailures.unwrap(throwable);
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank()
                ? current == null ? "unknown error" : current.getClass().getSimpleName()
                : message;
    }

    /**
     * Moves the finished entry out of {@code active}. The move is enqueued on the same physical path
     * as the preceding writes, so it can never overtake them. A failed archive leaves the file in
     * {@code active} for the next recovery pass, which is why it does not fail the operation.
     */
    private CompletableFuture<Void> archive(Entry entry) {
        Path source = activePath(entry.operationId());
        Path target = completedDirectory.resolve(entry.operationId() + ".yml");
        FileScope scope = fileScope();
        if (scope == null) {
            activeEntries.remove(entry.operationId());
            plugin.getLogger().warning("Failed to archive gem operation " + entry.operationId()
                    + ": async file service is unavailable");
            return CompletableFuture.completedFuture(null);
        }
        return scope.write(source, "gem-journal-archive:" + entry.operationId(), () -> {
            try {
                Files.createDirectories(completedDirectory);
                moveReplacing(source, target);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).handle((_, throwable) -> {
            activeEntries.remove(entry.operationId());
            if (throwable != null) {
                plugin.getLogger().warning("Failed to archive gem operation " + entry.operationId() + ": "
                        + rootCauseMessage(throwable));
            }
            return null;
        });
    }

    private Path activePath(String operationId) {
        return activeDirectory.resolve(operationId + ".yml");
    }

    private AsyncYamlFiles asyncYamlFiles() {
        resolveAsyncFiles();
        return asyncYamlFiles;
    }

    private FileScope fileScope() {
        resolveAsyncFiles();
        return fileScope;
    }

    private void resolveAsyncFiles() {
        if (asyncYamlFiles != null) {
            return;
        }
        synchronized (this) {
            if (asyncYamlFiles != null) {
                return;
            }
            try {
                EmakiCoreLibPlugin coreLib = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
                FileScope scope = coreLib.asyncFileScope(plugin);
                this.fileScope = scope;
                this.asyncYamlFiles = new AsyncYamlFiles(scope);
            } catch (RuntimeException | LinkageError failure) {
                if (!asyncFilesUnavailableLogged) {
                    asyncFilesUnavailableLogged = true;
                    plugin.getLogger().warning("Gem operation journal cannot reach the CoreLib async file service: "
                            + rootCauseMessage(failure));
                }
            }
        }
    }

    private Throwable asyncFilesUnavailable(String operation) {
        return new IllegalStateException(
                "Gem operation journal async file service is unavailable for " + operation);
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
            ItemSourceRef source = ItemSourceUtil.parse(text(value.get("item")));
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
