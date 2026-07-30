package emaki.jiuwu.craft.forge.api.model;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The item layout offered to a forging attempt, keyed by slot index.
 *
 * <p>This mirrors EmakiForge's internal GUI item container without exposing it, so third parties can
 * validate or match a recipe without opening a GUI.
 *
 * <p>The target and every map value are cloned on construction and again on access, so asynchronous
 * forging always observes a detached escrow snapshot rather than caller-owned mutable stacks.
 *
 * @param targetItem        the item in the target slot, or {@code null} when the recipe needs none
 * @param blueprints        slot index to blueprint item
 * @param requiredMaterials slot index to required material item
 * @param optionalMaterials slot index to optional material item
 */
public record ForgeInputs(@Nullable ItemStack targetItem,
                          @NotNull Map<Integer, ItemStack> blueprints,
                          @NotNull Map<Integer, ItemStack> requiredMaterials,
                          @NotNull Map<Integer, ItemStack> optionalMaterials) {

    /**
     * Normalises every map so no accessor can return {@code null}.
     *
     * @param targetItem        the target slot item
     * @param blueprints        blueprint items by slot
     * @param requiredMaterials required material items by slot
     * @param optionalMaterials optional material items by slot
     */
    public ForgeInputs {
        targetItem = cloneItem(targetItem);
        blueprints = cloneItems(blueprints);
        requiredMaterials = cloneItems(requiredMaterials);
        optionalMaterials = cloneItems(optionalMaterials);
    }

    /** {@return a clone of the target item, or {@code null}} */
    @Override
    public @Nullable ItemStack targetItem() {
        return cloneItem(targetItem);
    }

    /** {@return a detached copy of blueprint inputs by slot} */
    @Override
    public @NotNull Map<Integer, ItemStack> blueprints() {
        return cloneItems(blueprints);
    }

    /** {@return a detached copy of required material inputs by slot} */
    @Override
    public @NotNull Map<Integer, ItemStack> requiredMaterials() {
        return cloneItems(requiredMaterials);
    }

    /** {@return a detached copy of optional material inputs by slot} */
    @Override
    public @NotNull Map<Integer, ItemStack> optionalMaterials() {
        return cloneItems(optionalMaterials);
    }

    /**
     * Creates inputs consisting only of required materials.
     *
     * @param requiredMaterials required material items by slot
     * @return the inputs
     */
    public static @NotNull ForgeInputs ofRequired(@Nullable Map<Integer, ItemStack> requiredMaterials) {
        return new ForgeInputs(null, Map.of(), requiredMaterials == null ? Map.of() : requiredMaterials, Map.of());
    }

    /** {@return inputs with no items at all} */
    public static @NotNull ForgeInputs empty() {
        return new ForgeInputs(null, Map.of(), Map.of(), Map.of());
    }

    /** {@return whether no item was supplied in any slot} */
    public boolean isEmpty() {
        return targetItem == null && blueprints.isEmpty() && requiredMaterials.isEmpty() && optionalMaterials.isEmpty();
    }

    private static @Nullable ItemStack cloneItem(@Nullable ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }

    private static @NotNull Map<Integer, ItemStack> cloneItems(@Nullable Map<Integer, ItemStack> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<Integer, ItemStack> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, ItemStack> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey(), entry.getValue().clone());
            }
        }
        return copy.isEmpty() ? Map.of() : Map.copyOf(copy);
    }
}
