package emaki.jiuwu.craft.level.service;

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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

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

    private final Plugin plugin;
    private final EmakiScheduling scheduling;
    private final Path activeDirectory;
    private final Path completedDirectory;
    private final Path quarantineDirectory;
    private final Map<String, Entry> activeEntries = new ConcurrentHashMap<>();
    private volatile AsyncYamlFiles asyncYamlFiles;
    private volatile FileScope fileScope;
    private volatile boolean asyncFilesUnavailableLogged;

    LevelOperationJournal(Plugin plugin, EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
        Path root = plugin.getDataFolder().toPath().resolve("data/operation-journal");
        this.activeDirectory = root.resolve("active");
        this.completedDirectory = root.resolve("completed");
        this.quarantineDirectory = root.resolve("quarantine");
    }

    String begin(String kind, UUID playerId) {
        String operationId = UUID.randomUUID().toString();
        save(new Entry(operationId, kind, playerId, Phase.PREPARED, List.of(), List.of(), ""));
        return operationId;
    }

    void preparedCosts(String operationId,
            List<LevelCostTransaction.CurrencyCharge> currencies,
            List<LevelCostTransaction.MaterialCharge> materials) {
        Entry current = load(operationId);
        if (current != null) {
            save(new Entry(current.operationId(), current.kind(), current.playerId(), current.phase(),
                    encodeCurrencies(currencies), encodeMaterials(materials), current.error()));
        }
    }

    void charged(String operationId, LevelCostTransaction.Result result) {
        Entry current = load(operationId);
        if (current == null || result == null || !result.success()) {
            return;
        }
        save(new Entry(current.operationId(), current.kind(), current.playerId(), Phase.CHARGED,
                encodeCurrencies(result.chargedCurrencies()), encodeMaterials(result.chargedMaterials()), ""));
    }

    void advance(String operationId, Phase phase) {
        advanceAsync(operationId, phase);
    }

    private CompletableFuture<Void> advanceAsync(String operationId, Phase phase) {
        Entry current = load(operationId);
        if (current == null || phase == null) {
            return CompletableFuture.completedFuture(null);
        }
        Entry updated = new Entry(current.operationId(), current.kind(), current.playerId(), phase,
                current.currencies(), current.materials(), current.error());
        CompletableFuture<Void> persisted = save(updated);
        if (phase != Phase.COMPLETED) {
            return persisted;
        }
        return persisted.thenCompose(_ -> archive(updated));
    }

    void failedCharge(String operationId, LevelCostTransaction.Result result) {
        if (result == null || result.compensationComplete()) {
            advance(operationId, Phase.COMPLETED);
            return;
        }
        Entry current = load(operationId);
        if (current == null) {
            return;
        }
        save(new Entry(current.operationId(), current.kind(), current.playerId(), Phase.COMPENSATION_PENDING,
                encodeCurrencies(result.remainingCurrencies()), encodeMaterials(result.remainingMaterials()),
                result.failureReason()));
    }

    void compensationPending(String operationId, String error) {
        Entry current = load(operationId);
        if (current != null) {
            save(new Entry(current.operationId(), current.kind(), current.playerId(), Phase.COMPENSATION_PENDING,
                    current.currencies(), current.materials(), error == null ? "" : error));
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
            save(new Entry(current.operationId(), current.kind(), current.playerId(), Phase.REWARD_PENDING,
                    current.currencies(), current.materials(), error == null ? "" : error));
        }
    }

    void recover(EconomyManager economyManager, ItemSourceService itemSourceService) {
        loadActive().thenAccept(entries -> {
            if (entries.isEmpty()) {
                return;
            }
            Runnable apply = () -> applyRecovery(entries, economyManager, itemSourceService);
            scheduling.runGlobal(plugin, apply);
        });
    }

    private void applyRecovery(List<Entry> entries,
            EconomyManager economyManager,
            ItemSourceService itemSourceService) {
        for (Entry entry : entries) {
            activeEntries.putIfAbsent(entry.operationId(), entry);
            switch (entry.phase()) {
                case PREPARED, REWARDED -> advance(entry.operationId(), Phase.COMPLETED);
                case STATE_COMMITTED, REWARD_PENDING -> rewardPending(entry.operationId(),
                        entry.error().isBlank() ? "reward_completion_unknown" : entry.error());
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
                save(new Entry(entry.operationId(), entry.kind(), entry.playerId(), Phase.COMPENSATION_PENDING,
                        result.remainingCurrencies(), result.remainingMaterials(), "refund_failed"));
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
        for (Map<String, Object> currency : entry.currencies()) {
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
        for (Map<String, Object> material : entry.materials()) {
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
        plugin.getLogger().severe("Failed to persist level operation " + operationId + ": "
                + rootCauseMessage(throwable));
        return CompletableFuture.failedFuture(new IllegalStateException(
                "Failed to persist level operation " + operationId, AsyncFailures.unwrap(throwable)));
    }

    private Entry load(String operationId) {
        return operationId == null ? null : activeEntries.get(operationId);
    }

    private CompletableFuture<List<Entry>> loadActive() {
        AsyncYamlFiles files = asyncYamlFiles();
        if (files == null) {
            plugin.getLogger().severe("Cannot recover level operation journal: async file service is unavailable");
            return CompletableFuture.completedFuture(List.of());
        }
        return files.read("level-journal-list-active", this::listActiveFiles)
                .thenCompose(this::loadEach)
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to list level operation journal directory: "
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

    private CompletableFuture<Entry> quarantineCorruptFile(Path source, Throwable throwable) {
        String fileName = source.getFileName() == null ? "unknown.yml" : source.getFileName().toString();
        String stem = fileName.replaceFirst("(?i)\\.ya?ml$", "");
        Path target = quarantineDirectory.resolve(sanitize(stem) + "-corrupt-" + System.currentTimeMillis() + ".yml");
        FileScope scope = fileScope();
        if (scope == null) {
            plugin.getLogger().warning("Failed to load level operation journal '" + fileName + "': "
                    + rootCauseMessage(throwable) + "; quarantine skipped because the file scope is unavailable");
            return CompletableFuture.completedFuture(null);
        }
        return scope.write(source, "level-journal-quarantine:" + fileName, () -> {
            try {
                Files.createDirectories(quarantineDirectory);
                moveReplacing(source, target);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).handle((_, moveFailure) -> {
            if (moveFailure == null) {
                plugin.getLogger().warning("Quarantined corrupt level operation journal '" + fileName + "' to '"
                        + target.getFileName() + "': " + rootCauseMessage(throwable));
            } else {
                plugin.getLogger().warning("Failed to load level operation journal '" + fileName + "': "
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
        plugin.getLogger().info("Recoverable level operations: " + String.join(", ", operationIds));
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

    private CompletableFuture<Void> archive(Entry entry) {
        Path source = activePath(entry.operationId());
        Path target = completedDirectory.resolve(entry.operationId() + ".yml");
        FileScope scope = fileScope();
        if (scope == null) {
            activeEntries.remove(entry.operationId());
            plugin.getLogger().warning("Failed to archive level operation " + entry.operationId()
                    + ": async file service is unavailable");
            return CompletableFuture.completedFuture(null);
        }
        return scope.write(source, "level-journal-archive:" + entry.operationId(), () -> {
            try {
                Files.createDirectories(completedDirectory);
                moveReplacing(source, target);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).handle((_, throwable) -> {
            activeEntries.remove(entry.operationId());
            if (throwable != null) {
                plugin.getLogger().warning("Failed to archive level operation " + entry.operationId() + ": "
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
                    plugin.getLogger().warning("Level operation journal cannot reach the CoreLib async file service: "
                            + rootCauseMessage(failure));
                }
            }
        }
    }

    private Throwable asyncFilesUnavailable(String operation) {
        return new IllegalStateException(
                "Level operation journal async file service is unavailable for " + operation);
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

    private List<Map<String, Object>> encodeCurrencies(List<LevelCostTransaction.CurrencyCharge> costs) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (costs != null) {
            for (LevelCostTransaction.CurrencyCharge cost : costs) {
                values.add(Map.of("provider", cost.provider(), "currency_id", cost.currencyId(), "amount", cost.amount()));
            }
        }
        return List.copyOf(values);
    }

    private List<Map<String, Object>> encodeMaterials(List<LevelCostTransaction.MaterialCharge> costs) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (costs != null) {
            for (LevelCostTransaction.MaterialCharge cost : costs) {
                values.add(Map.of("item_sources", cost.itemSources(), "amount", cost.amount()));
            }
        }
        return List.copyOf(values);
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

    private record Entry(String operationId, String kind, UUID playerId, Phase phase,
            List<Map<String, Object>> currencies, List<Map<String, Object>> materials, String error) {
    }
}
