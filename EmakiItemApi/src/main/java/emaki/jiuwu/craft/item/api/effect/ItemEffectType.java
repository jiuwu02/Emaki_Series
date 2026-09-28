package emaki.jiuwu.craft.item.api.effect;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A third-party {@code effects} type that EmakiItem resolves while building an item definition.
 *
 * <p>Register an implementation through
 * {@link emaki.jiuwu.craft.item.api.ItemExtensions#registerEffectType(org.bukkit.plugin.Plugin, ItemEffectType)}
 * and items may then declare {@code effects: - type: "<your-id>"} next to the built-in types
 * ({@code variables}, {@code ea_attribute}, {@code es_skill}, {@code accessory_slot}, {@code name_action},
 * {@code lore_action}). The built-in types are reserved; registering one of their ids is rejected and yields an
 * inactive handle.
 *
 * <h2>Lifecycle</h2>
 * A handler is consulted every time an item using its type is built. EmakiItem does not cache the parsed
 * result across builds, so an item rebuild, an EmakiItem reload, or a re-registration of the type is always
 * observed. Registering or unregistering a type also discards EmakiItem's built-item prototype cache and the
 * affected definitions are re-parsed on their next build.
 *
 * <h2>Threading</h2>
 * {@link #typeId()} is read while registering and may be called from any thread. {@link #parse} and every
 * {@link ItemEffect} callback run on the thread that is building the item, normally the owning stack's thread;
 * keep them non-blocking and do not schedule from them.
 *
 * <p>The raw effect entry is part of the item definition signature EmakiItem stores and compares, so editing a
 * custom effect's configuration is enough to trigger an item update; no signature hook is required.
 */
public interface ItemEffectType {

    /**
     * Id matched against the {@code type} field of an {@code effects} entry.
     *
     * <p>Ids are trimmed, lower-cased with {@code Locale.ROOT} and have spaces replaced by underscores before
     * matching, and ids of the built-in effect types cannot be used.
     *
     * @return the effect type id; a blank id makes the registration inactive
     */
    @NotNull String typeId();

    /**
     * Turns one raw effect entry into an applicable effect.
     *
     * <p>Called each time an item declaring this type is built, before
     * {@link ItemEffect#clear(ItemEffectApplyContext)} and {@link ItemEffect#apply(ItemEffectApplyContext)}
     * are invoked. Return {@code null} to skip this entry — for example when the configuration does not apply
     * to the current item.
     *
     * @param context the definition id and the raw effect entry
     * @return the effect to apply, or {@code null} to skip this entry
     */
    @Nullable ItemEffect parse(@NotNull ItemEffectParseContext context);
}
