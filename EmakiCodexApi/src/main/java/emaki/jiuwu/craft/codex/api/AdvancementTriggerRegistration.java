package emaki.jiuwu.craft.codex.api;

import org.jetbrains.annotations.ApiStatus;

/** Closeable handle for one external advancement-trigger provider. */
@ApiStatus.NonExtendable
@FunctionalInterface
public interface AdvancementTriggerRegistration extends AutoCloseable {
    @Override void close();

    static AdvancementTriggerRegistration noop() {
        return NoopHolder.INSTANCE;
    }

    final class NoopHolder {
        private static final AdvancementTriggerRegistration INSTANCE = () -> { };
        private NoopHolder() { }
    }
}
