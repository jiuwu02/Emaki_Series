package emaki.jiuwu.craft.dungeon.api;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;

/**
 * Facade for administering live dungeon instances.
 */
public interface InstanceApi {

    /**
     * Forces the instance to tear down and dispose immediately, bypassing the
     * normal settlement flow.
     *
     * @param instanceId the instance to stop
     * {@return the outcome; fails with {@code INSTANCE_NOT_FOUND} when no
     * such instance exists, or {@code ALREADY_DISPOSING} when it is already
     * shutting down}
     */
    @NotNull Result<Void> forceStop(@NotNull UUID instanceId);
}
