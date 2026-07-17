package emaki.jiuwu.craft.corelib.async;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

class FoliaSchedulerCompatTest {

    @Test
    void forwardsRetiredCallbackToEntityScheduler() {
        Fixture fixture = new Fixture();
        AtomicReference<Consumer<ScheduledTask>> scheduled = new AtomicReference<>();
        AtomicReference<Runnable> retired = new AtomicReference<>();
        when(fixture.entityScheduler.runDelayed(
                eq(fixture.plugin),
                any(),
                any(Runnable.class),
                eq(1L)))
                .thenAnswer(invocation -> {
                    scheduled.set(invocation.getArgument(1));
                    retired.set(invocation.getArgument(2));
                    return fixture.scheduledTask;
                });

        Runnable task = mock(Runnable.class);
        Runnable retiredCallback = mock(Runnable.class);
        SchedulerCompat compat = fixture.compat();

        TaskHandle handle = compat.runEntityTaskLater(
                fixture.plugin,
                fixture.entity,
                task,
                retiredCallback,
                0L);

        assertNotNull(handle);
        assertSame(retiredCallback, retired.get());
        scheduled.get().accept(fixture.scheduledTask);
        verify(task).run();
        retired.get().run();
        verify(retiredCallback).run();
    }

    @Test
    void oldOverloadDelegatesWithoutRetiredCallback() {
        Fixture fixture = new Fixture();
        when(fixture.entityScheduler.runDelayed(
                eq(fixture.plugin),
                any(),
                isNull(),
                eq(4L)))
                .thenReturn(fixture.scheduledTask);

        SchedulerCompat compat = fixture.compat();
        TaskHandle handle = compat.runEntityTaskLater(
                fixture.plugin,
                fixture.entity,
                () -> {
                },
                4L);

        assertNotNull(handle);
        verify(fixture.entityScheduler).runDelayed(
                eq(fixture.plugin),
                any(),
                isNull(),
                eq(4L));
    }

    private static final class Fixture {

        private final Server server = mock(Server.class);
        private final Plugin plugin = mock(Plugin.class);
        private final Entity entity = mock(Entity.class);
        private final GlobalRegionScheduler globalScheduler = mock(GlobalRegionScheduler.class);
        private final RegionScheduler regionScheduler = mock(RegionScheduler.class);
        private final AsyncScheduler asyncScheduler = mock(AsyncScheduler.class);
        private final EntityScheduler entityScheduler = mock(EntityScheduler.class);
        private final ScheduledTask scheduledTask = mock(ScheduledTask.class);

        private Fixture() {
            when(server.getGlobalRegionScheduler()).thenReturn(globalScheduler);
            when(server.getRegionScheduler()).thenReturn(regionScheduler);
            when(server.getAsyncScheduler()).thenReturn(asyncScheduler);
            when(plugin.isEnabled()).thenReturn(true);
            when(entity.getScheduler()).thenReturn(entityScheduler);
        }

        private SchedulerCompat compat() {
            SchedulerCompat compat = FoliaSchedulerCompat.createIfSupported(server, true);
            assertNotNull(compat);
            return compat;
        }
    }
}
