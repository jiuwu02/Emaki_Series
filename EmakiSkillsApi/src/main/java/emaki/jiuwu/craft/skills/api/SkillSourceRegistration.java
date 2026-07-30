package emaki.jiuwu.craft.skills.api;

import org.jetbrains.annotations.ApiStatus;

/** Closeable handle for one external skill-source registration. */
@ApiStatus.NonExtendable
@FunctionalInterface
public interface SkillSourceRegistration extends AutoCloseable {

    /** Releases this registration. Repeated closes and superseded handles have no effect. */
    @Override
    void close();

    /** Reusable inactive registration handle. */
    static SkillSourceRegistration noop() {
        return NoopHolder.INSTANCE;
    }

    final class NoopHolder {
        private static final SkillSourceRegistration INSTANCE = () -> {
        };

        private NoopHolder() {
        }
    }
}
