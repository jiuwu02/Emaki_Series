package emaki.jiuwu.craft.kits.store;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.corelib.api.data.SharedEntry;
import emaki.jiuwu.craft.corelib.api.data.SharedStateRepository;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import emaki.jiuwu.craft.corelib.log.LogTags;

public final class KitStore {

    private static final String CLAIM_NAMESPACE = "kits.claim";
    private static final String COUNT_NAMESPACE = "kits.count";
    private static final String KV_NAMESPACE = "kits.kv";
    private static final String AUDIT_NAMESPACE = "kits.audit";
    private static final String AUDIT_SEQUENCE = "kits.audit";
    private static final String TRANSACTION_SEQUENCE = "kits.tx";
    private static final String KV_INVITE_BINDS = "invite_binds.";
    private static final String KV_FIRST_JOIN = "first_join.";
    private static final String KV_SELECTIONS = "selections.";
    private static final String KV_CDK_DISABLED = "cdk_disabled.";
    private static final long DATABASE_TIMEOUT_SECONDS = 10L;

    private final Logger logger;
    private final ReentrantLock lock = new ReentrantLock();
    private final File file;
    private final KitState state = new KitState();
    private volatile SharedStateRepository sharedRepository;
    private volatile boolean dirty;
    private volatile boolean sealed;

    public KitStore(File dataFolder, Logger logger) {
        this.logger = logger;
        this.file = new File(dataFolder, "data/store.yml");
    }

    public void attachDatabase(SharedStateRepository repository) {
        this.sharedRepository = repository;
    }

    public void load() {
        SharedStateRepository repository = sharedRepository;
        if (repository != null) {
            loadDatabase(repository);
            return;
        }
        lock.lock();
        try {
            if (!file.exists()) {
                return;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getStringList("idempotency_keys")) {
                state.idempotencyKeys().add(key);
            }
            readCountSection(yaml.getConfigurationSection("purchase_counts"), state.purchaseCounts());
            readCountSection(yaml.getConfigurationSection("open_counts"), state.openCounts());
            readCountSection(yaml.getConfigurationSection("claim_totals"), state.claimTotals());
            readCountSection(yaml.getConfigurationSection("cdk_redeem_counts"), state.cdkRedeemCounts());
            readCountSection(yaml.getConfigurationSection("pity_counts"), state.pityCounts());
            readInviteBinds(yaml.getConfigurationSection("invite_binds"), state.inviteBinds());
            readUuidStringSection(yaml.getConfigurationSection("first_join"), state.firstJoin());
            readStringSection(yaml.getConfigurationSection("selections"), state.selections());
            for (String code : yaml.getStringList("cdk_disabled")) {
                state.cdkDisabled().add(code);
            }
            ConfigurationSection audits = yaml.getConfigurationSection("audits");
            if (audits != null) {
                for (String id : audits.getKeys(false)) {
                    ConfigurationSection node = audits.getConfigurationSection(id);
                    if (node == null) {
                        continue;
                    }
                    UUID player;
                    try {
                        player = UUID.fromString(node.getString("player", UUID.nameUUIDFromBytes(new byte[0]).toString()));
                    } catch (IllegalArgumentException exception) {
                        continue;
                    }
                    state.audits().put(id, new KitState.AuditRecord(id,
                            (long) (double) node.getDouble("at", 0D), player,
                            node.getString("kit", ""), node.getString("action", ""),
                            node.getString("detail", "")));
                }
            }
            long seq = (long) (double) yaml.getDouble("sequence", 0D);
            state.sequence(seq);
            seedCdkCounts();
        } finally {
            lock.unlock();
        }
    }

