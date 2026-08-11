package emaki.jiuwu.craft.attribute.api.extension;

import java.util.Collection;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Extension point that lets third-party plugins feed extra attribute values
 * into EmakiAttribute's combat snapshots.
 *
 * <p>Register an implementation through the EmakiAttribute service so its
 * {@link #collect(LivingEntity)} output is merged when an entity's attributes
 * are gathered. Providers are consulted by descending {@link #priority()} value;
 * equal priorities are ordered by normalized provider id.
 *
 * <p>Implementations may be invoked frequently during combat; keep
 * {@link #collect(LivingEntity)} cheap and side-effect free.
 */
public interface AttributeContributionProvider {

    /**
     * {@return a stable, unique identifier for this provider} Used for
     * de-duplication and diagnostics.
     */
    @NotNull
    String id();

    /**
     * {@return the ordering weight of this provider} Higher values are applied
     * earlier when contributions are aggregated.
     */
    int priority();

    /**
     * Collects the attribute contributions this provider grants to the given
     * entity at the moment of the call.
     *
     * @param entity the entity whose attributes are being gathered
     * @return the contributions to merge; return an empty collection (never
     *         {@code null}) when nothing applies
     */
    @NotNull
    Collection<AttributeContribution> collect(@NotNull LivingEntity entity);
}
