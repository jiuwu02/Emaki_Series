package emaki.jiuwu.craft.forge.api.model;

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
 * <p>All maps are copied defensively, but the {@link ItemStack} values are not cloned: do not mutate
 * a stack after handing it to this record.
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
        blueprints = blueprints == null ? Map.of() : Map.copyOf(blueprints);
        requiredMaterials = requiredMaterials == null ? Map.of() : Map.copyOf(requiredMaterials);
        optionalMaterials = optionalMaterials == null ? Map.of() : Map.copyOf(optionalMaterials);
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
}