    private void loadDatabase(SharedStateRepository repository) {
        lock.lock();
        try {
            for (SharedEntry entry : listDatabase(repository, CLAIM_NAMESPACE)) {
                state.idempotencyKeys().add(entry.key());
            }
            Map<String, Long> counters = new LinkedHashMap<>();
            Set<String> authoritative = new HashSet<>();
            for (SharedEntry entry : listDatabase(repository, COUNT_NAMESPACE)) {
                if (entry.counter() != 0L) {
                    counters.put(entry.key(), entry.counter());
                    authoritative.add(entry.key());
                } else if (!authoritative.contains(entry.key())) {
                    counters.put(entry.key(), parseLong(entry.value()));
                }
            }
            for (Map.Entry<String, Long> entry : counters.entrySet()) {
                installCounter(entry.getKey(), entry.getValue());
            }
            for (SharedEntry entry : listDatabase(repository, KV_NAMESPACE)) {
                installKv(entry.key(), entry.value());
            }
            for (SharedEntry entry : listDatabase(repository, AUDIT_NAMESPACE)) {
                installAudit(entry.key(), entry.value());
            }
            seedCdkCounts();
        } finally {
            lock.unlock();
        }
    }

    private List<SharedEntry> listDatabase(SharedStateRepository repository, String namespace) {
        try {
            List<SharedEntry> entries = await(repository.list(namespace));
            return entries == null ? List.of() : entries;
        } catch (RuntimeException failure) {
            logDatabaseFailure(namespace, "list", failure);
            return List.of();
        }
    }

    private void installCounter(String key, long value) {
        Map<String, Long> counters = counterMap(key);
        if (counters == state.cdkRedeemCounts() && value < 1L) {
            return;
        }
        counters.put(key, value);
    }

    private void installKv(String key, String value) {
        String text = value == null ? "" : value;
        if (key.startsWith(KV_INVITE_BINDS)) {
            UUID invitee = parseUuid(key.substring(KV_INVITE_BINDS.length()));
            if (invitee != null) {
                state.inviteBinds().put(invitee, parseUuidList(text));
            }
            return;
        }
        if (key.startsWith(KV_FIRST_JOIN)) {
            UUID player = parseUuid(key.substring(KV_FIRST_JOIN.length()));
            if (player != null) {
                state.firstJoin().put(player, text);
            }
            return;
        }
        if (key.startsWith(KV_SELECTIONS)) {
            state.selections().put(key.substring(KV_SELECTIONS.length()), text);
            return;
        }
        if (key.startsWith(KV_CDK_DISABLED)) {
            state.cdkDisabled().add(key.substring(KV_CDK_DISABLED.length()));
        }
    }

    private void installAudit(String key, String value) {
        YamlSection node;
        try {
            node = YamlFiles.load(value == null ? "" : value);
        } catch (RuntimeException failure) {
            return;
        }
        if (node == null) {
            return;
        }
        UUID player = parseUuid(node.getString("player", ""));
        if (player == null) {
            return;
        }
        state.audits().put(key, new KitState.AuditRecord(key,
                (long) (double) node.getDouble("at", 0D), player,
                node.getString("kit", ""), node.getString("action", ""),
                node.getString("detail", "")));
    }

    private void seedCdkCounts() {
        for (String key : state.idempotencyKeys()) {
            if (!key.startsWith("cdk_player|")) {
                continue;
            }
            String rest = key.substring("cdk_player|".length());
            int bar = rest.lastIndexOf('|');
            if (bar <= 0 || bar >= rest.length() - 1) {
                continue;
            }
            state.cdkRedeemCounts().putIfAbsent(rest.substring(0, bar) + "|" + rest.substring(bar + 1), 1L);
        }
    }

    public <T> T read(Function<KitState, T> reader) {
        lock.lock();
        try {
            return reader.apply(state);
        } finally {
            lock.unlock();
        }
    }

    public boolean claimOnce(String key, Runnable onFirst) {
        SharedStateRepository repository = sharedRepository;
        if (repository != null) {
            if (sealed) {
                return false;
            }
            boolean reserved;
            try {
                reserved = Boolean.TRUE.equals(await(repository.claimOnce(CLAIM_NAMESPACE, key)));
            } catch (RuntimeException failure) {
                logDatabaseFailure(CLAIM_NAMESPACE, key, failure);
                return false;
            }
            if (!reserved) {
                return false;
            }
            lock.lock();
            try {
                state.idempotencyKeys().add(key);
                onFirst.run();
            } finally {
                lock.unlock();
            }
            return true;
        }
        lock.lock();
        try {
            if (sealed || state.idempotencyKeys().contains(key)) {
                return false;
            }
            onFirst.run();
            state.idempotencyKeys().add(key);
            dirty = true;
            return true;
        } finally {
            lock.unlock();
        }
    }

