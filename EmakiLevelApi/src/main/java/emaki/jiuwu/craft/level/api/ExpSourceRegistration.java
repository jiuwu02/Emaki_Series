package emaki.jiuwu.craft.level.api;

import org.jetbrains.annotations.ApiStatus;

/** Closeable handle for an experience source provider registration. */
@ApiStatus.NonExtendable
@FunctionalInterface
public interface ExpSourceRegistration extends AutoCloseable {

    /** Releases this registration. Repeated closes and superseded handles have no effect. */
    @Override
    void close();

    /** {@return a reusable inactive registration handle} */
    static ExpSourceRegistration noop() {
        return NoopHolder.INSTANCE;
    }

    final class NoopHolder {
        private static final ExpSourceRegistration INSTANCE = () -> {
        };

        private NoopHolder() {
        }
    }
}
