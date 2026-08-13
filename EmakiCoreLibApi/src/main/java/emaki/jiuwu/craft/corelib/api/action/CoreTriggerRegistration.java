package emaki.jiuwu.craft.corelib.api.action;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Revocable handle for one registered trigger.
 *
 * <p>Mirrors {@link CoreStageRegistration}'s semantics — keep it, close it in {@code onDisable},
 * {@link #close()} is idempotent, and no plugin can revoke another plugin's trigger by id. It is a
 * separate type rather than a reuse of {@code CoreStageRegistration} because that interface must
 * report a {@link CoreStageKind}, and a trigger is not one of the three stage roles.</p>
 */
@ApiStatus.Experimental
public interface CoreTriggerRegistration extends AutoCloseable {

    /** {@return whether the registration succeeded} */
    boolean successful();

    /** {@return the registered trigger id, or an empty string when registration failed} */
    @NotNull
    String triggerId();

    /** {@return a stable language key describing why registration failed, or an empty string} */
    @NotNull
    String reasonKey();

    /** {@return whether this handle still holds a live registration} */
    boolean active();

    /** Revokes this registration. Idempotent. */
    @Override
    void close();

    /**
     * Creates a failed handle, used when EmakiCoreLib is absent or the registry is not built yet.
     *
     * @param reasonKey stable language key
     * @return an inactive handle
     */
    static @NotNull CoreTriggerRegistration unavailable(@Nullable String reasonKey) {
        String resolvedReason = reasonKey == null ? "corelib_unavailable" : reasonKey;
        return new CoreTriggerRegistration() {

            @Override
            public boolean successful() {
                return false;
            }

            @Override
            public @NotNull String triggerId() {
                return "";
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
