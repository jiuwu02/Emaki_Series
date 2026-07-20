package emaki.jiuwu.craft.cooking.service.display;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PacketEventsCookingTextDisplayService implements CookingTextDisplayService, Listener {

    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(1_500_000_000);
    private static final int ENTITY_METADATA_BASE = 8;

    private final JavaPlugin plugin;
    private final CookingSettingsService settingsService;
    private final ExecutionDispatcher executionDispatcher;
    @SuppressWarnings("unused")
    private final ThreadOwnership threadOwnership;
    private final Map<String, VirtualText> displays = new LinkedHashMap<>();
    private final Map<String, Set<String>> displaysByStation = new LinkedHashMap<>();
    private final TaskHandle refreshTask;

    public PacketEventsCookingTextDisplayService(JavaPlugin plugin,
            CookingSettingsService settingsService,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.settingsService = settingsService;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        int interval = settingsService.displayEntitiesRefreshIntervalTicks();
        refreshTask = executionDispatcher.runGlobalTimer(plugin, this::refreshAll, interval, interval);
    }

    static boolean isRuntimeSupported() {
        return PacketEvents.getAPI()
                .getServerManager()
                .getVersion()
                .isNewerThanOrEquals(ServerVersion.V_1_19_4);
    }

    @Override
    public void upsert(CookingTextDisplaySpec spec) {
        if (spec == null) {
            return;
        }
        if (!spec.hasText() || spec.displayLocation() == null || spec.displayLocation().getWorld() == null) {
            remove(spec.stationType(), spec.stationCoordinates(), spec.displayKey());
            return;
        }
        String key = spec.runtimeKey();
        VirtualText display = displays.get(key);
        if (display == null) {
            display = new VirtualText(NEXT_ENTITY_ID.incrementAndGet(), UUID.randomUUID(), spec);
            displays.put(key, display);
            displaysByStation.computeIfAbsent(spec.stationRuntimeKey(), ignored -> new LinkedHashSet<>()).add(key);
            refreshEntry(display);
            return;
        }
        display.spec = spec;
        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(display.entityId, metadata(spec));
        for (UUID playerId : Set.copyOf(display.visiblePlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                sendPacket(player, packet);
            }
        }
        refreshEntry(display);
    }

    @Override
    public void remove(StationType stationType, StationCoordinates coordinates, String displayKey) {
        if (stationType == null || coordinates == null || displayKey == null) {
            return;
        }
        String stationKey = stationType.folderName() + ":" + coordinates.runtimeKey();
        removeKey(stationKey, stationKey + ":" + displayKey);
    }

    @Override
    public void removeStation(StationType stationType, StationCoordinates coordinates) {
        if (stationType == null || coordinates == null) {
            return;
        }
        removeStationKey(stationType.folderName() + ":" + coordinates.runtimeKey());
    }

    @Override
    public void removeStationType(StationType stationType) {
        if (stationType == null) {
            return;
        }
        String prefix = stationType.folderName() + ":";
        for (String stationKey : Set.copyOf(displaysByStation.keySet())) {
            if (stationKey.startsWith(prefix)) {
                removeStationKey(stationKey);
            }
        }
    }

    @Override
    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        HandlerList.unregisterAll(this);
        for (VirtualText display : Set.copyOf(displays.values())) {
            destroyForAllVisible(display);
        }
        displays.clear();
        displaysByStation.clear();
    }

    @Override
    public String backendName() {
        return "packet_events";
    }

    private void refreshAll() {
        for (VirtualText display : List.copyOf(displays.values())) {
            refreshEntry(display);
        }
    }

    private void refreshEntry(VirtualText display) {
        if (display == null || display.spec == null || !display.spec.hasText()) {
            return;
        }
        Set<UUID> onlinePlayers = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlinePlayers.add(player.getUniqueId());
            executionDispatcher.runEntity(plugin, player, () -> refreshVisibilityForPlayer(display, player), () ->
                    display.visiblePlayers.remove(player.getUniqueId()));
        }
        display.visiblePlayers.removeIf(playerId -> !onlinePlayers.contains(playerId));
    }

    private void refreshVisibilityForPlayer(VirtualText display, Player player) {
        if (display == null || player == null || !player.isOnline()) {
            return;
        }
        boolean visible = isVisible(player, display.spec);
        boolean alreadyVisible = display.visiblePlayers.contains(player.getUniqueId());
        if (visible && !alreadyVisible) {
            spawnFor(player, display);
            display.visiblePlayers.add(player.getUniqueId());
        } else if (!visible && alreadyVisible) {
            destroyFor(player, display);
            display.visiblePlayers.remove(player.getUniqueId());
        }
    }

    private boolean isVisible(Player player, CookingTextDisplaySpec spec) {
        org.bukkit.Location location = spec.displayLocation();
        if (player == null || location == null || location.getWorld() == null || player.getWorld() == null) {
            return false;
        }
        if (!player.getWorld().equals(location.getWorld())) {
            return false;
        }
        double distance = settingsService.displayEntitiesViewDistanceBlocks();
        return player.getLocation().distanceSquared(location) <= distance * distance;
    }

    private void spawnFor(Player player, VirtualText display) {
        org.bukkit.Location bukkitLocation = display.spec.displayLocation();
        if (bukkitLocation == null) {
            return;
        }
        Location packetLocation = new Location(bukkitLocation.getX(), bukkitLocation.getY(), bukkitLocation.getZ(), 0F, 0F);
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                display.entityId,
                display.uuid,
                EntityTypes.TEXT_DISPLAY,
                packetLocation,
                0F,
                0,
                Vector3d.zero()
        );
        sendPacket(player, spawnPacket);
        sendPacket(player, new WrapperPlayServerEntityMetadata(display.entityId, metadata(display.spec)));
    }

    private List<EntityData<?>> metadata(CookingTextDisplaySpec spec) {
        ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
        boolean hasPositionRotationInterpolation = version.isNewerThanOrEquals(ServerVersion.V_1_20_2);
        int translationIndex = ENTITY_METADATA_BASE + (hasPositionRotationInterpolation ? 3 : 2);
        int billboardIndex = ENTITY_METADATA_BASE + (hasPositionRotationInterpolation ? 7 : 6);
        int viewRangeIndex = ENTITY_METADATA_BASE + (hasPositionRotationInterpolation ? 9 : 8);
        int textIndex = ENTITY_METADATA_BASE + (hasPositionRotationInterpolation ? 15 : 14);

        CookingSettingsService.TextDisplayProfile profile = spec.profile();
        CookingSettingsService.Vector3 scale = profile.scale();

        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 1, EntityDataTypes.INT, 0));
        if (hasPositionRotationInterpolation) {
            metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 2, EntityDataTypes.INT, 0));
        }
        metadata.add(new EntityData<>(translationIndex, EntityDataTypes.VECTOR3F, Vector3f.zero()));
        metadata.add(new EntityData<>(translationIndex + 1, EntityDataTypes.VECTOR3F,
                new Vector3f((float) scale.x(), (float) scale.y(), (float) scale.z())));
        metadata.add(new EntityData<>(translationIndex + 2, EntityDataTypes.QUATERNION, new Quaternion4f(0F, 0F, 0F, 1F)));
        metadata.add(new EntityData<>(translationIndex + 3, EntityDataTypes.QUATERNION, new Quaternion4f(0F, 0F, 0F, 1F)));
        metadata.add(new EntityData<>(billboardIndex, EntityDataTypes.BYTE, billboardByte(profile.billboard())));
        metadata.add(new EntityData<>(viewRangeIndex, EntityDataTypes.FLOAT, (float) settingsService.displayEntitiesViewDistanceBlocks()));
        metadata.add(componentMetadata(textIndex, spec.componentObject()));
        metadata.add(new EntityData<>(textIndex + 1, EntityDataTypes.INT, profile.lineWidth()));
        metadata.add(new EntityData<>(textIndex + 2, EntityDataTypes.INT, profile.backgroundArgb()));
        metadata.add(new EntityData<>(textIndex + 3, EntityDataTypes.BYTE, (byte) 0xFF));
        metadata.add(new EntityData<>(textIndex + 4, EntityDataTypes.BYTE, styleFlags(profile)));
        return metadata;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private EntityData<?> componentMetadata(int index, Object component) {
        return new EntityData(index, EntityDataTypes.ADV_COMPONENT, component);
    }

    private byte billboardByte(String value) {
        return switch (value == null ? "center" : value) {
            case "fixed" -> (byte) 0;
            case "vertical" -> (byte) 1;
            case "horizontal" -> (byte) 2;
            default -> (byte) 3;
        };
    }

    private byte styleFlags(CookingSettingsService.TextDisplayProfile profile) {
        byte flags = 0;
        if (profile.shadow()) {
            flags |= 0x01;
        }
        if (profile.seeThrough()) {
            flags |= 0x02;
        }
        return flags;
    }

    private void destroyFor(Player player, VirtualText display) {
        sendPacket(player, new WrapperPlayServerDestroyEntities(display.entityId));
    }

    private void sendPacket(Player player, WrapperPlayServerSpawnEntity packet) {
        sendPacketInternal(player, () -> PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet));
    }

    private void sendPacket(Player player, WrapperPlayServerEntityMetadata packet) {
        sendPacketInternal(player, () -> PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet));
    }

    private void sendPacket(Player player, WrapperPlayServerDestroyEntities packet) {
        sendPacketInternal(player, () -> PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet));
    }

    private void sendPacketInternal(Player player, Runnable sender) {
        if (player == null || sender == null) {
            return;
        }
        executionDispatcher.runEntity(plugin, player, () -> {
            if (player.isOnline()) {
                sender.run();
            }
        }, () -> {
        });
    }

    private void destroyForAllVisible(VirtualText display) {
        for (UUID playerId : Set.copyOf(display.visiblePlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                destroyFor(player, display);
            }
        }
        display.visiblePlayers.clear();
    }

    private void removeStationKey(String stationKey) {
        Set<String> keys = displaysByStation.remove(stationKey);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : Set.copyOf(keys)) {
            VirtualText display = displays.remove(key);
            if (display != null) {
                destroyForAllVisible(display);
            }
        }
    }

    private void removeKey(String stationKey, String key) {
        VirtualText display = displays.remove(key);
        if (display != null) {
            destroyForAllVisible(display);
        }
        Set<String> stationKeys = displaysByStation.get(stationKey);
        if (stationKeys == null) {
            return;
        }
        stationKeys.remove(key);
        if (stationKeys.isEmpty()) {
            displaysByStation.remove(stationKey);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleRefresh();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        for (VirtualText display : displays.values()) {
            display.visiblePlayers.remove(playerId);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        scheduleRefresh();
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        executionDispatcher.runGlobal(plugin, this::refreshAll);
    }

    private static final class VirtualText {

        private final int entityId;
        private final UUID uuid;
        private final Set<UUID> visiblePlayers = new LinkedHashSet<>();
        private CookingTextDisplaySpec spec;

        private VirtualText(int entityId, UUID uuid, CookingTextDisplaySpec spec) {
            this.entityId = entityId;
            this.uuid = uuid;
            this.spec = spec;
        }
    }
}
