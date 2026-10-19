package emaki.jiuwu.craft.dungeon.instance;

import java.util.List;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.dungeon.instance.EventIngress.IngressEvent;

public final class InstanceLoop implements AutoCloseable {

    private static final long TICK_MS = 50L;
    private static final long RECONCILE_TICKS = 100L;

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
        long tick = 0;
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
                if (++tick % RECONCILE_TICKS == 0) {
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
            dispatcher.runGlobal(plugin, () -> {
                try {
                    command.execute();
                } catch (Throwable throwable) {
                    command.fail(throwable);
                }
            });
        }
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
                current.join(1000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
