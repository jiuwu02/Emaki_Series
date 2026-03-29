package emaki.jiuwu.craft.corelib.pdc;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

public final class PdcService {

    private final String namespace;

    public PdcService(String namespace) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
    }

    public String namespace() {
        return namespace;
    }

    public PdcPartition partition(String path) {
        return new PdcPartition(namespace, path);
    }

    public NamespacedKey key(String path) {
        return partition(path).key();
    }

    public <P, C> void set(PersistentDataHolder holder,
                           PdcPartition partition,
                           String field,
                           PersistentDataType<P, C> type,
                           C value) {
        if (holder == null || partition == null || type == null || field == null) {
            return;
        }
        PersistentDataContainer container = holder.getPersistentDataContainer();
        container.set(partition.key(field), type, value);
    }

    public <P, C> C get(PersistentDataHolder holder,
                        PdcPartition partition,
                        String field,
                        PersistentDataType<P, C> type) {
        if (holder == null || partition == null || type == null || field == null) {
            return null;
        }
        PersistentDataContainer container = holder.getPersistentDataContainer();
        NamespacedKey key = partition.key(field);
        return container.has(key, type) ? container.get(key, type) : null;
    }

    public <P, C> boolean has(PersistentDataHolder holder,
                              PdcPartition partition,
                              String field,
                              PersistentDataType<P, C> type) {
        if (holder == null || partition == null || type == null || field == null) {
            return false;
        }
        return holder.getPersistentDataContainer().has(partition.key(field), type);
    }

    public void remove(PersistentDataHolder holder, PdcPartition partition, String field) {
        if (holder == null || partition == null || field == null) {
            return;
        }
        holder.getPersistentDataContainer().remove(partition.key(field));
    }

    public <T> boolean writeBlob(PersistentDataHolder holder,
                                 PdcPartition partition,
                                 String field,
                                 SnapshotCodec<T> codec,
                                 T value) {
        if (holder == null || partition == null || codec == null || field == null) {
            return false;
        }
        holder.getPersistentDataContainer().set(partition.key(field), PersistentDataType.STRING, codec.encode(value));
        return true;
    }

    public <T> T readBlob(PersistentDataHolder holder,
                          PdcPartition partition,
                          String field,
                          SnapshotCodec<T> codec) {
        if (holder == null || partition == null || codec == null || field == null) {
            return null;
        }
        PersistentDataContainer container = holder.getPersistentDataContainer();
        String payload = container.get(partition.key(field), PersistentDataType.STRING);
        return codec.decode(payload);
    }

    public <P, C> void set(ItemStack itemStack,
                           PdcPartition partition,
                           String field,
                           PersistentDataType<P, C> type,
                           C value) {
        mutateItemMeta(itemStack, container -> container.set(partition.key(field), type, value));
    }

    public <P, C> C get(ItemStack itemStack,
                        PdcPartition partition,
                        String field,
                        PersistentDataType<P, C> type) {
        ItemMeta itemMeta = itemMeta(itemStack);
        if (itemMeta == null) {
            return null;
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        NamespacedKey key = partition.key(field);
        return container.has(key, type) ? container.get(key, type) : null;
    }

    public <P, C> boolean has(ItemStack itemStack,
                              PdcPartition partition,
                              String field,
                              PersistentDataType<P, C> type) {
        ItemMeta itemMeta = itemMeta(itemStack);
        if (itemMeta == null) {
            return false;
        }
        return itemMeta.getPersistentDataContainer().has(partition.key(field), type);
    }

    public void remove(ItemStack itemStack, PdcPartition partition, String field) {
        mutateItemMeta(itemStack, container -> container.remove(partition.key(field)));
    }

    public <T> boolean writeBlob(ItemStack itemStack,
                                 PdcPartition partition,
                                 String field,
                                 SnapshotCodec<T> codec,
                                 T value) {
        if (codec == null) {
            return false;
        }
        mutateItemMeta(itemStack, container -> container.set(partition.key(field), PersistentDataType.STRING, codec.encode(value)));
        return true;
    }

    public <T> T readBlob(ItemStack itemStack,
                          PdcPartition partition,
                          String field,
                          SnapshotCodec<T> codec) {
        ItemMeta itemMeta = itemMeta(itemStack);
        if (itemMeta == null || codec == null) {
            return null;
        }
        String payload = itemMeta.getPersistentDataContainer().get(partition.key(field), PersistentDataType.STRING);
        return codec.decode(payload);
    }

    public void mutateItemMeta(ItemStack itemStack, Consumer<PersistentDataContainer> consumer) {
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

    private ItemMeta itemMeta(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        return itemStack.getItemMeta();
    }
}
