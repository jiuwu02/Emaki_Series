package emaki.jiuwu.craft.corelib.api.capability;

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Revocable handle for one batch of published capabilities.
 *
 * <p>Keep it and close it in {@code onDisable}, before the owning plugin uninstalls its own API
 * bridge. A handle that outlives its owner keeps advertising a capability whose implementation is
 * already gone, which is worse than never publishing it.</p>
 */
public interface CapabilityRegistration extends AutoCloseable {

    /** {@return whether every requested capability was published} */
    boolean successful();

    /** {@return the capabilities this handle actually published; empty when registration failed} */
    @NotNull
    Set<ApiCapability> published();

    /** {@return a stable language key describing why publication failed, or an empty string} */
    @NotNull
    String reasonKey();

    /** {@return whether this handle still holds live publications} */
    boolean active();

    /** Revokes every capability published through this handle. Idempotent. */
    @Override
    void close();

    /**
     * Creates a failed handle, used when EmakiCoreLib is absent or the request was rejected.
     *
     * @param reasonKey stable language key
     * @return an inactive handle
     */
    static @NotNull CapabilityRegistration unavailable(@Nullable String reasonKey) {
        String resolvedReason = reasonKey == null || reasonKey.isBlank() ? "corelib_unavailable" : reasonKey;
        return new CapabilityRegistration() {

            @Override
            public boolean successful() {
                return false;
            }

            @Override
            public @NotNull Set<ApiCapability> published() {
                return Set.of();
            }

            @Override
            public @NotNull String reasonKey() {
                return resolvedReason;
            }

            @Override
            public boolean active() {
                return false;
            }

            @Override
            public void close() {
                // no-op
            }
        };
    }
}
