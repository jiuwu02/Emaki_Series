package emaki.jiuwu.craft.attribute.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.async.TaskHandle;

class ResourceManagementServiceTest {

    @Test
    void globalResyncDispatchesBeforeTouchingPlayerState() {
        Fixture fixture = new Fixture();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
                MockedStatic<FoliaSchedulerAdapter> scheduler = org.mockito.Mockito.mockStatic(FoliaSchedulerAdapter.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(fixture.player));
            scheduler.when(() -> FoliaSchedulerAdapter.runEntityTask(
                    eq(fixture.plugin),
                    eq(fixture.player),
                    any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        scheduled.set(invocation.getArgument(2));
                        return fixture.taskHandle;
                    });

            fixture.management.resyncAllPlayers();

            verify(fixture.service, never()).collectCombatSnapshot(fixture.player);
            scheduled.get().run();
            verify(fixture.service).collectCombatSnapshot(fixture.player);
        }
    }

    @Test
    void globalHealthScaleResetDispatchesBeforeTouchingPlayerState() {
        Fixture fixture = new Fixture();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
                MockedStatic<FoliaSchedulerAdapter> scheduler = org.mockito.Mockito.mockStatic(FoliaSchedulerAdapter.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(fixture.player));
            scheduler.when(() -> FoliaSchedulerAdapter.runEntityTask(
                    eq(fixture.plugin),
                    eq(fixture.player),
                    any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        scheduled.set(invocation.getArgument(2));
                        return fixture.taskHandle;
                    });

            fixture.management.resetHealthDisplayScaling();

            verify(fixture.player, never()).setHealthScaled(false);
            scheduled.get().run();
            verify(fixture.player).setHealthScaled(false);
        }
    }

    @Test
    void globalRegenerationDispatchesBeforeCollectingSnapshot() {
        Fixture fixture = new Fixture();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
                MockedStatic<FoliaSchedulerAdapter> scheduler = org.mockito.Mockito.mockStatic(FoliaSchedulerAdapter.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(fixture.player));
            scheduler.when(() -> FoliaSchedulerAdapter.runEntityTask(
                    eq(fixture.plugin),
                    eq(fixture.player),
                    any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        scheduled.set(invocation.getArgument(2));
                        return fixture.taskHandle;
                    });

            fixture.management.regenerateOnlinePlayers();

            verify(fixture.service, never()).collectCombatSnapshot(fixture.player);
            scheduled.get().run();
            verify(fixture.service).collectCombatSnapshot(fixture.player);
        }
    }

    @Test
    void retiredEquipmentTaskReleasesPendingMarker() {
        Fixture fixture = new Fixture();
        AtomicReference<Runnable> retired = new AtomicReference<>();
        try (MockedStatic<FoliaSchedulerAdapter> scheduler = org.mockito.Mockito.mockStatic(FoliaSchedulerAdapter.class)) {
            scheduler.when(() -> FoliaSchedulerAdapter.runEntityTaskLater(
                    eq(fixture.plugin),
                    eq(fixture.player),
                    any(Runnable.class),
                    any(Runnable.class),
                    anyLong()))
                    .thenAnswer(invocation -> {
                        retired.set(invocation.getArgument(3));
                        return fixture.taskHandle;
                    });

            fixture.management.scheduleEquipmentSync(fixture.player);
            fixture.management.scheduleEquipmentSync(fixture.player);
            scheduler.verify(() -> FoliaSchedulerAdapter.runEntityTaskLater(
                    eq(fixture.plugin),
                    eq(fixture.player),
                    any(Runnable.class),
                    any(Runnable.class),
                    anyLong()), times(1));

            retired.get().run();
            fixture.management.scheduleEquipmentSync(fixture.player);
            scheduler.verify(() -> FoliaSchedulerAdapter.runEntityTaskLater(
                    eq(fixture.plugin),
                    eq(fixture.player),
                    any(Runnable.class),
                    any(Runnable.class),
                    anyLong()), times(2));
        }
    }

    @Test
    void rejectedEquipmentTaskReleasesPendingMarker() {
        Fixture fixture = new Fixture();
        try (MockedStatic<FoliaSchedulerAdapter> scheduler = org.mockito.Mockito.mockStatic(FoliaSchedulerAdapter.class)) {
            scheduler.when(() -> FoliaSchedulerAdapter.runEntityTaskLater(
                    eq(fixture.plugin),
                    eq(fixture.player),
                    any(Runnable.class),
                    any(Runnable.class),
                    anyLong()))
                    .thenReturn(null);

            fixture.management.scheduleEquipmentSync(fixture.player);
            fixture.management.scheduleEquipmentSync(fixture.player);

            scheduler.verify(() -> FoliaSchedulerAdapter.runEntityTaskLater(
                    eq(fixture.plugin),
                    eq(fixture.player),
                    any(Runnable.class),
                    any(Runnable.class),
                    anyLong()), times(2));
        }
    }

    @Test
    void delayedPlayerCallbackUsesCapturedOwnerWithoutGlobalLookup() {
        Fixture fixture = new Fixture();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
                MockedStatic<FoliaSchedulerAdapter> scheduler = org.mockito.Mockito.mockStatic(FoliaSchedulerAdapter.class)) {
            scheduler.when(() -> FoliaSchedulerAdapter.runEntityTaskLater(
                    eq(fixture.plugin),
                    eq(fixture.player),
                    any(Runnable.class),
                    anyLong()))
                    .thenAnswer(invocation -> {
                        scheduled.set(invocation.getArgument(2));
                        return fixture.taskHandle;
                    });

            fixture.management.scheduleRespawnHealthSync(fixture.player);
            scheduled.get().run();

            bukkit.verify(() -> Bukkit.getPlayer(any(UUID.class)), never());
            verify(fixture.service).collectCombatSnapshot(fixture.player);
        }
    }

    @Test
    void delayedLivingEntityCallbackUsesCapturedOwnerWithoutGlobalLookup() {
        Fixture fixture = new Fixture();
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.isValid()).thenReturn(true);
        when(entity.isDead()).thenReturn(false);
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
                MockedStatic<FoliaSchedulerAdapter> scheduler = org.mockito.Mockito.mockStatic(FoliaSchedulerAdapter.class)) {
            scheduler.when(() -> FoliaSchedulerAdapter.runEntityTaskLater(
                    eq(fixture.plugin),
                    eq(entity),
                    any(Runnable.class),
                    anyLong()))
                    .thenAnswer(invocation -> {
                        scheduled.set(invocation.getArgument(2));
                        return fixture.taskHandle;
                    });

            fixture.management.scheduleLivingEntitySync(entity);
            scheduled.get().run();

            bukkit.verify(() -> Bukkit.getEntity(any(UUID.class)), never());
            verify(fixture.service).collectCombatSnapshot(entity);
        }
    }

    private static final class Fixture {

        private final AttributeService service = mock(AttributeService.class);
        private final EmakiAttributePlugin plugin = mock(EmakiAttributePlugin.class);
        private final AttributeConfig config = mock(AttributeConfig.class);
        private final AttributeSnapshot snapshot = mock(AttributeSnapshot.class);
        private final AttributeStateRepository stateRepository = mock(AttributeStateRepository.class);
        private final AttributeRegistryService registryService = mock(AttributeRegistryService.class);
        private final VanillaAttributeSynchronizer vanillaSynchronizer = mock(VanillaAttributeSynchronizer.class);
        private final Player player = mock(Player.class);
        private final TaskHandle taskHandle = mock(TaskHandle.class);
        private final ResourceManagementService management;

        private Fixture() {
            UUID playerId = UUID.randomUUID();
            when(service.plugin()).thenReturn(plugin);
            when(service.config()).thenReturn(config);
            when(service.resourceDefinitions()).thenReturn(Map.of());
            when(service.collectCombatSnapshot(any(LivingEntity.class))).thenReturn(snapshot);
            when(service.stateRepository()).thenReturn(stateRepository);
            when(service.registryService()).thenReturn(registryService);
            when(service.vanillaSynchronizer()).thenReturn(vanillaSynchronizer);
            when(config.regenIntervalTicks()).thenReturn(20);
            when(config.syncDelayTicks()).thenReturn(1);
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.isOnline()).thenReturn(true);
            when(player.isValid()).thenReturn(true);
            management = new ResourceManagementService(service);
        }
    }
}
