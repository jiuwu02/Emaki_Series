package emaki.jiuwu.craft.kits.store;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public final class KitStore {

    private final Logger logger;
    private final ReentrantLock lock = new ReentrantLock();
    private final File file;
    private final KitState state = new KitState();
    private volatile boolean dirty;
    private volatile boolean sealed;

    public KitStore(File dataFolder, Logger logger) {
        this.logger = logger;
        this.file = new File(dataFolder, "data/store.yml");
    }

    public void load() {
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
            readUuidStringSection(yaml.getConfigurationSection("invite_binds"), state.inviteBinds());
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
                            node.getLong("at", 0L), player,
                            node.getString("kit", ""), node.getString("action", ""),
                            node.getString("detail", "")));
                }
            }
            long seq = yaml.getLong("sequence", 0L);
            state.sequence(seq);
        } finally {
            lock.unlock();
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
        mutate(s -> s.audits().put(record.id(), record));
    }

    public synchronized void flush() {
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
        Map<UUID, String> binds;
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
        yaml.set("invite_binds", toStringKeyMap(binds));
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
            logger.log(Level.SEVERE,
                    "[EmakiKits] 持久化写入失败: operation=flush, file=" + file + ", cause=" + exception, exception);
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

    private static void readCountSection(ConfigurationSection section, Map<String, Long> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            target.put(key, section.getLong(key, 0L));
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
                logger.fine("[EmakiKits] 跳过非法 UUID 存档键: " + key);
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
