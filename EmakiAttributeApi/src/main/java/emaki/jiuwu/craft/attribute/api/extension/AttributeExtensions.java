package emaki.jiuwu.craft.attribute.api.extension;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.attribute.api.PdcAttributeAccess;

/**
 * Third-party contribution, gate and item-PDC extension points.
 *
 * <p>Reached through {@code EmakiAttributeApi.extensions()}. Both registration methods return a non-null
 * closeable handle even when the arguments were rejected or EmakiAttribute is absent, so a caller never has
 * to null-check or catch to find out whether the runtime is present.
 *
 * <p><strong>Handle lifecycle:</strong> close a handle when your plugin tears down its integration. Closing is
 * idempotent — repeated closes do nothing — and a superseded handle is inert, so it will never remove the
 * registration that replaced it. EmakiAttribute additionally drops every registration owned by a plugin when
 * that plugin is disabled, so a missed close does not leak past disable.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface AttributeExtensions {

    /**
     * Registers an owner-scoped contribution provider that can add attribute contributions to entities.
     *
     * <p>Providers are keyed by the normalized {@link AttributeContributionProvider#id()} (trimmed and
     * lower-cased with {@code Locale.ROOT}). That key is <strong>global rather than per-owner</strong>, so a
     * second registration using the same provider id supersedes the first even if a different plugin owns it;
     * pick an id namespaced to your plugin to avoid colliding with another integration.
     *
     * <p>Registration is internally synchronized and allowed from any thread. Close the returned handle on
     * disable; closing is idempotent, and a superseded handle never removes the replacement provider.
     *
     * @param owner    the registering plugin, used for automatic cleanup on disable; {@code null} or an
     *                 already-disabled plugin yields an inactive no-op handle
     * @param provider the contribution provider; {@code null} or a blank {@code id()} yields an inactive
     *                 no-op handle
     * @return a closeable handle for this registration, never {@code null}; the handle is inert when the
     *         arguments were rejected or EmakiAttribute is unavailable
     */
    @NotNull
    ContributionProviderRegistration registerContributionProvider(@Nullable Plugin owner,
            @Nullable AttributeContributionProvider provider);

    /**
     * Registers an owner-scoped item contribution gate that can veto every attribute contribution of one item.
     *
     * <p>Gates are keyed by the normalized {@link ItemContributionGate#id()}, and as with contribution
     * providers that key is global rather than per-owner, so reusing an id supersedes the existing gate.
     * Registration is internally synchronized and allowed from any thread; the same idempotent close and
     * inert-superseded-handle rules apply.
     *
     * <p>A gate is consulted while equipment contributions are collected and is expected to be cheap. A gate
     * that throws is treated as accepting the item, so a broken gate cannot strip a player's attributes; do
     * not rely on an exception to deny a contribution.
     *
     * @param owner the registering plugin, used for automatic cleanup on disable; {@code null} yields an
     *              inactive no-op handle
     * @param gate  the gate implementation; {@code null} or a blank {@code id()} yields an inactive no-op
     *              handle
     * @return a closeable handle for this registration, never {@code null}; the handle is inert when the
     *         arguments were rejected or EmakiAttribute is unavailable
     */
    @NotNull
    ItemContributionGateRegistration registerItemContributionGate(@Nullable Plugin owner,
            @Nullable ItemContributionGate gate);

    /**
     * Registers an owner-scoped equipment slot provider so the items it exposes take part in attribute
     * aggregation.
     *
     * <p>Slot providers are keyed by the normalized {@link AttributeSlotProvider#id()}, and as with
     * contribution providers that key is global rather than per-owner, so reusing an id supersedes the
     * existing provider. The vanilla slots are registered by EmakiAttribute itself under the names
     * {@code main_hand}, {@code off_hand}, {@code helmet}, {@code chestplate}, {@code leggings}, and
     * {@code boots}; registering one of those ids replaces the built-in provider, which is rarely what a
     * caller wants. Registration is internally synchronized and allowed from any thread; the same
     * idempotent close and inert-superseded-handle rules apply.
     *
     * <p>Adding or removing a provider changes the snapshot cache signature, so an aggregated value
     * picks up the new slot on the next collection rather than requiring a manual invalidation.
     *
     * @param owner    the registering plugin, used for automatic cleanup on disable; {@code null} yields
     *                 an inactive no-op handle
     * @param provider the slot provider; {@code null} or a blank {@code id()} yields an inactive no-op
     *                 handle
     * @return a closeable handle for this registration, never {@code null}; the handle is inert when the
     *         arguments were rejected or EmakiAttribute is unavailable
     */
    @NotNull
    SlotProviderRegistration registerSlotProvider(@Nullable Plugin owner,
            @Nullable AttributeSlotProvider provider);

    /**
     * {@return item persistent-data access for this module's own attribute keys; never {@code null}, falling
     * back to a no-op implementation while EmakiAttribute is unavailable}
     */
    @NotNull
    PdcAttributeAccess pdc();
}
