package emaki.jiuwu.craft.codex.api;

import org.jetbrains.annotations.ApiStatus;

/** Closeable handle for one externally registered advancement. */
@ApiStatus.NonExtendable
@FunctionalInterface
public interface AdvancementRegistration extends AutoCloseable {
    @Override void close();

    static AdvancementRegistration noop() {
        return NoopHolder.INSTANCE;
    }

    final class NoopHolder {
        private static final AdvancementRegistration INSTANCE = () -> { };
        private NoopHolder() { }
    }
}
