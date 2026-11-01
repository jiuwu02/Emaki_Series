package emaki.jiuwu.craft.corelib.api.animation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owner-scoped registration handle for an {@link AnimationDefinition}.
 *
 * <p>Keep the handle and close it on plugin disable. A definition registered by one owner never revokes
 * another owner's definition; re-registering the same id under the same owner replaces the previous
 * definition and stops future playbacks of the old one.</p>
 */
public interface AnimationRegistration extends AutoCloseable {

    /** {@return whether the definition was accepted} */
    boolean successful();

    /** {@return the registered definition id, or an empty string when registration failed} */
    @NotNull String definitionId();

    /** {@return a stable language key describing why registration failed, or an empty string} */
    @NotNull String reasonKey();

    /** {@return whether this handle still holds a live registration} */
    boolean active();

    /** Revokes this registration. Idempotent. */
    @Override
    void close();

    /**
     * Creates a failed handle, used when EmakiCoreLib is absent.
     *
     * @param reasonKey stable language key; {@code null} selects {@code corelib_unavailable}
     * @return an inactive handle
     */
    static @NotNull AnimationRegistration unavailable(@Nullable String reasonKey) {
        String resolved = reasonKey == null ? "corelib_unavailable" : reasonKey;
        return new AnimationRegistration() {

            @Override
            public boolean successful() {
                return false;
            }

            @Override
            public @NotNull String definitionId() {
                return "";
            }

            @Override
            public @NotNull String reasonKey() {
                return resolved;
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
