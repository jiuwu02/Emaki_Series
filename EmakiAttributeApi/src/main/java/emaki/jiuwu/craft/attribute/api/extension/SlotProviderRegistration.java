package emaki.jiuwu.craft.attribute.api.extension;

import org.jetbrains.annotations.ApiStatus;

/**
 * Closeable handle for an {@link AttributeSlotProvider} registration.
 */
@ApiStatus.NonExtendable
@FunctionalInterface
public interface SlotProviderRegistration extends AutoCloseable {

    /**
     * Releases this registration. Closing an already closed or superseded handle
     * has no effect.
     */
    @Override
    void close();

    /** {@return a reusable no-op registration handle} */
    static SlotProviderRegistration noop() {
        return NoopHolder.INSTANCE;
    }

    /** Holder for the shared no-op handle. */
    final class NoopHolder {

        private static final SlotProviderRegistration INSTANCE = () -> {
        };

        private NoopHolder() {
        }
    }
}
