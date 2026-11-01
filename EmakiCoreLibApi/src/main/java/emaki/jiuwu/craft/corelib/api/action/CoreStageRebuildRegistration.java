package emaki.jiuwu.craft.corelib.api.action;

import org.jetbrains.annotations.NotNull;

/** Revocable registration for one independent stage-registry rebuild callback. */
public interface CoreStageRebuildRegistration extends AutoCloseable {

    /** {@return whether this callback is still registered} */
    boolean active();

    /** Removes this callback. Repeated calls are no-ops. */
    @Override
    void close();

    /** {@return an inactive registration used when CoreLib cannot accept the callback} */
    static @NotNull CoreStageRebuildRegistration inactive() {
        return Inactive.INSTANCE;
    }

    enum Inactive implements CoreStageRebuildRegistration {
        INSTANCE;

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public void close() {
        }
    }
}
