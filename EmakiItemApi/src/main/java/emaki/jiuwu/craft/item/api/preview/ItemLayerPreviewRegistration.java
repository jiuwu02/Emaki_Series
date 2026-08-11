package emaki.jiuwu.craft.item.api.preview;

import org.jetbrains.annotations.ApiStatus;

/**
 * Closeable handle for an item layer preview provider registration.
 */
@ApiStatus.NonExtendable
@FunctionalInterface
public interface ItemLayerPreviewRegistration extends AutoCloseable {

    /**
     * Releases this registration. Closing an already closed or superseded handle
     * has no effect.
     */
    @Override
    void close();

    /** {@return a reusable no-op registration handle} */
    static ItemLayerPreviewRegistration noop() {
        return NoopHolder.INSTANCE;
    }

    @ApiStatus.Internal
    final class NoopHolder {
        private static final ItemLayerPreviewRegistration INSTANCE = () -> {
        };

        private NoopHolder() {
        }
    }
}
