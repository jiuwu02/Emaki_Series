package emaki.jiuwu.craft.cooking.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

/** Durable receiver acknowledgements keyed by the stable cooking delivery unit id. */
final class CookingDeliveryLedgerStore {

    private static final int SCHEMA_VERSION = 1;

    private final Path directory;
    private final FileScope fileScope;
    private final boolean memoryOnly;
    private final ConcurrentMap<Path, Object> synchronousPathLocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Receipt> confirmed = new ConcurrentHashMap<>();

    CookingDeliveryLedgerStore(JavaPlugin plugin, FileScope fileScope) {
        this(Objects.requireNonNull(plugin, "plugin").getDataFolder(), fileScope, false);
    }

    CookingDeliveryLedgerStore(File dataFolder, FileScope fileScope) {
        this(dataFolder, fileScope, false);
    }

    private CookingDeliveryLedgerStore(File dataFolder, FileScope fileScope, boolean memoryOnly) {
        this.directory = Objects.requireNonNull(dataFolder, "dataFolder").toPath()
                .resolve("data/completions/delivery-ledger");
        this.fileScope = fileScope;
        this.memoryOnly = memoryOnly;
    }

    static CookingDeliveryLedgerStore inMemory() {
        return new CookingDeliveryLedgerStore(new File("."), null, true);
    }

