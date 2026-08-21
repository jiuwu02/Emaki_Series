package emaki.jiuwu.craft.station.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

/**
 * Read-only view of one material requirement inside a recipe.
 *
 * <p>{@code sources} is an "any of" set: the requirement is satisfied by the combined count of every
 * listed identity, not by each of them separately. Matching is unordered set matching, so a
 * requirement never cares which input slot an item sits in.
 *
 * <p>{@code sources} is empty when the recipe describes the requirement with a component matcher
 * instead of a fixed identity list. Such a requirement has no enumerable identity set, so callers
 * that render or index by identity must tolerate an empty list rather than assume a first element.
 *
 * @param sources the acceptable item identities; empty when the requirement is matcher-described
 * @param amount  the units required per batch; always positive
 * @param consume whether satisfying this requirement debits the materials
 */
public record MaterialRequirementView(@NotNull List<ItemSourceRef> sources, long amount, boolean consume) {

    /**
     * Creates a material-requirement view with a defensively copied source list.
     *
     * @param sources the acceptable item identities, empty when matcher-described
     * @param amount  the units required per batch
     * @param consume whether satisfying this requirement debits the materials
     * @throws NullPointerException     when {@code sources} is {@code null}
     * @throws IllegalArgumentException when {@code amount} is not positive
     */
    public MaterialRequirementView {
        if (sources == null) {
            throw new NullPointerException("sources");
        }
        sources = List.copyOf(sources);
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }
}
