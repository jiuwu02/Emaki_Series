package emaki.jiuwu.craft.corelib.api.world;

import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime bridge that mounts, saves and unmounts worlds on servers whose
 * {@code CraftServer.createWorld} is an unsupported-operation stub (Folia), where the plain
 * Bukkit world API refuses to create worlds after startup.
 *
 * <p>Consumers obtain the implementation reflectively: load
 * {@code emaki.jiuwu.craft.nms.WorldMountSupportImpl} with {@code Class.forName}, instantiate it
 * through its no-argument constructor and cast the result to this interface. The implementation is
 * delivered by EmakiCoreLib as the runtime library {@code emaki.jiuwu.craft:emakinms}, is never
 * shaded into a plugin jar, and must not be referenced at compile time beyond this interface.</p>
 *
 * <p><strong>Threading on Folia:</strong> {@link #create(WorldCreator)} and
 * {@link #unload(World, boolean)} must be started from the global region thread or from an async
 * thread. A call issued from a region tick thread is rejected with an exception, and a call issued
 * while the server is shutting down is rejected as well, because the shutdown save owns the world
 * at that point. {@link #save(World, Runnable)} may be called from any thread; the callback runs
 * after the world data has been flushed, on a thread that is not guaranteed to be a Bukkit owner
 * thread, so consumers that touch Bukkit state inside the callback must reschedule themselves.</p>
 *
 * <p><strong>Threading elsewhere:</strong> on non-Folia servers the three methods are literal
 * equivalents of {@code Bukkit.createWorld(creator)}, {@code Bukkit.unloadWorld(world, save)} and
 * {@code world.save()}, and keep the platform's own thread contract.</p>
 */
public interface WorldMountSupport {

    /**
     * Creates the world described by {@code creator} and registers it with the server.
     *
     * <p>When a world with the same name or key already exists, that world is returned unchanged.
     * {@code null} is returned when the platform refused to create the world, matching
     * {@code Bukkit.createWorld} semantics.</p>
     *
     * @param creator world definition, never {@code null}
     * @return the mounted world, or {@code null} when creation was refused
     * @throws IllegalStateException when called before the server finished loading its first
     *                               worlds, from a region tick thread on Folia, or on a server
     *                               version the bridge has no assembly sequence for
     * @throws IllegalArgumentException when {@code creator} is {@code null} or names an
     *                                  unsupported environment
     */
    @Nullable World create(@NotNull WorldCreator creator);

    /**
     * Unloads {@code world} and detaches it from the server.
     *
     * <p>On Folia the bridge first takes ownership of every ticking region (with a bounded wait),
     * so no region keeps ticking a world that is being closed; when ownership cannot be taken in
     * time the world stays loaded and the call reports {@code false}. The call also reports
     * {@code false} when the world is the overworld, when players are still inside, when the
     * {@code WorldUnloadEvent} is cancelled, or when the server is shutting down.</p>
     *
     * @param world world to unload
     * @param save  {@code true} to flush world data before closing its storage
     * @return {@code true} when the world was unloaded
     */
    boolean unload(@NotNull World world, boolean save);

    /**
     * Flushes the world data of {@code world}, then runs {@code onSaved}.
     *
     * @param world   world to save
     * @param onSaved callback executed after the flush completes; its thread is unspecified
     */
    void save(@NotNull World world, @NotNull Runnable onSaved);
}
