package emaki.jiuwu.craft.attribute.api.extension;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.attribute.api.PdcAttributeAccess;

/** Third-party contribution, gate and item-PDC extension points. */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface AttributeExtensions {

    /**
     * Registers an owner-scoped contribution provider.
     *
     * <p>Registration is allowed from any thread. Close the returned handle on disable; closing is
     * idempotent, and a superseded handle never removes the replacement provider.
     */
    @NotNull
    ContributionProviderRegistration registerContributionProvider(@Nullable Plugin owner,
            @Nullable AttributeContributionProvider provider);

    /**
     * Registers an owner-scoped item contribution gate. Registration is allowed from any thread.
     */
    @NotNull
    ItemContributionGateRegistration registerItemContributionGate(@Nullable Plugin owner,
            @Nullable ItemContributionGate gate);

    /** {@return item persistent-data access; never {@code null}} */
    @NotNull
    PdcAttributeAccess pdc();
}