    public long incrementCounter(String key, long delta) {
        SharedStateRepository repository = sharedRepository;
        if (repository != null) {
            if (sealed) {
                lock.lock();
                try {
                    return counterValue(key);
                } finally {
                    lock.unlock();
                }
            }
            long value;
            try {
                value = await(repository.incrementCounter(COUNT_NAMESPACE, key, delta));
            } catch (RuntimeException failure) {
                logDatabaseFailure(COUNT_NAMESPACE, key, failure);
                lock.lock();
                try {
                    applyLocalCounter(key, delta);
                    return counterValue(key);
                } finally {
                    lock.unlock();
                }
            }
            lock.lock();
            try {
                installCounter(key, value);
            } finally {
                lock.unlock();
            }
            try {
                await(repository.put(COUNT_NAMESPACE, key, String.valueOf(value)));
            } catch (RuntimeException failure) {
                logDatabaseFailure(COUNT_NAMESPACE, key, failure);
            }
            return value;
        }
        lock.lock();
        try {
            if (sealed) {
                return counterValue(key);
            }
            applyLocalCounter(key, delta);
            dirty = true;
            return counterValue(key);
        } finally {
            lock.unlock();
        }
    }

    private void applyLocalCounter(String key, long delta) {
        Map<String, Long> counters = counterMap(key);
        Long previous = counters.get(key);
        if (previous == null && delta <= 0L) {
            return;
        }
        long value = (previous == null ? 0L : previous) + delta;
        if (counters == state.cdkRedeemCounts()) {
            if (value < 1L) {
                counters.remove(key);
                return;
            }
            counters.put(key, value);
            return;
        }
        counters.put(key, Math.max(0L, value));
    }

    private long counterValue(String key) {
        return counterMap(key).getOrDefault(key, 0L);
    }

    private Map<String, Long> counterMap(String key) {
        if (isCountKey(key, "claim")) {
            return state.claimTotals();
        }
        if (isCountKey(key, "purchase")) {
            return state.purchaseCounts();
        }
        if (isCountKey(key, "purchase_total")) {
            return state.purchaseCounts();
        }
        if (isCountKey(key, "open")) {
            return state.openCounts();
        }
        if (isCountKey(key, "pity")) {
            return state.pityCounts();
        }
        return state.cdkRedeemCounts();
    }

    private static boolean isCountKey(String key, String kind) {
        String prefix = kind + "|";
        if (key == null || !key.startsWith(prefix)) {
            return false;
        }
        String rest = key.substring(prefix.length());
        int bar = rest.indexOf('|');
        return bar > 0 && bar < rest.length() - 1 && bar == rest.lastIndexOf('|');
    }

    public void putSelection(String key, String value) {
        SharedStateRepository repository = sharedRepository;
        if (repository == null) {
            mutate(s -> s.selections().put(key, value));
            return;
        }
        if (sealed) {
            return;
        }
        lock.lock();
        try {
            state.selections().put(key, value);
        } finally {
            lock.unlock();
        }
        putKv(repository, KV_SELECTIONS + key, value);
    }

    public void removeSelection(String key) {
        SharedStateRepository repository = sharedRepository;
        if (repository == null) {
            mutate(s -> s.selections().remove(key));
            return;
        }
        if (sealed) {
            return;
        }
        lock.lock();
        try {
            state.selections().remove(key);
        } finally {
            lock.unlock();
        }
        removeKv(repository, KV_SELECTIONS + key);
    }

