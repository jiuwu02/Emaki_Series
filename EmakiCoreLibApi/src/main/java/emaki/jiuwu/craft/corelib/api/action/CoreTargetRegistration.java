package emaki.jiuwu.craft.corelib.api.action;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owner-scoped handle for one target condition or one target identity provider registration.
 *
 * <p>Close it on disable. Closing is idempotent, and a handle whose entry has already been replaced
 * does not remove the replacement.</p>
 */
public interface CoreTargetRegistration extends AutoCloseable {

    /** {@return whether the registration was accepted} */
    boolean successful();

    /** {@return the registered id — the condition id or the identity system id, empty when rejected} */
    @NotNull
    String targetId();

    /** {@return a stable language key describing a rejection, or an empty string} */
    @NotNull
    String reasonKey();

    /** {@return whether this handle still holds a live registration} */
    boolean active();

    /** Revokes this registration. Idempotent. */
    @Override
    void close();

    /**
     * Creates a rejected handle, used when EmakiCoreLib is absent or its runtime predates the feature.
     *
     * @param targetId the id the caller aimed at
     * @param reasonKey stable language key
     * @return an inactive handle
     */
    static @NotNull CoreTargetRegistration unavailable(@Nullable String targetId, @Nullable String reasonKey) {
        String resolvedId = targetId == null ? "" : targetId;
        String resolvedReason = reasonKey == null || reasonKey.isBlank()
                ? "corelib_unavailable"
                : reasonKey;
        return new CoreTargetRegistration() {

            @Override
            public boolean successful() {
                return false;
            }

            @Override
            public @NotNull String targetId() {
                return resolvedId;
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