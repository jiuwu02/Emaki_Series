package emaki.jiuwu.craft.dungeon.instance;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.dungeon.instance.EventIngress.IngressEvent;

public final class InstanceLoop implements AutoCloseable {

    private static final long TICK_MS = 50L;
    private static final long RECONCILE_TICKS = 100L;
    private static final long CLOSE_JOIN_MILLIS = 1000L;

    private final String instanceId;
    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final EventIngress ingress;
    private final WorldCommandQueue commands;
    private final Consumer<IngressEvent> eventConsumer;
    private final Runnable reconcile;
    private final Consumer<Throwable> crashHandler;

    private volatile boolean running;
    private volatile Thread thread;

    public InstanceLoop(String instanceId,
            Plugin plugin,
            ExecutionDispatcher dispatcher,
            EventIngress ingress,
            WorldCommandQueue commands,
            Consumer<IngressEvent> eventConsumer,
            Runnable reconcile,
            Consumer<Throwable> crashHandler) {
        this.instanceId = instanceId;
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.ingress = ingress;
        this.commands = commands;
        this.eventConsumer = eventConsumer;
        this.reconcile = reconcile;
        this.crashHandler = crashHandler;
    }

    public synchronized void start() {
        if (thread != null) {
            return;
        }
        running = true;
        thread = Thread.ofVirtual().name("emd-instance-" + instanceId).start(this::run);
    }

    public boolean isRunning() {
        return running;
    }

    private void run() {
        long tick = 0L;
        try {
            while (running) {
                IngressEvent event = ingress.poll(TICK_MS);
                if (event != null) {
                    dispatchEvent(event);
                }
                while ((event = ingress.poll()) != null) {
                    dispatchEvent(event);
                }
                dispatchCommands();
                tick++;
                if (tick % RECONCILE_TICKS == 0L) {
                    reconcile.run();
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Throwable throwable) {
            if (running && crashHandler != null) {
                crashHandler.accept(throwable);
            }
        } finally {
            running = false;
        }
    }

    private void dispatchEvent(IngressEvent event) {
        if (eventConsumer != null) {
            eventConsumer.accept(event);
        }
    }

    private void dispatchCommands() {
        List<WorldCommand> batch = commands.drain();
        if (batch.isEmpty()) {
            return;
        }
        for (WorldCommand command : batch) {
            if (!dispatch(command)) {
                command.retired();
            }
        }
    }

    private boolean dispatch(WorldCommand command) {
        return switch (command.target()) {
            case GLOBAL -> scheduleGlobal(command);
            case LOCATION -> scheduleLocation(command);
            case ENTITY -> scheduleEntity(command);
        };
    }

    private boolean scheduleGlobal(WorldCommand command) {
        return accept(dispatcher.runGlobal(plugin, () -> execute(command)));
    }

    private boolean scheduleLocation(WorldCommand command) {
        return accept(dispatcher.runGlobal(plugin, () -> {
            Location anchor = anchorOf(command);
            if (anchor == null) {
                execute(command);
                return;
            }
            TaskToken token = dispatcher.runAtLocation(plugin, anchor, () -> execute(command));
            if (!accept(token)) {
                command.retired();
            }
        }));
    }

    private boolean scheduleEntity(WorldCommand command) {
        UUID entityId = command.entityId();
        if (entityId == null) {
            return false;
        }
        return accept(dispatcher.runGlobal(plugin, () -> {
            Entity entity = Bukkit.getPlayer(entityId);
            if (entity == null) {
                command.retired();
                return;
            }
            TaskToken token =
                    dispatcher.runEntity(plugin, entity, () -> execute(command), command::retired);
            if (!accept(token)) {
                command.retired();
            }
        }));
    }

    private boolean accept(TaskToken token) {
        return token != null && !token.cancelled();
    }

    private void execute(WorldCommand command) {
        if (!running) {
            command.retired();
            return;
        }
        try {
            command.execute();
        } catch (Exception exception) {
            command.fail(exception);
        }
    }

    private Location anchorOf(WorldCommand command) {
        String worldName = command.worldName();
        World world = worldName == null || worldName.isBlank()
                ? Bukkit.getWorld(instanceId)
                : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double[] anchor = command.anchor();
        if (anchor == null || anchor.length < 3) {
            return world.getSpawnLocation();
        }
        return new Location(world, anchor[0], anchor[1], anchor[2]);
    }

    @Override
    public synchronized void close() {
        running = false;
        ingress.close();
        Thread current = thread;
        thread = null;
        if (current != null) {
            current.interrupt();
            try {
                current.join(CLOSE_JOIN_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