    public void addInviteBind(UUID invitee, UUID inviter) {
        SharedStateRepository repository = sharedRepository;
        if (repository == null) {
            mutate(s -> s.inviteBinds().computeIfAbsent(invitee, key -> new ArrayList<>()).add(inviter));
            return;
        }
        if (sealed) {
            return;
        }
        String encoded;
        lock.lock();
        try {
            List<UUID> inviters = state.inviteBinds().computeIfAbsent(invitee, key -> new ArrayList<>());
            inviters.add(inviter);
            encoded = joinUuids(inviters);
        } finally {
            lock.unlock();
        }
        putKv(repository, KV_INVITE_BINDS + invitee, encoded);
    }

    public void putFirstJoin(UUID player, String value) {
        SharedStateRepository repository = sharedRepository;
        if (repository == null) {
            mutate(s -> s.firstJoin().putIfAbsent(player, value));
            return;
        }
        if (sealed) {
            return;
        }
        boolean added;
        lock.lock();
        try {
            added = state.firstJoin().putIfAbsent(player, value) == null;
        } finally {
            lock.unlock();
        }
        if (added) {
            putKv(repository, KV_FIRST_JOIN + player, value);
        }
    }

    public void disableCdk(String code) {
        SharedStateRepository repository = sharedRepository;
        if (repository == null) {
            mutate(s -> s.cdkDisabled().add(code));
            return;
        }
        if (sealed) {
            return;
        }
        boolean added;
        lock.lock();
        try {
            added = state.cdkDisabled().add(code);
        } finally {
            lock.unlock();
        }
        if (added) {
            putKv(repository, KV_CDK_DISABLED + code, code);
        }
    }

    public boolean removeClaim(String key) {
        SharedStateRepository repository = sharedRepository;
        if (repository != null) {
            if (sealed) {
                return false;
            }
            boolean removed;
            lock.lock();
            try {
                removed = state.idempotencyKeys().remove(key);
            } finally {
                lock.unlock();
            }
            try {
                await(repository.remove(CLAIM_NAMESPACE, key));
            } catch (RuntimeException failure) {
                logDatabaseFailure(CLAIM_NAMESPACE, key, failure);
            }
            return removed;
        }
        boolean[] removed = {false};
        mutate(s -> removed[0] = s.idempotencyKeys().remove(key));
        return removed[0];
    }

    public boolean removeCounter(String key) {
        SharedStateRepository repository = sharedRepository;
        if (repository != null) {
            if (sealed) {
                return false;
            }
            boolean removed;
            lock.lock();
            try {
                removed = counterMap(key).remove(key) != null;
            } finally {
                lock.unlock();
            }
            try {
                await(repository.remove(COUNT_NAMESPACE, key));
            } catch (RuntimeException failure) {
                logDatabaseFailure(COUNT_NAMESPACE, key, failure);
            }
            return removed;
        }
        boolean[] removed = {false};
        mutate(s -> removed[0] = counterMap(key).remove(key) != null);
        return removed[0];
    }

    public boolean removeAudit(String id) {
        SharedStateRepository repository = sharedRepository;
        if (repository != null) {
            if (sealed) {
                return false;
            }
            boolean removed;
            lock.lock();
            try {
                removed = state.audits().remove(id) != null;
            } finally {
                lock.unlock();
            }
            try {
                await(repository.remove(AUDIT_NAMESPACE, id));
            } catch (RuntimeException failure) {
                logDatabaseFailure(AUDIT_NAMESPACE, id, failure);
            }
            return removed;
        }
        boolean[] removed = {false};
        mutate(s -> removed[0] = s.audits().remove(id) != null);
        return removed[0];
    }

    public String nextAuditId() {
        return "A" + nextSequenceValue(AUDIT_SEQUENCE);
    }

    public String nextTransactionId() {
        return "T" + nextSequenceValue(TRANSACTION_SEQUENCE);
    }

    private long nextSequenceValue(String name) {
        SharedStateRepository repository = sharedRepository;
        if (repository != null) {
            try {
                return await(repository.nextSequence(name));
            } catch (RuntimeException failure) {
                logDatabaseFailure(name, "sequence", failure);
            }
        }
        lock.lock();
        try {
            return state.nextSequence();
        } finally {
            lock.unlock();
        }
    }

