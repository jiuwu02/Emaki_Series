package emaki.jiuwu.craft.item.api.effect;

import org.jetbrains.annotations.ApiStatus;

/**
 * Closeable handle for a custom effect type registration.
 */
@ApiStatus.NonExtendable
@FunctionalInterface
public interface ItemEffectRegistration extends AutoCloseable {

    /**
     * Releases this registration. Closing an already closed or superseded handle has no effect.
     */
    @Override
    void close();

    /** {@return a reusable no-op registration handle} */
    static ItemEffectRegistration noop() {
        return NoopHolder.INSTANCE;
    }

    @ApiStatus.Internal
    final class NoopHolder {
        private static final ItemEffectRegistration INSTANCE = () -> {
        };

        private NoopHolder() {
        }
    }
}
