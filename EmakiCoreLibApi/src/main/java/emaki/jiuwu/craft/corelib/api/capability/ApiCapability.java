package emaki.jiuwu.craft.corelib.api.capability;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stable identifier for one optional API capability, written {@code namespace:id}.
 *
 * <p>A capability answers exactly one question: "is this method actually callable on the version of
 * the providing plugin that is installed right now?" It exists because version strings and
 * reflection are both unusable for that question &mdash; a version string tells you nothing about
 * which build shaded which API, and reflection turns a compile-time contract into a runtime string
 * lookup.</p>
 *
 * <h2>Why gating works</h2>
 * The JVM resolves a method reference the first time the {@code invoke} instruction actually
 * executes, not when the enclosing class is loaded. So a call guarded by a capability check is never
 * resolved while the capability is missing, and no {@link NoSuchMethodError} can be thrown:
 *
 * <pre>{@code
 * if (EmakiCoreLibApi.hasCapability(ApiCapability.of("emakistorage:atomic_batch"))) {
 *     storage.applyBatchAsync(playerId, request);   // only reached once the capability is present
 * }
 * }</pre>
 *
 * <p>This guarantee is lost the moment the call site is moved somewhere that evaluates eagerly:
 * a field initialiser, a static block, or an {@code Optional.map(...)} argument that is built before
 * the check runs. Keep the guarded call inside the {@code if} body or inside a lambda that only the
 * {@code if} body invokes.</p>
 *
 * <h2>Where the constant may live</h2>
 * A consumer must build the identifier from {@link #of(String)} against this
 * {@code emaki-corelib-api} class, never from a typed constant published by the providing plugin's
 * own API jar. Referencing, say, {@code StorageCapabilities.ATOMIC_BATCH} makes that class part of
 * the consumer's own constant pool: with an older provider installed the class is simply absent and
 * the consumer fails with {@link NoClassDefFoundError} while loading itself, which happens earlier
 * and is harder to guard than a missing method. Providers may still declare typed constants for
 * their own use and for callers that hard-depend on them.
 *
 * @param namespace provider namespace, conventionally the providing plugin name in lower case
 * @param id capability id within the namespace
 */
public record ApiCapability(@NotNull String namespace, @NotNull String id) {

    /**
     * Normalises both segments with {@link Locale#ROOT}.
     *
     * @throws IllegalArgumentException when either segment is blank or contains {@code :}
     */
    public ApiCapability {
        namespace = normalize(namespace, "namespace");
        id = normalize(id, "id");
    }

    /** {@return the canonical {@code namespace:id} form} */
    public @NotNull String key() {
        return namespace + ':' + id;
    }

    /**
     * Parses a canonical {@code namespace:id} identifier.
     *
     * @param key the identifier text
     * @return the parsed capability
     * @throws IllegalArgumentException when {@code key} is blank or is not exactly two non-blank
     *         segments separated by one {@code :}
     */
    public static @NotNull ApiCapability of(@Nullable String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("capability key must not be blank");
        }
        String trimmed = key.trim();
        int separator = trimmed.indexOf(':');
        if (separator < 0 || trimmed.indexOf(':', separator + 1) >= 0) {
            throw new IllegalArgumentException("capability key must be \"namespace:id\": " + key);
        }
        return new ApiCapability(trimmed.substring(0, separator), trimmed.substring(separator + 1));
    }

    @Override
    public String toString() {
        return key();
    }

    private static String normalize(String segment, String label) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException("capability " + label + " must not be blank");
        }
        String normalized = segment.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') >= 0) {
            throw new IllegalArgumentException("capability " + label + " must not contain ':': " + segment);
        }
        return normalized;
    }
}
