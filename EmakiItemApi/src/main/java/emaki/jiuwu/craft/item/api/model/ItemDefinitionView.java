package emaki.jiuwu.craft.item.api.model;

import java.util.List;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only view of an EmakiItem definition.
 *
 * <p>Deliberately much narrower than EmakiItem's internal definition, which exposes raw YAML nodes as
 * bare {@link Object} for display names, lore, and action scripts. Those are configuration internals
 * that server owners restructure freely; surfacing them would make third-party code break on a config
 * change.
 *
 * <p>Note that {@link #rarity()} is the vanilla {@code minecraft:rarity} value. EmakiItem has no tier or
 * quality concept of its own.
 *
 * @param id                canonical lowercase definition id
 * @param material          the base vanilla material, or {@code null} when the definition builds from a
 *                          custom item source instead
 * @param itemName          the configured plain item name; empty when the definition renders its name
 *                          dynamically
 * @param amount            the default stack size the definition produces
 * @param rarity            vanilla rarity such as {@code common} or {@code epic}; empty when unset
 * @param equipSlot         the equipment slot this item occupies; empty when it is not equipment
 * @param skillIds          EmakiSkills skill ids this item grants
 * @param setId             the item set this definition belongs to; empty when it belongs to none
 * @param setPieceId        this definition's piece id inside its set; empty when it belongs to none
 * @param repairable        whether repair is enabled for this definition
 * @param hasRandomElements whether creating this item involves randomness, meaning results must not be
 *                          cached or assumed identical between calls
 */
public record ItemDefinitionView(@NotNull String id,
                                 @Nullable Material material,
                                 @NotNull String itemName,
                                 int amount,
                                 @NotNull String rarity,
                                 @NotNull String equipSlot,
                                 @NotNull List<String> skillIds,
                                 @NotNull String setId,
                                 @NotNull String setPieceId,
                                 boolean repairable,
                                 boolean hasRandomElements) {

    /**
     * Normalises every reference component so no accessor except {@code material} can return
     * {@code null}.
     *
     * @param id                canonical lowercase definition id
     * @param material          base vanilla material
     * @param itemName          configured plain item name
     * @param amount            default stack size
     * @param rarity            vanilla rarity
     * @param equipSlot         equipment slot
     * @param skillIds          granted skill ids
     * @param setId             owning item set id
     * @param setPieceId        piece id inside the set
     * @param repairable        whether repair is enabled
     * @param hasRandomElements whether creation involves randomness
     */
    public ItemDefinitionView {
        id = id == null ? "" : id;
        itemName = itemName == null ? "" : itemName;
        amount = Math.max(1, amount);
        rarity = rarity == null ? "" : rarity;
        equipSlot = equipSlot == null ? "" : equipSlot;
        skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
        setId = setId == null ? "" : setId;
        setPieceId = setPieceId == null ? "" : setPieceId;
    }

    /** {@return whether this definition belongs to an item set} */
    public boolean partOfSet() {
        return !setId.isEmpty();
    }

    /** {@return whether this definition occupies an equipment slot} */
    public boolean equipment() {
        return !equipSlot.isEmpty();
    }
}
