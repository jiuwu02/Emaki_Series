package emaki.jiuwu.craft.corelib.api.action.v2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Revocable handle for one registered stage.
 *
 * <p>Keep it and close it in {@code onDisable}. A registration that outlives its owner leaks across
 * reloads. Unlike v1 there is no way to unregister another plugin's stage by id.</p>
 */
public interface CoreStageRegistration extends AutoCloseable {

    /** {@return whether the registration succeeded} */
    boolean successful();

    /** {@return the registered stage id, or an empty string when registration failed} */
    @NotNull
    String stageId();

    /** {@return which table this stage went into} */
    @NotNull
    CoreStageKind kind();

    /** {@return a stable language key describing why registration failed, or an empty string} */
    @NotNull
    String reasonKey();

    /** {@return whether this handle still holds a live registration} */
    boolean active();

    /** Revokes this registration. Idempotent. */
    @Override
    void close();

    /**
     * Creates a failed handle, used when EmakiCoreLib is absent.
     *
     * @param kind the table the caller aimed at
     * @param reasonKey stable language key
     * @return an inactive handle
     */
    static @NotNull CoreStageRegistration unavailable(@Nullable CoreStageKind kind, @Nullable String reasonKey) {
        CoreStageKind resolvedKind = kind == null ? CoreStageKind.ACTION : kind;
        String resolvedReason = reasonKey == null ? "corelib_unavailable" : reasonKey;
        return new CoreStageRegistration() {

            @Override
            public boolean successful() {
                return false;
            }

            @Override
            public @NotNull String stageId() {
                return "";
            }

            @Override
            public @NotNull CoreStageKind kind() {
                return resolvedKind;
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
