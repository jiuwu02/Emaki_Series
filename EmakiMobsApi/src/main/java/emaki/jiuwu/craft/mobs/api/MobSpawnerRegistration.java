package emaki.jiuwu.craft.mobs.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Closeable handle for a custom mob spawner registration.
 */
@ApiStatus.NonExtendable
@FunctionalInterface
public interface MobSpawnerRegistration extends AutoCloseable {

    /**
     * Releases this registration. Closing an already closed or superseded handle has no effect.
     */
    @Override
    void close();

    /** {@return a reusable no-op registration handle} */
    static MobSpawnerRegistration noop() {
        return NoopHolder.INSTANCE;
    }

    @ApiStatus.Internal
    final class NoopHolder {
        private static final MobSpawnerRegistration INSTANCE = () -> {
        };

        private NoopHolder() {
        }
    }
}
