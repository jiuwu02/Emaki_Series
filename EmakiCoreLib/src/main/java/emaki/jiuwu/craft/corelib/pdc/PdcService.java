package emaki.jiuwu.craft.corelib.pdc;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.pdc.PdcKeyMigration;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;

public final class PdcService {

    private final String namespace;
    private final String debugModule;
    private final DebugLogger debugLogger;

    public PdcService(@NotNull String namespace) {
        this(namespace, "pdc", null);
    }

    public PdcService(@NotNull String namespace, @Nullable DebugLogger debugLogger) {
        this(namespace, "pdc", debugLogger);
    }

    public PdcService(@NotNull String namespace, @NotNull String debugModule, @Nullable DebugLogger debugLogger) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.debugModule = Objects.requireNonNull(debugModule, "debugModule");
        this.debugLogger = debugLogger;
    }

    @NotNull
    public String namespace() {
        return namespace;
    }

    @NotNull
    public PdcPartition partition(@NotNull String path) {
        return new PdcPartition(namespace, path);
    }

    @NotNull
    public NamespacedKey key(@NotNull String path) {
        return partition(path).key();
    }

    public <P, C> void set(@Nullable PersistentDataHolder holder,
            @Nullable PdcPartition partition,
            @Nullable String field,
            @Nullable PersistentDataType<P, C> type,
            C value) {
        if (holder == null || partition == null || type == null || field == null) {
            return;
        }
        PersistentDataContainer container = holder.getPersistentDataContainer();
        container.set(partition.key(field), type, value);
    }

    @Nullable
    public <P, C> C get(@Nullable PersistentDataHolder holder,
            @Nullable PdcPartition partition,
            @Nullable String field,
            @Nullable PersistentDataType<P, C> type) {
        if (holder == null || partition == null || type == null || field == null) {
            return null;
        }
        PersistentDataContainer container = holder.getPersistentDataContainer();
        NamespacedKey key = partition.key(field);
        return container.has(key, type) ? container.get(key, type) : null;
    }

    public <P, C> boolean has(@Nullable PersistentDataHolder holder,
            @Nullable PdcPartition partition,
            @Nullable String field,
            @Nullable PersistentDataType<P, C> type) {
        if (holder == null || partition == null || type == null || field == null) {
            return false;
        }
        return holder.getPersistentDataContainer().has(partition.key(field), type);
    }

    public void remove(@Nullable PersistentDataHolder holder, @Nullable PdcPartition partition, @Nullable String field) {
        if (holder == null || partition == null || field == null) {
            return;
        }
        holder.getPersistentDataContainer().remove(partition.key(field));
    }

    /**
     * 删除实体/玩家上该字段的新键与历史带点键。
     *
     * <p>实体容器是活容器，改动直接生效，无需回写。
     *
     * @param legacyPartitionPath 历史分区路径（含点）
     */
    public void removeMigrating(@Nullable PersistentDataHolder holder,
            @Nullable PdcPartition partition,
            @Nullable String legacyPartitionPath,
            @Nullable String field) {
        if (holder == null || partition == null || field == null) {
            return;
        }
        PersistentDataContainer container = holder.getPersistentDataContainer();
        container.remove(partition.key(field));
        NamespacedKey legacyKey = PdcKeyMigration.legacyKey(partition.namespace(), legacyPartitionPath, field);
        if (legacyKey != null) {
            container.remove(legacyKey);
        }
    }

    public <T> boolean writeBlob(@Nullable PersistentDataHolder holder,
            @Nullable PdcPartition partition,
            @Nullable String field,
            @Nullable SnapshotCodec<T> codec,
            T value) {
        if (holder == null || partition == null || codec == null || field == null) {
            return false;
        }
        holder.getPersistentDataContainer().set(partition.key(field), PersistentDataType.STRING, codec.encode(value));
        return true;
    }

    @Nullable
    public <T> T readBlob(@Nullable PersistentDataHolder holder,
            @Nullable PdcPartition partition,
            @Nullable String field,
            @Nullable SnapshotCodec<T> codec) {
        if (holder == null || partition == null || codec == null || field == null) {
            return null;
        }
        PersistentDataContainer container = holder.getPersistentDataContainer();
        String payload = container.get(partition.key(field), PersistentDataType.STRING);
        return codec.decode(payload);
    }

    public <P, C> void set(@Nullable ItemStack itemStack,
            @Nullable PdcPartition partition,
            @Nullable String field,
            @Nullable PersistentDataType<P, C> type,
            C value) {
        if (partition == null || type == null || field == null) {
            logSkipped(itemStack, "set", "", value, "invalid_arguments");
            return;
        }
        NamespacedKey key = partition.key(field);
        mutateItemStack(itemStack, "set", key, value, container -> container.set(key, type, value));
    }

    @Nullable
    public <P, C> C get(@Nullable ItemStack itemStack,
            @Nullable PdcPartition partition,
            @Nullable String field,
            @Nullable PersistentDataType<P, C> type) {
        if (partition == null || type == null || field == null) {
            return null;
        }
        ItemMeta itemMeta = itemMeta(itemStack);
        if (itemMeta == null) {
            return null;
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        NamespacedKey key = partition.key(field);
        return container.has(key, type) ? container.get(key, type) : null;
    }

    public <P, C> boolean has(@Nullable ItemStack itemStack,
            @Nullable PdcPartition partition,
            @Nullable String field,
            @Nullable PersistentDataType<P, C> type) {
        if (partition == null || type == null || field == null) {
            return false;
        }
        ItemMeta itemMeta = itemMeta(itemStack);
        if (itemMeta == null) {
            return false;
        }
        return itemMeta.getPersistentDataContainer().has(partition.key(field), type);
    }

    public void remove(@Nullable ItemStack itemStack, @Nullable PdcPartition partition, @Nullable String field) {
        if (partition == null || field == null) {
            logSkipped(itemStack, "remove", "", "", "invalid_arguments");
            return;
        }
        NamespacedKey key = partition.key(field);
        mutateItemStack(itemStack, "remove", key, "", container -> container.remove(key));
    }

    /**
     * 同时删除新键与历史带点键。
     *
     * <p>清理路径必须用这个方法：只删新键会让尚未迁移的老键变成孤儿，
     * 永久留在物品 NBT 里且无法再被读到。
     *
     * @param legacyPartitionPath 历史分区路径（含点）
     */
    public void removeMigrating(@Nullable ItemStack itemStack,
            @Nullable PdcPartition partition,
            @Nullable String legacyPartitionPath,
            @Nullable String field) {
        if (partition == null || field == null) {
            logSkipped(itemStack, "remove_migrating", "", "", "invalid_arguments");
            return;
        }
        NamespacedKey key = partition.key(field);
        NamespacedKey legacyKey = PdcKeyMigration.legacyKey(partition.namespace(), legacyPartitionPath, field);
        mutateItemStack(itemStack, "remove_migrating", key, "", container -> {
            container.remove(key);
            if (legacyKey != null) {
                container.remove(legacyKey);
            }
        });
    }

    public <T> boolean writeBlob(@Nullable ItemStack itemStack,
            @Nullable PdcPartition partition,
            @Nullable String field,
            @Nullable SnapshotCodec<T> codec,
            T value) {
        if (partition == null || codec == null || field == null) {
            logSkipped(itemStack, "write_blob", "", value, "invalid_arguments");
            return false;
        }
        NamespacedKey key = partition.key(field);
        mutateItemStack(itemStack, "write_blob", key, value,
                container -> container.set(key, PersistentDataType.STRING, codec.encode(value)));
        return true;
    }

    @Nullable
    public <T> T readBlob(@Nullable ItemStack itemStack,
            @Nullable PdcPartition partition,
            @Nullable String field,
            @Nullable SnapshotCodec<T> codec) {
        if (partition == null || codec == null || field == null) {
            return null;
        }
        ItemMeta itemMeta = itemMeta(itemStack);
        if (itemMeta == null) {
            return null;
        }
        String payload = itemMeta.getPersistentDataContainer().get(partition.key(field), PersistentDataType.STRING);
        return codec.decode(payload);
    }

    /**
     * 读取物品 PDC，新键缺失时回落到历史带点键并就地迁移。
     *
     * <p>{@link PdcPartition#SEPARATOR} 从 {@code '.'} 改为 {@code '_'} 后，已落盘的物品
     * 仍是老键。这个方法让读取路径自愈：命中老键就搬到新键、删掉老键，然后返回值。
     *
     * <p>历史分区路径必须由调用方显式提供——新键的 {@code '_'} 无法反推哪些原本是连接符，
     * 详见 {@link PdcKeyMigration#legacyKey}。
     *
     * <p>O(1)，可放在战斗热路径。迁移发生时会写回 {@code ItemMeta}。
     *
     * @param itemStack           目标物品
     * @param partition           新分区
     * @param legacyPartitionPath 历史分区路径（含点），如 {@code "item.attributes"}
     * @param field               字段名
     * @param type                值类型
     * @return 读到的值，新老键都没有时返回 {@code null}
     */
    @Nullable
    public <P, C> C getMigrating(@Nullable ItemStack itemStack,
            @Nullable PdcPartition partition,
            @Nullable String legacyPartitionPath,
            @Nullable String field,
            @Nullable PersistentDataType<P, C> type) {
        if (partition == null || type == null || field == null) {
            return null;
        }
        ItemMeta itemMeta = itemMeta(itemStack);
        if (itemMeta == null) {
            return null;
        }
        NamespacedKey newKey = partition.key(field);
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        if (container.has(newKey, type)) {
            return container.get(newKey, type);
        }
        NamespacedKey legacyKey = PdcKeyMigration.legacyKey(partition.namespace(), legacyPartitionPath, field);
        if (legacyKey == null || !container.has(legacyKey, type)) {
            return null;
        }
        C value = container.get(legacyKey, type);
        if (value == null) {
            return null;
        }
        // 走 mutateItemStack 以复用它的 setItemMeta 回写与调试日志。
        mutateItemStack(itemStack, "migrate_key", newKey, value, target -> {
            target.set(newKey, type, value);
            target.remove(legacyKey);
        });
        return value;
    }

    /** {@link #getMigrating} 的 blob 版本。 */
    @Nullable
    public <T> T readBlobMigrating(@Nullable ItemStack itemStack,
            @Nullable PdcPartition partition,
            @Nullable String legacyPartitionPath,
            @Nullable String field,
            @Nullable SnapshotCodec<T> codec) {
        if (codec == null) {
            return null;
        }
        String payload = getMigrating(itemStack, partition, legacyPartitionPath, field, PersistentDataType.STRING);
        return codec.decode(payload);
    }

    /**
     * 读取实体/玩家 PDC，新键缺失时回落到历史带点键并就地迁移。
     *
     * <p>实体的 {@code PersistentDataContainer} 是活容器，改动直接生效，无需回写。
     */
    @Nullable
    public <P, C> C getMigrating(@Nullable PersistentDataHolder holder,
            @Nullable PdcPartition partition,
            @Nullable String legacyPartitionPath,
            @Nullable String field,
            @Nullable PersistentDataType<P, C> type) {
        if (holder == null || partition == null || type == null || field == null) {
            return null;
        }
        return PdcKeyMigration.readWithMigration(
                holder.getPersistentDataContainer(),
                partition.key(field),
                PdcKeyMigration.legacyKey(partition.namespace(), legacyPartitionPath, field),
                type
        );
    }

    /**
     * 清掉物品上所有历史带点键（不搬值），并回写 {@code ItemMeta}。
     *
     * <p>写入路径收尾时调用：新键刚写好，老键必须删除，否则会成为读不到的孤儿残留。
     * 不要用 {@code migrateAll} 代替——它会把过期老键值覆盖到刚写好的新键上。
     */
    public void purgeLegacyKeys(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return;
        }
        ItemMeta itemMeta = itemMeta(itemStack);
        if (itemMeta == null) {
            return;
        }
        if (PdcKeyMigration.purgeLegacyKeys(itemMeta.getPersistentDataContainer()) > 0) {
            itemStack.setItemMeta(itemMeta);
        }
    }

    public void batchMutate(@Nullable ItemStack itemStack, @Nullable Consumer<PersistentDataContainer> consumer) {
        mutateItemStack(itemStack, "batch_mutate", null, "", consumer);
    }

    public void mutateItemMeta(@Nullable ItemStack itemStack, @Nullable Consumer<PersistentDataContainer> consumer) {
        batchMutate(itemStack, consumer);
    }

    private void mutateItemStack(@Nullable ItemStack itemStack,
            @NotNull String operation,
            @Nullable NamespacedKey targetKey,
            @Nullable Object requestedValue,
            @Nullable Consumer<PersistentDataContainer> consumer) {
        if (itemStack == null) {
            logSkipped(null, operation, targetKey, requestedValue, "item_missing");
            return;
        }
        if (consumer == null) {
            logSkipped(itemStack, operation, targetKey, requestedValue, "consumer_missing");
            return;
        }
        boolean debugEnabled = isDebugEnabled();
        try {
            ItemMeta itemMeta = itemMeta(itemStack);
            if (itemMeta == null) {
                logSkipped(itemStack, operation, targetKey, requestedValue, "item_meta_missing");
                return;
            }
            PersistentDataContainer container = itemMeta.getPersistentDataContainer();
            Map<String, String> before = debugEnabled ? snapshot(container) : Map.of();
            consumer.accept(container);
            boolean committed = itemStack.setItemMeta(itemMeta);
            if (debugEnabled) {
                Map<String, String> after = snapshot(itemMeta.getPersistentDataContainer());
                logMutation(itemStack, operation, targetKey, requestedValue, before, after, committed, "");
            }
        } catch (RuntimeException | Error failure) {
            if (debugEnabled) {
                logMutation(itemStack, operation, targetKey, requestedValue, Map.of(), Map.of(), false,
                        failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
            }
            throw failure;
        }
    }

    private boolean isDebugEnabled() {
        return debugLogger != null && debugLogger.shouldLog(debugModule, (UUID) null);
    }

    private void logSkipped(@Nullable ItemStack itemStack,
            @NotNull String operation,
            @Nullable Object targetKey,
            @Nullable Object requestedValue,
            @NotNull String reason) {
        if (!isDebugEnabled()) {
            return;
        }
        logMutation(itemStack, operation, targetKey, requestedValue, Map.of(), Map.of(), false, reason);
    }

    private void logMutation(@Nullable ItemStack itemStack,
            @NotNull String operation,
            @Nullable Object targetKey,
            @Nullable Object requestedValue,
            Map<String, String> before,
            Map<String, String> after,
            boolean committed,
            @NotNull String reason) {
        if (debugLogger == null) {
            return;
        }
        Delta delta = delta(before, after);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("operation", operation);
        replacements.put("item", itemStack == null ? "null" : itemStack.getType());
        replacements.put("amount", itemStack == null ? 0 : itemStack.getAmount());
        replacements.put("key", targetKey == null ? "" : targetKey);
        replacements.put("value", safeValue(requestedValue));
        replacements.put("before", before);
        replacements.put("after", after);
        replacements.put("added", delta.added());
        replacements.put("removed", delta.removed());
        replacements.put("changed", delta.changed());
        replacements.put("committed", committed);
        replacements.put("reason", reason);
        debugLogger.log(debugModule, (UUID) null, "pdc.mutation", replacements);
    }

    private Map<String, String> snapshot(PersistentDataContainer container) {
        if (container == null || container.getKeys().isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        container.getKeys().stream()
                .sorted(Comparator.comparing(NamespacedKey::toString))
                .forEach(key -> values.put(key.toString(), readValue(container, key)));
        return Map.copyOf(values);
    }

    private String readValue(PersistentDataContainer container, NamespacedKey key) {
        if (container.has(key, PersistentDataType.STRING)) {
            return safeValue(container.get(key, PersistentDataType.STRING));
        }
        if (container.has(key, PersistentDataType.BYTE)) {
            return safeValue(container.get(key, PersistentDataType.BYTE));
        }
        if (container.has(key, PersistentDataType.SHORT)) {
            return safeValue(container.get(key, PersistentDataType.SHORT));
        }
        if (container.has(key, PersistentDataType.INTEGER)) {
            return safeValue(container.get(key, PersistentDataType.INTEGER));
        }
        if (container.has(key, PersistentDataType.LONG)) {
            return safeValue(container.get(key, PersistentDataType.LONG));
        }
        if (container.has(key, PersistentDataType.FLOAT)) {
            return safeValue(container.get(key, PersistentDataType.FLOAT));
        }
        if (container.has(key, PersistentDataType.DOUBLE)) {
            return safeValue(container.get(key, PersistentDataType.DOUBLE));
        }
        if (container.has(key, PersistentDataType.BYTE_ARRAY)) {
            return safeValue(container.get(key, PersistentDataType.BYTE_ARRAY));
        }
        if (container.has(key, PersistentDataType.INTEGER_ARRAY)) {
            return safeValue(container.get(key, PersistentDataType.INTEGER_ARRAY));
        }
        if (container.has(key, PersistentDataType.LONG_ARRAY)) {
            return safeValue(container.get(key, PersistentDataType.LONG_ARRAY));
        }
        return "<unsupported:" + key + ">";
    }

    private String safeValue(@Nullable Object value) {
        if (value instanceof byte[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof int[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof long[] values) {
            return Arrays.toString(values);
        }
        return String.valueOf(value);
    }

    private Delta delta(Map<String, String> before, Map<String, String> after) {
        Map<String, String> added = new LinkedHashMap<>();
        Map<String, String> removed = new LinkedHashMap<>();
        Map<String, String> changed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : after.entrySet()) {
            String previous = before.get(entry.getKey());
            if (previous == null) {
                added.put(entry.getKey(), entry.getValue());
            } else if (!Objects.equals(previous, entry.getValue())) {
                changed.put(entry.getKey(), previous + " -> " + entry.getValue());
            }
        }
        for (Map.Entry<String, String> entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey())) {
                removed.put(entry.getKey(), entry.getValue());
            }
        }
        return new Delta(Map.copyOf(added), Map.copyOf(removed), Map.copyOf(changed));
    }

    @Nullable
    private ItemMeta itemMeta(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        return itemStack.getItemMeta();
    }

    private record Delta(Map<String, String> added, Map<String, String> removed, Map<String, String> changed) {
    }
}
