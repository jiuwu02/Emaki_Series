package emaki.jiuwu.craft.corelib.display.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.display.DisplayGeometry;
import emaki.jiuwu.craft.corelib.display.DisplayKey;
import emaki.jiuwu.craft.corelib.display.DisplayLifetimeTracker;
import emaki.jiuwu.craft.corelib.display.DisplayMotionRunner;
import emaki.jiuwu.craft.corelib.display.DisplayRuntimeSettings;
import emaki.jiuwu.craft.corelib.display.TextDisplayService;
import emaki.jiuwu.craft.corelib.display.TextDisplaySpec;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.packet.VirtualEntityIds;

public final class PacketTextDisplayService implements TextDisplayService, Listener {

    private static final int ENTITY_METADATA_BASE = 8;

    private final Plugin plugin;
    private final DisplayRuntimeSettings settings;
    private final ExecutionDispatcher executionDispatcher;
    private final DisplayMotionRunner motionRunner;
    private final Map<String, VirtualText> displays = new ConcurrentHashMap<>();
    private final DisplayLifetimeTracker lifetime;
    private final TaskToken refreshTask;

    public PacketTextDisplayService(Plugin plugin,
            DisplayRuntimeSettings settings,
            ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.settings = settings;
        this.executionDispatcher = executionDispatcher;
        this.motionRunner = new DisplayMotionRunner(plugin, executionDispatcher);
        this.lifetime = new DisplayLifetimeTracker(plugin, executionDispatcher, this::removeKey);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        int interval = Math.max(1, settings.refreshIntervalTicks());
        this.refreshTask = executionDispatcher.runGlobalTimer(plugin, this::refreshAll, interval, interval);
    }

    @Override
    public void upsert(TextDisplaySpec spec) {
        if (spec == null || !DisplayKey.isValid(spec.key())) {
            return;
        }
        if (!spec.hasText() || spec.displayLocation() == null || spec.displayLocation().getWorld() == null) {
            remove(spec.key());
            return;
        }
        String key = spec.runtimeKey();
        VirtualText display = displays.get(key);
        if (display == null) {
            display = new VirtualText(VirtualEntityIds.next(), UUID.randomUUID(), spec);
            displays.put(key, display);
            lifetime.trackGroupMember(spec.groupKey(), key);

            startMotion(key, display);
            refreshEntry(display);
            lifetime.scheduleExpiry(spec);
            return;
        }
        display.spec = spec;

        startMotion(key, display);
        WrapperPlayServerEntityMetadata packet =
                new WrapperPlayServerEntityMetadata(display.entityId, metadata(display));
        for (UUID playerId : Set.copyOf(display.visiblePlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                sendPacket(player, packet);
            }
        }
        refreshEntry(display);
        lifetime.scheduleExpiry(spec);
    }

    @Override
    public void remove(DisplayKey key) {
        if (!DisplayKey.isValid(key)) {
            return;
        }
        removeKey(key.groupKey(), key.runtimeKey());
    }

    @Override
    public void removeGroup(String namespace, String group) {
        if (namespace == null || group == null) {
            return;
        }
        removeGroupKey(namespace + ":" + group);
    }

    @Override
    public void removeGroupPrefix(String namespace, String groupPrefix) {
        if (namespace == null || groupPrefix == null) {
            return;
        }
        String prefix = namespace + ":" + groupPrefix;
        for (String groupKey : lifetime.groupKeys()) {
            if (groupKey.startsWith(prefix)) {
                removeGroupKey(groupKey);
            }
        }
    }

    @Override
    public void removeNamespace(String namespace) {
        if (namespace == null) {
            return;
        }
        String prefix = namespace + ":";
        for (String groupKey : lifetime.groupKeys()) {
            if (groupKey.startsWith(prefix)) {
                removeGroupKey(groupKey);
            }
        }
    }

    @Override
    public void shutdown() {
        lifetime.cancelAllExpiry();
        motionRunner.shutdown();
        lifetime.cancelQuietly(refreshTask);
        HandlerList.unregisterAll(this);
        for (VirtualText display : Set.copyOf(displays.values())) {
            destroyForAllVisible(display);
        }
        displays.clear();
        lifetime.clearGroups();
    }

