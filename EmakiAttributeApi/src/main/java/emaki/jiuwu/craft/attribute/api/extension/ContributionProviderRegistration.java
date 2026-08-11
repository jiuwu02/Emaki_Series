package emaki.jiuwu.craft.attribute.api.extension;

import org.jetbrains.annotations.ApiStatus;

/** Closeable handle for an attribute contribution provider registration. */
@ApiStatus.NonExtendable
@FunctionalInterface
public interface ContributionProviderRegistration extends AutoCloseable {

    /** Releases this registration. Repeated closes and superseded handles have no effect. */
    @Override
    void close();

    /** {@return a reusable inactive registration handle} */
    static ContributionProviderRegistration noop() {
        return NoopHolder.INSTANCE;
    }

    final class NoopHolder {
        private static final ContributionProviderRegistration INSTANCE = () -> {
        };

        private NoopHolder() {
        }
    }
}
