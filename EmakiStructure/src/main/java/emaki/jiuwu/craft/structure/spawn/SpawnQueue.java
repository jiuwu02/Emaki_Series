package emaki.jiuwu.craft.structure.spawn;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class SpawnQueue implements Runnable {

    private record Entry(Location location, SpawnAction action) {
    }

    private final EmakiStructurePlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final Supplier<Integer> batchPerTick;
    private final Deque<Entry> queue = new ArrayDeque<>();

    private TaskToken token;

    public SpawnQueue(EmakiStructurePlugin plugin,
            ExecutionDispatcher dispatcher,
            Supplier<Integer> batchPerTick) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.batchPerTick = batchPerTick;
    }

    public void start() {
        if (token != null) {
            return;
        }
        token = dispatcher.runGlobalTimer(plugin, this, 1L, 1L);
    }

    public void submit(Location location, SpawnAction action) {
        synchronized (queue) {
            queue.addLast(new Entry(location, action));
        }
    }

    @Override
    public void run() {
        int budget = Math.max(1, batchPerTick.get());
        while (budget > 0) {
            Entry entry;
            synchronized (queue) {
                entry = queue.poll();
            }
            if (entry == null) {
                return;
            }
            budget--;
            dispatch(entry);
        }
    }

    private void dispatch(Entry entry) {
        try {
            TaskToken submitted = dispatcher.runAtLocation(plugin, entry.location(), () -> execute(entry));
            if (submitted == null || submitted == TaskToken.UNAVAILABLE) {
                plugin.getLogger().warning("生物生成批次提交失败，坐标 "
                        + entry.location().getBlockX() + "," + entry.location().getBlockY()
                        + "," + entry.location().getBlockZ());
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "生物生成批次提交异常", exception);
        }
    }

    private void execute(Entry entry) {
        try {
            entry.action().run();
        } catch (LinkageError error) {
            plugin.getLogger().warning("软依赖生物生成入口不可用: " + error.getMessage());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "生物生成异常", exception);
        }
    }

    public int pendingCount() {
        synchronized (queue) {
            return queue.size();
        }
    }

    public void close() {
        if (token != null) {
            token.cancel();
            token = null;
        }
        synchronized (queue) {
            queue.clear();
        }
    }
}
