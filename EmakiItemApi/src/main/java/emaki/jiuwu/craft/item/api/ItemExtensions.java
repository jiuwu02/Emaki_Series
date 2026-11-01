package emaki.jiuwu.craft.item.api;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.item.api.effect.ItemEffectRegistration;
import emaki.jiuwu.craft.item.api.effect.ItemEffectType;
import emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewProvider;
import emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewRegistration;

/**
 * Extension points third-party plugins can plug into EmakiItem.
 *
 * <p>Reached through {@code EmakiItemApi.extensions()}.
 *
 * <h2>Layer previews</h2>
 * EmakiItem's inspection commands and GUIs render a preview of how each subsystem's layer contributes to
 * an item. Register a provider so your own layer appears alongside the built-in ones.
 *
 * <p>This is a rendering hook only. It does not read or write an item's stored layer data, and returning
 * a preview does not make your layer part of the item.
 *
 * <p><strong>Thread:</strong> registration may be called from any thread. Your provider is invoked from
 * whichever thread requested the preview, typically a command or GUI thread; keep it non-blocking and do
 * not schedule from it.
 *
 * <h2>Custom effect types</h2>
 * Items declare behaviour under {@code effects:} using a {@code type} field. Register an
 * {@link ItemEffectType} to add your own type next to the built-in ones, then reference its id from any item
 * definition. A registered type is applied while the item is built, so a rebuild or reload picks up handler
 * and configuration changes without a restart.
 *
 * <h2>Lifecycle</h2>
 * Close the returned registration in your {@code onDisable}. EmakiItem also drops registrations
 * automatically when their owning plugin disables, so a missed close does not leak across a reload —
 * but relying on that leaves your provider live for the rest of the current session.
 */
@ApiStatus.NonExtendable
public interface ItemExtensions {

    /**
     * Registers a layer preview provider.
     *
     * @param owner    the plugin that owns the registration lifecycle
     * @param provider the provider to register
     * @return a closeable registration; an inactive registration when EmakiItem is unavailable or the
     *         arguments were rejected
     */
    @NotNull
    ItemLayerPreviewRegistration registerLayerPreview(@Nullable Plugin owner,
                                                      @Nullable ItemLayerPreviewProvider provider);

    /**
     * Removes every layer preview provider a plugin registered.
     *
     * @param owner the plugin whose registrations are removed
     */
    void unregisterLayerPreviews(@Nullable Plugin owner);

    /**
     * Registers a custom {@code effects} type.
     *
     * <p>Registrations are keyed by the normalized type id, so re-registering the same id supersedes the
     * previous handler regardless of owner. Built-in type ids ({@code variables}, {@code ea_attribute},
     * {@code es_skill}, {@code accessory_slot}, {@code name_action}, {@code lore_action}) are reserved and
     * cannot be replaced.
     *
     * <p>Registering a type discards EmakiItem's built-item prototype cache, so already-built items pick up
     * the handler on their next build without a reload. Items that use the type are never rejected while no
     * handler is registered; the entry is simply skipped until one appears.
     *
     * <p><strong>Thread:</strong> registration is internally synchronized and may be called from any thread.
     * The handler itself is invoked on the thread building the item.
     *
     * @param owner the plugin that owns the registration lifecycle
     * @param type  the effect type to register
     * @return a closeable registration; an inactive handle when EmakiItem is unavailable or the arguments,
     *         including the type id, were rejected
     */
    @NotNull
    ItemEffectRegistration registerEffectType(@Nullable Plugin owner, @Nullable ItemEffectType type);

    /**
     * Removes every custom effect type a plugin registered.
     *
     * @param owner the plugin whose registrations are removed
     */
    void unregisterEffectTypes(@Nullable Plugin owner);
}
