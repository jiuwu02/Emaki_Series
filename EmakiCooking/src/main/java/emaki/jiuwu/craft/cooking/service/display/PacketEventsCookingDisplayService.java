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
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

public final class PacketEventsCookingDisplayService implements CookingDisplayService {

    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(1_000_000_000);
    private static final int ENTITY_METADATA_BASE = 8;

    private final JavaPlugin plugin;
    private final CookingSettingsService settingsService;
    private final Map<String, VirtualDisplay> displays = new LinkedHashMap<>();
    private final Map<String, Set<String>> displaysByStation = new LinkedHashMap<>();
    private final DisplayVisibilityListener listener = new DisplayVisibilityListener();
    private final BukkitTask refreshTask;

    public PacketEventsCookingDisplayService(JavaPlugin plugin, CookingSettingsService settingsService) {
        this.plugin = plugin;
        this.settingsService = settingsService;
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        int interval = settingsService.displayEntitiesRefreshIntervalTicks();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, interval, interval);
    }

    static boolean isRuntimeSupported() {
        return PacketEvents.getAPI()
                .getServerManager()
                .getVersion()
                .isNewerThanOrEquals(ServerVersion.V_1_19_4);
    }

    @Override
    public void upsert(CookingDisplaySpec spec) {
        if (!isValidSpec(spec)) {
            return;
        }
        String key = spec.runtimeKey();
        VirtualDisplay display = displays.get(key);
        if (display == null) {
            display = new VirtualDisplay(NEXT_ENTITY_ID.incrementAndGet(), UUID.randomUUID(), spec);
            displays.put(key, display);
            displaysByStation.computeIfAbsent(spec.stationRuntimeKey(), ignored -> new LinkedHashSet<>()).add(key);
        } else {
            destroyForAllVisible(display);
            display.spec = spec;
        }
        refreshEntry(display);
    }

    @Override
    public void remove(StationType stationType, StationCoordinates coordinates, String displayKey) {
        if (stationType == null || coordinates == null || displayKey == null) {
            return;
        }
        String stationKey = stationType.folderName() + ":" + coordinates.runtimeKey();
        String key = stationKey + ":" + displayKey;
        removeKey(stationKey, key);
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
        refreshTask.cancel();
        HandlerList.unregisterAll(listener);
        for (VirtualDisplay display : Set.copyOf(displays.values())) {
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
        for (VirtualDisplay display : List.copyOf(displays.values())) {
            refreshEntry(display);
        }
    }

    private void refreshEntry(VirtualDisplay display) {
        if (display == null || !isValidSpec(display.spec)) {
            return;
        }
        Set<UUID> onlinePlayers = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlinePlayers.add(player.getUniqueId());
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
        display.visiblePlayers.removeIf(playerId -> !onlinePlayers.contains(playerId));
    }

    private boolean isVisible(Player player, CookingDisplaySpec spec) {
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

    private void spawnFor(Player player, VirtualDisplay display) {
        org.bukkit.Location bukkitLocation = display.spec.displayLocation();
        if (bukkitLocation == null) {
            return;
        }
        Location packetLocation = new Location(
                bukkitLocation.getX(),
                bukkitLocation.getY(),
                bukkitLocation.getZ(),
                0F,
                0F
        );
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                display.entityId,
                display.uuid,
                EntityTypes.ITEM_DISPLAY,
                packetLocation,
                0F,
                0,
                Vector3d.zero()
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, metadataPacket(display));
    }

    private WrapperPlayServerEntityMetadata metadataPacket(VirtualDisplay display) {
        return new WrapperPlayServerEntityMetadata(display.entityId, metadata(display.spec));
    }

    private List<EntityData<?>> metadata(CookingDisplaySpec spec) {
        ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
        boolean hasPositionRotationInterpolation = version.isNewerThanOrEquals(ServerVersion.V_1_20_2);
        int translationIndex = hasPositionRotationInterpolation ? ENTITY_METADATA_BASE + 3 : ENTITY_METADATA_BASE + 2;
        int viewRangeIndex = hasPositionRotationInterpolation ? ENTITY_METADATA_BASE + 9 : ENTITY_METADATA_BASE + 8;
        int itemStackIndex = hasPositionRotationInterpolation ? ENTITY_METADATA_BASE + 15 : ENTITY_METADATA_BASE + 14;

        Transformation transformation = spec.transformation();
        org.joml.Vector3f scale = transformation.getScale();
        org.joml.Quaternionf leftRotation = transformation.getLeftRotation();
        org.joml.Quaternionf rightRotation = transformation.getRightRotation();

        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 1, EntityDataTypes.INT, 0));
        if (hasPositionRotationInterpolation) {
            metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 2, EntityDataTypes.INT, 0));
        }
        metadata.add(new EntityData<>(translationIndex, EntityDataTypes.VECTOR3F, Vector3f.zero()));
        metadata.add(new EntityData<>(translationIndex + 1, EntityDataTypes.VECTOR3F, new Vector3f(scale.x(), scale.y(), scale.z())));
        metadata.add(new EntityData<>(translationIndex + 2, EntityDataTypes.QUATERNION, quaternion(leftRotation)));
        metadata.add(new EntityData<>(translationIndex + 3, EntityDataTypes.QUATERNION, quaternion(rightRotation)));
        metadata.add(new EntityData<>(viewRangeIndex, EntityDataTypes.FLOAT, (float) settingsService.displayEntitiesViewDistanceBlocks()));
        metadata.add(new EntityData<>(itemStackIndex, EntityDataTypes.ITEMSTACK, packetItemStack(spec.itemStack())));
        metadata.add(new EntityData<>(itemStackIndex + 1, EntityDataTypes.BYTE, (byte) 0));
        return metadata;
    }

    private Quaternion4f quaternion(org.joml.Quaternionf quaternion) {
        return new Quaternion4f(quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w());
    }

    private com.github.retrooper.packetevents.protocol.item.ItemStack packetItemStack(ItemStack itemStack) {
        ItemStack clone = itemStack.clone();
        clone.setAmount(1);
        return SpigotConversionUtil.fromBukkitItemStack(clone);
    }

    private void destroyFor(Player player, VirtualDisplay display) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(display.entityId));
    }

    private void destroyForAllVisible(VirtualDisplay display) {
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
            VirtualDisplay display = displays.remove(key);
            if (display != null) {
                destroyForAllVisible(display);
            }
        }
    }

    private void removeKey(String stationKey, String key) {
        VirtualDisplay display = displays.remove(key);
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

    private boolean isValidSpec(CookingDisplaySpec spec) {
        return spec != null
                && spec.itemStack() != null
                && !spec.itemStack().getType().isAir()
                && spec.baseLocation().getWorld() != null
                && spec.displayLocation() != null
                && spec.displayLocation().getWorld() != null;
    }

    private final class DisplayVisibilityListener implements Listener {

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            scheduleRefresh();
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            UUID playerId = event.getPlayer().getUniqueId();
            for (VirtualDisplay display : displays.values()) {
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
            Bukkit.getScheduler().runTask(plugin, PacketEventsCookingDisplayService.this::refreshAll);
        }
    }

    private static final class VirtualDisplay {

        private final int entityId;
        private final UUID uuid;
        private final Set<UUID> visiblePlayers = new LinkedHashSet<>();
        private CookingDisplaySpec spec;

        private VirtualDisplay(int entityId, UUID uuid, CookingDisplaySpec spec) {
            this.entityId = entityId;
            this.uuid = uuid;
            this.spec = spec;
        }
    }
}
