package emaki.jiuwu.craft.corelib.gui;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;

final class AsyncGuiRenderer {

    private final AsyncTaskScheduler scheduler;
    private final PerformanceMonitor performanceMonitor;

    AsyncGuiRenderer(AsyncTaskScheduler scheduler, PerformanceMonitor performanceMonitor) {
        this.scheduler = scheduler;
        this.performanceMonitor = performanceMonitor;
    }

    CompletableFuture<Map<Integer, org.bukkit.inventory.ItemStack>> prepare(GuiSession session) {
        if (session == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        if (scheduler == null) {
            return CompletableFuture.completedFuture(render(session));
        }
        return scheduler.supplyAsync(
                "gui-render:" + session.template().id(),
                AsyncTaskScheduler.TaskPriority.NORMAL,
                5_000L,
                () -> render(session)
        );
    }

    private Map<Integer, org.bukkit.inventory.ItemStack> render(GuiSession session) {
        if (performanceMonitor == null) {
            return session.renderSlots();
        }
        return performanceMonitor.measure("gui-render:" + session.template().id(), session::renderSlots);
    }
}