    public void mutate(Consumer<KitState> mutation) {
        lock.lock();
        try {
            if (sealed) {
                return;
            }
            mutation.accept(state);
            dirty = true;
        } finally {
            lock.unlock();
        }
    }

    public void recordAudit(KitState.AuditRecord record) {
        SharedStateRepository repository = sharedRepository;
        if (repository == null) {
            mutate(s -> s.audits().put(record.id(), record));
            return;
        }
        if (sealed) {
            return;
        }
        lock.lock();
        try {
            state.audits().put(record.id(), record);
        } finally {
            lock.unlock();
        }
        try {
            await(repository.put(AUDIT_NAMESPACE, record.id(), YamlFiles.dump(auditValues(record))));
        } catch (RuntimeException failure) {
            logDatabaseFailure(AUDIT_NAMESPACE, record.id(), failure);
        }
    }

    private static Map<String, Object> auditValues(KitState.AuditRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("at", record.atMillis());
        row.put("player", record.player().toString());
        row.put("kit", record.kitId());
        row.put("action", record.action());
        row.put("detail", record.detail());
        return row;
    }

    private void putKv(SharedStateRepository repository, String rowKey, String value) {
        try {
            await(repository.put(KV_NAMESPACE, rowKey, value));
        } catch (RuntimeException failure) {
            logDatabaseFailure(KV_NAMESPACE, rowKey, failure);
        }
    }

    private void removeKv(SharedStateRepository repository, String rowKey) {
        try {
            await(repository.remove(KV_NAMESPACE, rowKey));
        } catch (RuntimeException failure) {
            logDatabaseFailure(KV_NAMESPACE, rowKey, failure);
        }
    }

    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(DATABASE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("共享数据库操作被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            throw cause instanceof RuntimeException runtime ? runtime : new IllegalStateException(cause);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("共享数据库操作超时", exception);
        }
    }

