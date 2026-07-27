package emaki.jiuwu.craft.level.service;

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

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;


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
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;
    private final Path activeDirectory;
    private final Path completedDirectory;
    private final Object ioLock = new Object();

    LevelOperationJournal(Plugin plugin,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
        Path root = plugin.getDataFolder().toPath().resolve("data/operation-journal");
        this.activeDirectory = root.resolve("active");
        this.completedDirectory = root.resolve("completed");
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

    void completeAfterActions(String operationId, java.util.concurrent.CompletionStage<Boolean> actions) {
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
            advance(operationId, Phase.REWARDED);
            advance(operationId, Phase.COMPLETED);
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
        for (Entry entry : loadActive()) {
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
                    ItemSource source = ItemSourceUtil.parse(text(sourceValue));
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
        synchronized (ioLock) {
            try {
                YamlFiles.save(activePath(entry.operationId()).toFile(), encode(entry));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to persist level operation " + entry.operationId(), exception);
            }
        }
    }

    private Entry load(String operationId) {
        Path path = activePath(operationId);
        return Files.isRegularFile(path) ? decode(YamlFiles.load(path.toFile())) : null;
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
                plugin.getLogger().severe("Failed to recover level operation journal: " + exception.getMessage());
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
                plugin.getLogger().warning("Failed to archive level operation " + entry.operationId() + ": " + exception.getMessage());
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
