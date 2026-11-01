package emaki.jiuwu.craft.corelib.api.action;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Answers "which mob of my system is this entity?" for the shared target selector.
 *
 * <p>EmakiCoreLib ships a built-in provider for MythicMobs. Every other mob system — including the
 * Emaki gameplay modules — contributes one through
 * {@link emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi#registerTargetIdentityProvider}, which keeps the
 * library free of a compile-time dependency on the systems it can recognise.</p>
 *
 * <p>Implementations run on the entity's owner thread, because identifying a mob usually reads live
 * entity state such as its persistent data container. A provider must return {@code null} rather than
 * throw when its system is not installed or its data is not loaded yet; the selector then treats the
 * related condition as unanswered.</p>
 */
public interface CoreTargetIdentityProvider {

    /**
     * {@return the system id used in conditions} — {@code emakimobs}, {@code mythicmobs}, and so on,
     * matched case-insensitively
     */
    @NotNull
    String systemId();

    /**
     * Identifies one entity.
     *
     * @param entity the entity to inspect, possibly {@code null}
     * @return the identity when this entity belongs to this provider's system, otherwise {@code null}
     */
    @Nullable
    CoreTargetIdentity identify(@Nullable LivingEntity entity);
}