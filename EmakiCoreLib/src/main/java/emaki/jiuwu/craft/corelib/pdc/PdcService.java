package emaki.jiuwu.craft.corelib.pdc;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PdcService {

    private final String namespace;

    public PdcService(@NotNull String namespace) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
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
        batchMutate(itemStack, container -> container.set(partition.key(field), type, value));
    }

    @Nullable
    public <P, C> C get(@Nullable ItemStack itemStack,
            @Nullable PdcPartition partition,
            @Nullable String field,
            @Nullable PersistentDataType<P, C> type) {
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
        ItemMeta itemMeta = itemMeta(itemStack);
        if (itemMeta == null) {
            return false;
        }
        return itemMeta.getPersistentDataContainer().has(partition.key(field), type);
    }

    public void remove(@Nullable ItemStack itemStack, @Nullable PdcPartition partition, @Nullable String field) {
        batchMutate(itemStack, container -> container.remove(partition.key(field)));
    }

    public <T> boolean writeBlob(@Nullable ItemStack itemStack,
            @Nullable PdcPartition partition,
            @Nullable String field,
            @Nullable SnapshotCodec<T> codec,
            T value) {
        if (codec == null) {
            return false;
        }
        batchMutate(itemStack, container -> container.set(partition.key(field), PersistentDataType.STRING, codec.encode(value)));
        return true;
    }

    @Nullable
    public <T> T readBlob(@Nullable ItemStack itemStack,
            @Nullable PdcPartition partition,
            @Nullable String field,
            @Nullable SnapshotCodec<T> codec) {
        ItemMeta itemMeta = itemMeta(itemStack);
        if (itemMeta == null || codec == null) {
            return null;
        }
        String payload = itemMeta.getPersistentDataContainer().get(partition.key(field), PersistentDataType.STRING);
        return codec.decode(payload);
    }

    public void batchMutate(@Nullable ItemStack itemStack, @Nullable Consumer<PersistentDataContainer> consumer) {
        if (itemStack == null || consumer == null) {
            return;
        }
        ItemMeta itemMeta = itemMeta(itemStack);
        if (itemMeta == null) {
            return;
        }
        consumer.accept(itemMeta.getPersistentDataContainer());
        itemStack.setItemMeta(itemMeta);
    }

    public void mutateItemMeta(@Nullable ItemStack itemStack, @Nullable Consumer<PersistentDataContainer> consumer) {
        batchMutate(itemStack, consumer);
    }

    @Nullable
    private ItemMeta itemMeta(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        return itemStack.getItemMeta();
    }
}
