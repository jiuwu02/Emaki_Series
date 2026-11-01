package emaki.jiuwu.craft.dungeon.guard;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.InstanceState;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;

public final class BoundaryGuard {

    private static final long SWEEP_PERIOD_TICKS = 20L;

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final InstanceRegistry registry;
    private final TeleportGuard teleportGuard;
    private final MessageService messages;
    private TaskToken sweepTask = TaskToken.UNAVAILABLE;

    public BoundaryGuard(Plugin plugin,
            ExecutionDispatcher dispatcher,
            InstanceRegistry registry,
            TeleportGuard teleportGuard,
            MessageService messages) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.teleportGuard = teleportGuard;
        this.messages = messages;
    }

    public void start() {
        sweepTask = dispatcher.runGlobalTimer(plugin, this::sweep, SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
    }

    public void shutdown() {
        sweepTask.cancel();
    }

    private void sweep() {
        for (DungeonInstance instance : registry.all()) {
            if (instance.state() != InstanceState.RUNNING) {
                continue;
            }
            World world = Bukkit.getWorld(instance.instanceId());
            if (world == null) {
                continue;
            }
            for (Player player : world.getPlayers()) {
                Location location = player.getLocation();
                if (world.getWorldBorder().isInside(location)) {
                    continue;
                }
                teleportGuard.permit(player.getUniqueId());
                player.teleport(world.getSpawnLocation());
                messages.send(player, "guard.boundary_pullback", Map.of());
            }
        }
    }
}
