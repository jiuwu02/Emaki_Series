package emaki.jiuwu.craft.dungeon.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Facade for entering dungeons and inspecting their live instances.
 */
public interface DungeonApi {

    /**
     * Requests entry into a dungeon for {@code player}, starting the
     * challenge flow when the request passes limits, permission, and capacity
     * checks.
     *
     * <p>On success the value is a {@link ChallengeId} while no instance
     * world has been assigned yet, and an {@link InstanceId} once one has.
     * Denials are reported as a {@link Result} with an {@link ErrorCodes}
     * identifier rather than thrown.
     *
     * @param player the player requesting entry; must be online
     * @param dungeonId the id of the dungeon to enter
     * {@return the outcome of the entry request; never {@code null}}
     */
    @NotNull Result<Object> enter(@NotNull Player player, @NotNull String dungeonId);

    /**
     * {@return a snapshot of every live instance; never {@code null}, and
     * empty when no instance exists}
     */
    @NotNull List<InstanceView> listInstances();

    /**
     * {@return the view of the requested instance, or empty when no instance
     * with that id is registered}
     *
     * @param instanceId the instance id, equal to its world name
     */
    @NotNull Optional<InstanceView> getInstance(@NotNull UUID instanceId);
}
