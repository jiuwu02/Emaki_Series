package emaki.jiuwu.craft.item.api.preview;

/**
 * Closeable handle for an item layer preview provider registration.
 */
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

    final class NoopHolder {
        private static final ItemLayerPreviewRegistration INSTANCE = () -> {
        };

        private NoopHolder() {
        }
    }
}
