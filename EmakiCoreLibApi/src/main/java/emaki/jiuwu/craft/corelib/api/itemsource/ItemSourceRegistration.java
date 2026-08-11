package emaki.jiuwu.craft.corelib.api.itemsource;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Revocable handle for one registered item source provider.
 *
 * <p>Keep it and close it in {@code onDisable}. A registration that outlives its owner keeps answering
 * for a plugin that is already gone, so config referring to that source resolves to items that can no
 * longer be built.
 */
public interface ItemSourceRegistration extends AutoCloseable {

    /** {@return whether the registration succeeded} */
    boolean successful();

    /** {@return the registered kind, or {@link ItemSourceKind#VANILLA} when registration failed} */
    @NotNull
    ItemSourceKind kind();

    /** {@return a stable language key describing why registration failed, or an empty string} */
    @NotNull
    String reasonKey();

    /** {@return whether this handle still holds a live registration} */
    boolean active();

    /** Revokes this registration. Idempotent. */
    @Override
    void close();

    /**
     * Creates a failed handle, used when EmakiCoreLib is absent or the request was refused.
     *
     * @param kind the kind the caller aimed at
     * @param reasonKey stable language key
     * @return an inactive handle
     */
    static @NotNull ItemSourceRegistration unavailable(@Nullable ItemSourceKind kind, @Nullable String reasonKey) {
        ItemSourceKind resolvedKind = kind == null ? ItemSourceKind.VANILLA : kind;
        String resolvedReason = reasonKey == null || reasonKey.isBlank() ? "corelib_unavailable" : reasonKey;
        return new ItemSourceRegistration() {

            @Override
            public boolean successful() {
                return false;
            }

            @Override
            public @NotNull ItemSourceKind kind() {
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
