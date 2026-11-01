package emaki.jiuwu.craft.station.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

/**
 * Read-only view of one material requirement of a recipe.
 *
 * <p>{@code sources} lists the item identities accepted for this requirement; they are matched
 * as an unordered set, and every entry beyond the first is presented to players as an
 * alternative. The compact constructor normalizes a {@code null} {@code materialId} to an empty
 * string and a {@code null} {@code requirementId} or {@code countKey} to {@code materialId},
 * rejects a {@code null} {@code sources} list, copies it into an immutable list, and requires a
 * positive {@code amount}. Instances are immutable.
 *
 * @param materialId    material identity declared by the recipe; {@code null} becomes an empty
 *                      string
 * @param requirementId identity of this requirement within the recipe, unique per requirement;
 *                      {@code null} falls back to {@code materialId}
 * @param countKey      key grouping this requirement's counted amounts; {@code null} falls back
 *                      to {@code materialId}
 * @param sources       accepted item identities, matched as an unordered set; never {@code null}
 * @param amount        units required per batch; must be positive
 * @param consume       whether the units are actually taken on submission; when {@code false}
 *                      the player only has to hold them
 */
public record MaterialRequirementView(@NotNull String materialId,
        @NotNull String requirementId,
        @NotNull String countKey,
        @NotNull List<ItemSourceRef> sources,
        long amount,
        boolean consume) {

    /**
     * Normalizes the identity triple and freezes the source list.
     *
     * @param materialId    material identity; {@code null} becomes an empty string
     * @param requirementId requirement identity; {@code null} falls back to {@code materialId}
     * @param countKey      counting key; {@code null} falls back to {@code materialId}
     * @param sources       accepted item identities; copied, never {@code null}
     * @param amount        units required per batch
     * @param consume       whether the units are taken on submission
     * @throws NullPointerException     when {@code sources} is {@code null}
     * @throws IllegalArgumentException when {@code amount} is not positive
     */
    public MaterialRequirementView {
        materialId = materialId == null ? "" : materialId;
        requirementId = requirementId == null ? materialId : requirementId;
        countKey = countKey == null ? materialId : countKey;
        if (sources == null) {
            throw new NullPointerException("sources");
        }
        sources = List.copyOf(sources);
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount 必须为正数: " + amount);
        }
    }

    /**
     * Creates a requirement with an empty identity triple.
     *
     * @param sources the accepted item identities
     * @param amount  units required per batch
     * @param consume whether the units are taken on submission
     * @throws NullPointerException     when {@code sources} is {@code null}
     * @throws IllegalArgumentException when {@code amount} is not positive
     */
    public MaterialRequirementView(List<ItemSourceRef> sources, long amount, boolean consume) {
        this("", "", "", sources, amount, consume);
    }
}
