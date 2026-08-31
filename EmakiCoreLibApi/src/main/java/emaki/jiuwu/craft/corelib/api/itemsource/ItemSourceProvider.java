package emaki.jiuwu.craft.corelib.api.itemsource;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provider for one item-source kind, including shorthand, creation, identification, display, probing,
 * and lifecycle state.
 *
 * <p>{@link #identify}, {@link #create}, {@link #supports}, and {@link #probe} may run on any thread
 * and must not touch Bukkit world/entity state. Lifecycle hooks run on the server thread. Close the
 * {@link ItemSourceRegistration} on disable.
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
     * {@return claimed shorthand prefixes}
     *
     * <p>Prefixes must be unique; matching uses longest-prefix-first.
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
     * Claims a reference. Rejection skips this provider's create/display/probe paths; {@code null}
     * should return false. Runtime and linkage failures are treated as rejection.
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
     * Supplies a MiniMessage name after {@link #supports} accepts the reference. {@code null}, blank,
     * runtime failure, or linkage failure continues to lower-priority providers and CoreLib fallbacks.
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
     * Called when the backing plugin enables or signals completed item loading.
     * {@code itemsLoaded} distinguishes those phases.
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