    private void logDatabaseFailure(String namespace, String key, Throwable throwable) {
        LogTags.send(logger, null, Level.SEVERE, "console.database_op_failed",
                Map.of("namespace", namespace, "key", key, "error", throwable), throwable);
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String joinUuids(List<UUID> values) {
        StringBuilder builder = new StringBuilder();
        for (UUID value : values) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static List<UUID> parseUuidList(String value) {
        List<UUID> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String item : value.split(",")) {
            UUID parsed = parseUuid(item.trim());
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    public synchronized void flush() {
        if (sharedRepository != null) {
            return;
        }
        if (!dirty) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> keys;
        List<String> disabled;
        List<Map<String, Object>> audits = new ArrayList<>();
        Map<String, Long> purchases;
        Map<String, Long> opens;
        Map<String, Long> totals;
        Map<String, Long> cdkCounts;
        Map<String, Long> pityCounts;
        Map<UUID, List<UUID>> binds;
        Map<UUID, String> joins;
        Map<String, String> selections;
        long seq;
        lock.lock();
        try {
            keys = new ArrayList<>(state.idempotencyKeys());
            disabled = new ArrayList<>(state.cdkDisabled());
            purchases = new LinkedHashMap<>(state.purchaseCounts());
            opens = new LinkedHashMap<>(state.openCounts());
            totals = new LinkedHashMap<>(state.claimTotals());
            cdkCounts = new LinkedHashMap<>(state.cdkRedeemCounts());
            pityCounts = new LinkedHashMap<>(state.pityCounts());
            binds = new LinkedHashMap<>(state.inviteBinds());
            joins = new LinkedHashMap<>(state.firstJoin());
            selections = new LinkedHashMap<>(state.selections());
            for (KitState.AuditRecord record : state.audits().values()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("at", record.atMillis());
                row.put("player", record.player().toString());
                row.put("kit", record.kitId());
                row.put("action", record.action());
                row.put("detail", record.detail());
                Map<String, Object> wrapper = new LinkedHashMap<>();
                wrapper.put(record.id(), row);
                audits.add(wrapper);
            }
            seq = state.sequence();
        } finally {
            lock.unlock();
        }
        yaml.set("idempotency_keys", keys);
        yaml.set("cdk_disabled", disabled);
        yaml.set("purchase_counts", new LinkedHashMap<String, Object>(purchases));
        yaml.set("open_counts", new LinkedHashMap<String, Object>(opens));
        yaml.set("claim_totals", new LinkedHashMap<String, Object>(totals));
        yaml.set("cdk_redeem_counts", new LinkedHashMap<String, Object>(cdkCounts));
        yaml.set("pity_counts", new LinkedHashMap<String, Object>(pityCounts));
        yaml.set("invite_binds", toStringKeyListMap(binds));
        yaml.set("first_join", toStringKeyMap(joins));
        yaml.set("selections", new LinkedHashMap<String, Object>(selections));
        for (Map<String, Object> wrapper : audits) {
            for (Map.Entry<String, Object> entry : wrapper.entrySet()) {
                yaml.set("audits." + entry.getKey(), entry.getValue());
            }
        }
        yaml.set("sequence", seq);
        try {
            Path target = file.toPath();
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling("store.yml.tmp");
            Files.writeString(tmp, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException | RuntimeException exception) {
            LogTags.send(logger, null, Level.SEVERE, "console.persistence_write_failed", Map.of("file", file, "error", exception), exception);
        }
    }

    public void seal() {
        lock.lock();
        try {
            sealed = true;
        } finally {
            lock.unlock();
        }
        flush();
    }

    public boolean dirty() {
        return dirty;
    }

    private static Map<String, Object> toStringKeyMap(Map<UUID, String> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> entry : source.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue());
        }
        return result;
    }

    private static Map<String, List<String>> toStringKeyListMap(Map<UUID, List<UUID>> source) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<UUID>> entry : source.entrySet()) {
            List<String> inviters = new ArrayList<>();
            for (UUID inviter : entry.getValue()) {
                inviters.add(inviter.toString());
            }
            result.put(entry.getKey().toString(), inviters);
        }
        return result;
    }

    private static void readCountSection(ConfigurationSection section, Map<String, Long> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            target.put(key, section.getLong(key, 0L));
        }
    }

    private void readInviteBinds(ConfigurationSection section, Map<UUID, List<UUID>> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID invitee;
            try {
                invitee = UUID.fromString(key);
            } catch (IllegalArgumentException invalidUuid) {
                LogTags.send(logger, null, Level.FINE, "console.skip_invalid_uuid_key", Map.of("key", key));
                continue;
            }
            List<UUID> inviters = new ArrayList<>();
            Object raw = section.get(key);
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    try {
                        inviters.add(UUID.fromString(String.valueOf(item)));
                    } catch (IllegalArgumentException invalidUuid) {
                        LogTags.send(logger, null, Level.FINE, "console.skip_invalid_uuid_key", Map.of("key", String.valueOf(item)));
                    }
                }
            } else if (raw != null && !String.valueOf(raw).isBlank()) {
                try {
                    inviters.add(UUID.fromString(String.valueOf(raw)));
                } catch (IllegalArgumentException invalidUuid) {
                    LogTags.send(logger, null, Level.FINE, "console.skip_invalid_uuid_key", Map.of("key", String.valueOf(raw)));
                }
            }
            target.put(invitee, inviters);
        }
    }

    private void readUuidStringSection(ConfigurationSection section, Map<UUID, String> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                target.put(UUID.fromString(key), section.getString(key, ""));
            } catch (IllegalArgumentException invalidUuid) {
                LogTags.send(logger, null, Level.FINE, "console.skip_invalid_uuid_key", Map.of("key", key));
            }
        }
    }

    private static void readStringSection(ConfigurationSection section, Map<String, String> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            target.put(key, section.getString(key, ""));
        }
    }
}
