package emaki.jiuwu.craft.attribute.api;

import java.util.Collection;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Supplies additional attribute contributions for a living entity at runtime.
 */
public interface AttributeContributionProvider {

    /**
     * Returns the stable provider id used for ordering and diagnostics.
     *
     * @return the provider id
     */
    @NotNull
    String id();

    /**
     * Returns the provider priority. Higher values are processed first.
     *
     * @return the provider priority
     */
    int priority();

    /**
     * Collects contributions for the supplied entity.
     *
     * @param entity the entity being evaluated
     * @return the contributions produced by this provider
     */
    @NotNull
    Collection<AttributeContribution> collect(@NotNull LivingEntity entity);
}
