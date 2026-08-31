package emaki.jiuwu.craft.item.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Immutable read-only view of the typed item states stored on one stack. */
public record ItemStateSnapshot(@NotNull ItemStack item,
        @NotNull Map<ItemStateKey<?>, Object> values,
        @NotNull ItemStateMetadata metadata) {

    public ItemStateSnapshot(ItemStack item, Map<ItemStateKey<?>, Object> values) {
        this(item, values, ItemStateMetadata.empty());
    }

    public ItemStateSnapshot {
        values = values == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
        metadata = metadata == null ? ItemStateMetadata.empty() : metadata;
    }

    @Override
    public ItemStack item() {
        return item == null ? null : item.clone();
    }

    public <T> Optional<T> get(ItemStateKey<T> key) {
        if (key == null) {
            return Optional.empty();
        }
        Object value = values.get(key);
        return key.javaType().isInstance(value) ? Optional.of(key.javaType().cast(value)) : Optional.empty();
    }

    public boolean contains(ItemStateKey<?> key) {
        return key != null && values.containsKey(key);
    }

    /** {@return whether metadata was repaired while creating this snapshot} */
    public boolean repaired() {
        return metadata.repaired();
    }
}
