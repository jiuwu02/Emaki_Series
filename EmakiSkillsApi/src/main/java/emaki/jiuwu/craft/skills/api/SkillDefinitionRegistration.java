package emaki.jiuwu.craft.skills.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Closeable handle for an external skill definition registration.
 */
@ApiStatus.NonExtendable
@FunctionalInterface
public interface SkillDefinitionRegistration extends AutoCloseable {

    /**
     * Releases this registration. Closing an already closed or superseded handle has no effect.
     */
    @Override
    void close();

    /** {@return a reusable no-op registration handle} */
    static SkillDefinitionRegistration noop() {
        return NoopHolder.INSTANCE;
    }

    @ApiStatus.Internal
    final class NoopHolder {
        private static final SkillDefinitionRegistration INSTANCE = () -> {
        };

        private NoopHolder() {
        }
    }
}
