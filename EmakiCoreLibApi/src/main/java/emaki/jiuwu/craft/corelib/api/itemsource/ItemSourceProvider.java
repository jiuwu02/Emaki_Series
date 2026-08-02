package emaki.jiuwu.craft.corelib.api.itemsource;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies one kind of item source: its identity, how it is written in config, and how to build,
 * recognise and probe its items.
 *
 * <h2>One registration carries the whole chain</h2>
 * A provider declares {@link #kind()} <em>and</em> {@link #shorthandPrefixes()} together, on purpose.
 * The old design split them: EmakiItem supplied the resolver while CoreLib hard-coded the
 * {@code emakiitem-} prefix and the enum constant, so CoreLib knew about an item source it did not
 * implement. Registering a provider whose prefixes lived somewhere else would be worse than useless
 * &mdash; nothing would claim {@code emakiitem-mystic_dust} in config and parsing would just return
 * nothing.
 *
 * <h2>Threading</h2>
 * {@link #identify}, {@link #create}, {@link #supports} and {@link #probe} may be called from any
 * thread and must not touch Bukkit world or entity state. The lifecycle hooks are always called on the
 * server thread.
 *
 * <h2>Lifecycle</h2>
 * Keep the {@link ItemSourceRegistration} handle and close it in {@code onDisable}. A CoreLib reload
 * rebuilds nothing here, but a provider whose handle outlives its owner keeps answering for a plugin
 * that is already gone.
 */
public interface ItemSourceProvider {

    /** {@return which kind this provider owns; also its registry identity} */
    @NotNull
    ItemSourceKind kind();

    /**
     * {@return resolution priority, highest first}
     *
     * <p>CoreLib's built-in vanilla provider uses {@code 0} so anything else outranks it. Third-party
     * providers should use {@code 100} or above.
     */
    int priority();

    /**
     * {@return the shorthand prefixes this provider claims, such as {@code {"emakiitem-", "ei-"}}}
     *
     * <p>Two providers claiming the same prefix is a hard registration failure naming the first owner,
     * never a silent takeover. Matching is by longest prefix first, so {@code eci-} is not swallowed by
     * {@code ei-}.
     */
    @NotNull
    Set<String> shorthandPrefixes();

    /**
     * {@return the prefix used when writing a reference back out}
     *
     * <p>Defaults to the longest declared prefix, which is the spelled-out one in every case this
     * repository ships ({@code minecraft-} over {@code mc-}, {@code craftengine-} over {@code ce-}).
     * Override when that is not the canonical form for your source.
     */
    default @NotNull String canonicalShorthandPrefix() {
        return shorthandPrefixes().stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .map(prefix -> prefix.trim().toLowerCase(Locale.ROOT))
                .max(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
                .orElse("");
    }

    /**
     * Writes a reference back into shorthand text.
     *
     * @param ref the reference to write
     * @return the shorthand, or {@code null} when this provider cannot express it
     */
    default @Nullable String toShorthand(@Nullable ItemSourceRef ref) {
        if (ref == null || !kind().equals(ref.kind())) {
            return null;
        }
        String prefix = canonicalShorthandPrefix();
        return prefix.isEmpty() ? null : prefix + ref.identifier();
    }

    /**
     * Normalises the identifier portion of a shorthand this provider claimed.
     *
     * <p>Defaults to identity, because {@link ItemSourceRef} already trims, lower-cases and replaces
     * spaces. Override when the kind has its own rules &mdash; vanilla, for instance, rejects
     * namespaced ids outright.
     *
     * @param identifier the raw identifier, already stripped of its prefix
     * @return the normalised identifier, or {@code null}/blank to reject it
     */
    default @Nullable String normalizeIdentifier(@Nullable String identifier) {
        return identifier;
    }

    /**
     * @param ref the reference to test
     * @return whether this provider handles it
     */
    boolean supports(@Nullable ItemSourceRef ref);

    /**
     * Recognises an existing stack as belonging to this source.
     *
     * @param itemStack the stack to inspect
     * @return the reference, or {@code null} when the stack is not from this source
     */
    @Nullable
    ItemSourceRef identify(@Nullable ItemStack itemStack);

    /**
     * Builds a fresh item.
     *
     * @param ref the reference to build
     * @param amount the stack amount; implementations clamp to at least one
     * @return the item, or {@code null} when this source holds no such item
     */
    @Nullable
    ItemStack create(@Nullable ItemSourceRef ref, int amount);

    /**
     * @param ref the reference to name
     * @return a MiniMessage display name, or {@code null} to let CoreLib derive one from the built item
     */
    default @Nullable String displayName(@Nullable ItemSourceRef ref) {
        return null;
    }

    /**
     * Reports whether a reference resolves right now, and why not when it does not.
     *
     * <p>The default builds one item and reads the outcome, which is correct but does a real build.
     * Override when the source can answer more cheaply.
     *
     * @param ref the reference to probe
     * @return the outcome; never {@code null}
     */
    default @NotNull ItemSourceProbeResult probe(@Nullable ItemSourceRef ref) {
        String providerId = kind().key();
        if (!supports(ref)) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.INVALID_SOURCE, ref, providerId,
                    "This provider does not handle the given item source.");
        }
        try {
            ItemStack created = create(ref, 1);
            return created == null || created.getType().isAir()
                    ? ItemSourceProbeResult.of(ItemSourceProbeState.SOURCE_NOT_FOUND, ref, providerId,
                            "The provider holds no item under this identifier.")
                    : ItemSourceProbeResult.ready(ref, providerId);
        } catch (LinkageError exception) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.INCOMPATIBLE, ref, providerId,
                    describe(exception));
        } catch (RuntimeException exception) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.RESOLUTION_ERROR, ref, providerId,
                    describe(exception));
        }
    }

    /**
     * {@return the plugin this provider reads items from, or an empty string when it needs none}
     *
     * <p>CoreLib matches this against {@code PluginEnableEvent} / {@code PluginDisableEvent} to drive
     * the lifecycle hooks below.
     */
    default @NotNull String providerPluginName() {
        return "";
    }

    /**
     * Called once at registration.
     *
     * @return the resulting lifecycle status
     */
    default @NotNull LifecycleStatus bootstrap() {
        return LifecycleStatus.ready();
    }

    /**
     * Called when the backing plugin becomes usable.
     *
     * <p>The {@code itemsLoaded} flag is not decoration. "The plugin enabled" and "the plugin finished
     * loading its items" are different facts: on the first, a provider may still have to detect whether
     * items are available; on the second, it has been told so and may mark itself loaded outright.
     * Collapsing the two would either make load events useless or mark a provider ready too early.
     *
     * @param itemsLoaded {@code true} when the backing plugin signalled that its items are loaded,
     *                    {@code false} when it merely became enabled
     * @return the resulting lifecycle status
     */
    default @NotNull LifecycleStatus onProviderReady(boolean itemsLoaded) {
        return LifecycleStatus.ready();
    }

    /** Called when the backing plugin is disabled. Drop any cached handles here. */
    default void onProviderDisabled() {
        // no-op
    }

    private static String describe(Throwable throwable) {
        if (throwable == null) {
            return "Unknown resolution failure";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