    CompletableFuture<Boolean> isConfirmed(String unitId, String kind, Map<String, Object> payload) {
        if (memoryOnly) {
            return CompletableFuture.completedFuture(confirmedReceipt(unitId, kind, payload) != null);
        }
        Receipt expected = expected(unitId, kind, payload, ReceiptState.CONFIRMED);
        Receipt cached = confirmed.get(expected.unitId());
        if (cached != null) {
            if (!matches(cached, expected)) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Cooking delivery unit id was reused with different kind or payload: " + expected.unitId()));
            }
            return CompletableFuture.completedFuture(cached.state() == ReceiptState.CONFIRMED);
        }
        Path path = pathFor(expected.unitId());
        return read(path, "cooking-delivery-ledger-read:" + expected.unitId(), () -> load(path)).thenApply(receipt -> {
            if (receipt == null) {
                return false;
            }
            requireMatching(receipt, expected);
            if (receipt.state() == ReceiptState.CONFIRMED) {
                confirmed.put(receipt.unitId(), receipt);
                return true;
            }
            return false;
        });
    }

    CompletableFuture<Void> recordIntent(String unitId, String kind, Map<String, Object> payload) {
        if (memoryOnly) {
            expected(unitId, kind, payload, ReceiptState.INTENT);
            return CompletableFuture.completedFuture(null);
        }
        Receipt intent = expected(unitId, kind, payload, ReceiptState.INTENT);
        Path path = pathFor(intent.unitId());
        return write(path, "cooking-delivery-ledger-intent:" + intent.unitId(), () -> {
            Receipt existing = load(path);
            if (existing != null) {
                requireMatching(existing, intent);
                return;
            }
            save(path, intent);
        });
    }

    CompletableFuture<Boolean> confirm(String unitId, String kind, Map<String, Object> payload) {
        if (memoryOnly) {
            rememberConfirmed(unitId, kind, payload);
            return CompletableFuture.completedFuture(true);
        }
        Receipt acknowledgement = expected(unitId, kind, payload, ReceiptState.CONFIRMED);
        Path path = pathFor(acknowledgement.unitId());
        return write(path, "cooking-delivery-ledger-confirm:" + acknowledgement.unitId(), () -> {
            Receipt existing = load(path);
            if (existing != null) {
                requireMatching(existing, acknowledgement);
                if (existing.state() == ReceiptState.CONFIRMED) {
                    confirmed.put(existing.unitId(), existing);
                    return;
                }
            }
            save(path, acknowledgement);
            confirmed.put(acknowledgement.unitId(), acknowledgement);
        }).thenApply(_ -> true);
    }

    private Receipt confirmedReceipt(String unitId, String kind, Map<String, Object> payload) {
        Receipt expected = expected(unitId, kind, payload, ReceiptState.CONFIRMED);
        Receipt receipt = confirmed.get(expected.unitId());
        if (receipt != null) {
            requireMatching(receipt, expected);
        }
        return receipt != null && receipt.state() == ReceiptState.CONFIRMED ? receipt : null;
    }

    private void rememberConfirmed(String unitId, String kind, Map<String, Object> payload) {
        Receipt receipt = expected(unitId, kind, payload, ReceiptState.CONFIRMED);
        confirmed.put(receipt.unitId(), receipt);
    }

    private Receipt expected(String unitId, String kind, Map<String, Object> payload, ReceiptState state) {
        String stableId = Texts.toStringSafe(unitId).trim();
        String stableKind = Texts.toStringSafe(kind).trim();
        if (stableId.isBlank() || stableKind.isBlank()) {
            throw new IllegalArgumentException("Cooking delivery ledger requires unit id and kind");
        }
        String payloadDigest = CookingCompletionStateDigest.digest(payload == null ? Map.of() : payload);
        return new Receipt(stableId, stableKind, payloadDigest, state, System.currentTimeMillis());
    }

    private Receipt load(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        YamlSection root = YamlFiles.load(path.toFile());
        int schemaVersion = root.getInt("schema_version", -1);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported cooking delivery ledger schema: " + schemaVersion);
        }
        return new Receipt(
                required(root, "unit_id"),
                required(root, "kind"),
                required(root, "payload_digest"),
                ReceiptState.valueOf(required(root, "state")),
                longValue(root.get("updated_at_ms"), 0L)
        );
    }

    private void save(Path path, Receipt receipt) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("schema_version", SCHEMA_VERSION);
        values.put("unit_id", receipt.unitId());
        values.put("kind", receipt.kind());
        values.put("payload_digest", receipt.payloadDigest());
        values.put("state", receipt.state().name());
        values.put("updated_at_ms", receipt.updatedAtMs());
        try {
            YamlFiles.save(path.toFile(), values);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private void requireMatching(Receipt actual, Receipt expected) {
        if (!matches(actual, expected)) {
            throw new IllegalStateException("Cooking delivery unit id was reused with different kind or payload: "
                    + expected.unitId());
        }
    }

    private boolean matches(Receipt actual, Receipt expected) {
        return actual != null
                && expected != null
                && actual.unitId().equals(expected.unitId())
                && actual.kind().equals(expected.kind())
                && actual.payloadDigest().equals(expected.payloadDigest());
    }

    private String required(YamlSection root, String key) {
        String value = root.getString(key, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing cooking delivery ledger value: " + key);
        }
        return value;
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(Texts.toStringSafe(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Path pathFor(String unitId) {
        return directory.resolve(CookingCompletionStateDigest.digest(unitId) + ".yml");
    }

    private <T> CompletableFuture<T> read(Path path, String taskName, Supplier<T> action) {
        if (fileScope != null) {
            return fileScope.read(path, taskName, action);
        }
        return synchronous(path, action);
    }

    private CompletableFuture<Void> write(Path path, String taskName, Runnable action) {
        if (fileScope != null) {
            return fileScope.write(path, taskName, action);
        }
        return synchronous(path, () -> {
            action.run();
            return null;
        });
    }

    private <T> CompletableFuture<T> synchronous(Path path, Supplier<T> action) {
        Object lock = synchronousPathLocks.computeIfAbsent(path.toAbsolutePath().normalize(), _ -> new Object());
        synchronized (lock) {
            try {
                return CompletableFuture.completedFuture(action.get());
            } catch (Throwable throwable) {
                return CompletableFuture.failedFuture(throwable);
            }
        }
    }

    private enum ReceiptState {
        INTENT,
        CONFIRMED
    }

    private record Receipt(String unitId,
            String kind,
            String payloadDigest,
            ReceiptState state,
            long updatedAtMs) {
    }
}