    @Override
    public String backendName() {
        return "packet";
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
        Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
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

    private boolean isVisible(Player player, TextDisplaySpec spec) {
        if (player == null || spec == null) {
            return false;
        }
        if (spec.isTargeted() && !spec.viewers().contains(player.getUniqueId())) {
            return false;
        }
        Location location = spec.displayLocation();
        if (location == null || location.getWorld() == null || player.getWorld() == null) {
            return false;
        }
        if (!player.getWorld().equals(location.getWorld())) {
            return false;
        }
        double distance = settings.viewDistanceBlocks();
        return player.getLocation().distanceSquared(location) <= distance * distance;
    }

    private void startMotion(String key, VirtualText display) {
        TextDisplaySpec spec = display.spec;
        if (spec == null || !spec.hasMotion()) {
            motionRunner.cancel(key);
            display.motionTranslation = DisplayGeometry.Vector3.ZERO;
            display.motionScaleFactor = 1D;
            return;
        }
        motionRunner.start(key, spec.motion(), (interpolationTicks, translation, scaleFactor) -> {
            display.motionTranslation = translation;
            display.motionScaleFactor = scaleFactor;
            sendMotionFrame(display, interpolationTicks, translation, scaleFactor);
        });
    }

    private void sendMotionFrame(VirtualText display,
            int interpolationTicks,
            DisplayGeometry.Vector3 translation,
            double scaleFactor) {
        TextDisplaySpec spec = display.spec;
        if (spec == null) {
            return;
        }
        int translationIndex = ENTITY_METADATA_BASE + 3;
        DisplayGeometry.Vector3 scale = spec.profile().scale();

        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 1, EntityDataTypes.INT, interpolationTicks));
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 2, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(translationIndex, EntityDataTypes.VECTOR3F,
                new Vector3f((float) translation.x(), (float) translation.y(), (float) translation.z())));
        metadata.add(new EntityData<>(translationIndex + 1, EntityDataTypes.VECTOR3F,
                new Vector3f(
                        (float) (scale.x() * scaleFactor),
                        (float) (scale.y() * scaleFactor),
                        (float) (scale.z() * scaleFactor))));

        WrapperPlayServerEntityMetadata packet =
                new WrapperPlayServerEntityMetadata(display.entityId, metadata);
        for (UUID playerId : Set.copyOf(display.visiblePlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                sendPacket(player, packet);
            }
        }
    }

    private void removeGroupKey(String groupKey) {
        Set<String> keys = lifetime.removeGroup(groupKey);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : Set.copyOf(keys)) {
            lifetime.cancelExpiry(key);
            motionRunner.cancel(key);
            VirtualText display = displays.remove(key);
            if (display != null) {
                destroyForAllVisible(display);
            }
        }
    }

    private void removeKey(String groupKey, String key) {
        lifetime.cancelExpiry(key);
        motionRunner.cancel(key);
        VirtualText display = displays.remove(key);
        if (display != null) {
            destroyForAllVisible(display);
        }
        lifetime.removeGroupMember(groupKey, key);
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

    private void spawnFor(Player player, VirtualText display) {
        Location bukkitLocation = display.spec.displayLocation();
        if (bukkitLocation == null) {
            return;
        }
        var packetLocation =
                new com.github.retrooper.packetevents.protocol.world.Location(
                        bukkitLocation.getX(), bukkitLocation.getY(), bukkitLocation.getZ(), 0F, 0F);
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
        sendPacket(player, new WrapperPlayServerEntityMetadata(display.entityId, metadata(display)));
    }

    private List<EntityData<?>> metadata(VirtualText display) {
        TextDisplaySpec spec = display.spec;
        int translationIndex = ENTITY_METADATA_BASE + 3;
        int billboardIndex = ENTITY_METADATA_BASE + 7;
        int viewRangeIndex = ENTITY_METADATA_BASE + 9;
        int textIndex = ENTITY_METADATA_BASE + 15;

        DisplayGeometry.TextProfile profile = spec.profile();
        DisplayGeometry.Vector3 scale = profile.scale();
        DisplayGeometry.Vector3 translation = display.motionTranslation;
        double scaleFactor = display.motionScaleFactor;

        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 1, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 2, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(translationIndex, EntityDataTypes.VECTOR3F,
                new Vector3f((float) translation.x(), (float) translation.y(), (float) translation.z())));
        metadata.add(new EntityData<>(translationIndex + 1, EntityDataTypes.VECTOR3F,
                new Vector3f(
                        (float) (scale.x() * scaleFactor),
                        (float) (scale.y() * scaleFactor),
                        (float) (scale.z() * scaleFactor))));
        metadata.add(new EntityData<>(translationIndex + 2, EntityDataTypes.QUATERNION,
                new Quaternion4f(0F, 0F, 0F, 1F)));
        metadata.add(new EntityData<>(translationIndex + 3, EntityDataTypes.QUATERNION,
                new Quaternion4f(0F, 0F, 0F, 1F)));
        metadata.add(new EntityData<>(billboardIndex, EntityDataTypes.BYTE, billboardByte(profile.billboard())));
        metadata.add(new EntityData<>(viewRangeIndex, EntityDataTypes.FLOAT,
                (float) settings.viewDistanceBlocks()));
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

    private byte styleFlags(DisplayGeometry.TextProfile profile) {
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

    private static final class VirtualText {

        private final int entityId;
        private final UUID uuid;
        private final Set<UUID> visiblePlayers = ConcurrentHashMap.newKeySet();
        private volatile TextDisplaySpec spec;
        private volatile DisplayGeometry.Vector3 motionTranslation = DisplayGeometry.Vector3.ZERO;
        private volatile double motionScaleFactor = 1D;

        private VirtualText(int entityId, UUID uuid, TextDisplaySpec spec) {
            this.entityId = entityId;
            this.uuid = uuid;
            this.spec = spec;
        }
    }
}
