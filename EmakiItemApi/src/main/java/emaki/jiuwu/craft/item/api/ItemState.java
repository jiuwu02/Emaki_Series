package emaki.jiuwu.craft.item.api;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Typed read/write API for custom persistent item state. */
public interface ItemState {
    @NotNull ItemStateSnapshot snapshot(@Nullable ItemStack item);
    /** Repairs metadata and returns a read-back snapshot. */
    @NotNull ItemStateSnapshot repair(@Nullable ItemStack item);
    <T> @NotNull Optional<T> get(@Nullable ItemStack item, @NotNull ItemStateKey<T> key);
    <T> @NotNull ItemStateMutation<T> set(@Nullable ItemStack item, @NotNull ItemStateKey<T> key, @Nullable T value);
    <T> @NotNull ItemStateMutation<T> add(@Nullable ItemStack item, @NotNull ItemStateKey<T> key, @NotNull Number amount);
    <T> @NotNull ItemStateMutation<T> remove(@Nullable ItemStack item, @NotNull ItemStateKey<T> key);
}
