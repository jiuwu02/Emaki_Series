package emaki.jiuwu.craft.corelib.display.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
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
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;

import emaki.jiuwu.craft.corelib.display.DisplayGeometry;
import emaki.jiuwu.craft.corelib.display.DisplayKey;
import emaki.jiuwu.craft.corelib.display.DisplayRuntimeSettings;
import emaki.jiuwu.craft.corelib.display.TextDisplayService;
import emaki.jiuwu.craft.corelib.display.TextDisplaySpec;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.packet.VirtualEntityIds;

/**
 * 用封包模拟的文本展示实体。
 *
 * <p>虚拟实体不进入区块存档，也不占用服务端实体，且支持
 * {@link TextDisplaySpec#viewers()} 定向可见。
 */
public final class PacketTextDisplayService implements TextDisplayService, Listener {

    private static final int ENTITY_METADATA_BASE = 8;

    private final Plugin plugin;
    private final DisplayRuntimeSettings settings;
    private final ExecutionDispatcher executionDispatcher;
    private final Map<String, VirtualText> displays = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> displaysByGroup = new ConcurrentHashMap<>();
    private final Map<String, TaskHandle> expiryTasks = new ConcurrentHashMap<>();
    private final TaskHandle refreshTask;

    public PacketTextDisplayService(Plugin plugin,
            DisplayRuntimeSettings settings,
            ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.settings = settings;
        this.executionDispatcher = executionDispatcher;
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
            displaysByGroup.computeIfAbsent(spec.groupKey(), ignored -> ConcurrentHashMap.newKeySet()).add(key);
            refreshEntry(display);
            scheduleExpiry(spec);
            return;
        }
        display.spec = spec;
        WrapperPlayServerEntityMetadata packet =
                new WrapperPlayServerEntityMetadata(display.entityId, metadata(spec));
        for (UUID playerId : Set.copyOf(display.visiblePlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                sendPacket(player, packet);
            }
        }
        refreshEntry(display);
        scheduleExpiry(spec);
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
        for (String groupKey : Set.copyOf(displaysByGroup.keySet())) {
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
        for (String groupKey : Set.copyOf(displaysByGroup.keySet())) {
            if (groupKey.startsWith(prefix)) {
                removeGroupKey(groupKey);
            }
        }
    }

    @Override
    public void shutdown() {
        for (TaskHandle handle : Map.copyOf(expiryTasks).values()) {
            cancelQuietly(handle);
        }
        expiryTasks.clear();
        cancelQuietly(refreshTask);
        HandlerList.unregisterAll(this);
        for (VirtualText display : Set.copyOf(displays.values())) {
            destroyForAllVisible(display);
        }
        displays.clear();
        displaysByGroup.clear();
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

    /**
     * 判断玩家是否应看到该实体。
     *
     * <p>定向 spec 先做 viewer 白名单过滤，再照常做同世界与距离判定，
     * 因此定向可见永远是空间可见性的子集。
     */
    private boolean isVisible(Player player, TextDisplaySpec spec) {
        if (player == null || spec == null) {
            return false;
        }
        if (spec.isTargeted() && !spec.viewers().contains(player.getUniqueId())) {
            return false;
        }
        org.bukkit.Location location = spec.displayLocation();
        if (location == null || location.getWorld() == null || player.getWorld() == null) {
            return false;
        }
        if (!player.getWorld().equals(location.getWorld())) {
            return false;
        }
        double distance = settings.viewDistanceBlocks();
        return player.getLocation().distanceSquared(location) <= distance * distance;
    }

    /** 与真实体后端同理：重排到期任务前必须取消旧任务。 */
    private void scheduleExpiry(TextDisplaySpec spec) {
        String key = spec.runtimeKey();
        cancelQuietly(expiryTasks.remove(key));
        if (!spec.hasLifetime()) {
            return;
        }
        String groupKey = spec.groupKey();
        TaskHandle handle = executionDispatcher.runGlobalLater(
                plugin,
                () -> {
                    expiryTasks.remove(key);
                    removeKey(groupKey, key);
                },
                spec.lifetimeTicks()
        );
        if (handle != null) {
            expiryTasks.put(key, handle);
        }
    }

    private void cancelQuietly(TaskHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.cancel();
        } catch (RuntimeException _) {
            // 任务可能已结束，忽略
        }
    }

    private void removeGroupKey(String groupKey) {
        Set<String> keys = displaysByGroup.remove(groupKey);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : Set.copyOf(keys)) {
            cancelQuietly(expiryTasks.remove(key));
            VirtualText display = displays.remove(key);
            if (display != null) {
                destroyForAllVisible(display);
            }
        }
    }

    private void removeKey(String groupKey, String key) {
        cancelQuietly(expiryTasks.remove(key));
        VirtualText display = displays.remove(key);
        if (display != null) {
            destroyForAllVisible(display);
        }
        Set<String> groupKeys = displaysByGroup.get(groupKey);
        if (groupKeys == null) {
            return;
        }
        groupKeys.remove(key);
        if (groupKeys.isEmpty()) {
            displaysByGroup.remove(groupKey);
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

    private void spawnFor(Player player, VirtualText display) {
        org.bukkit.Location bukkitLocation = display.spec.displayLocation();
        if (bukkitLocation == null) {
            return;
        }
        com.github.retrooper.packetevents.protocol.world.Location packetLocation =
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
        sendPacket(player, new WrapperPlayServerEntityMetadata(display.entityId, metadata(display.spec)));
    }

    /**
     * 构建 TextDisplay 的元数据。
     *
     * <p>索引需按服务端版本推导：1.20.2 起新增了位置旋转插值字段，
     * 之后的所有索引整体后移一位。
     */
    private List<EntityData<?>> metadata(TextDisplaySpec spec) {
        ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
        boolean hasPositionRotationInterpolation = version.isNewerThanOrEquals(ServerVersion.V_1_20_2);
        int translationIndex = ENTITY_METADATA_BASE + (hasPositionRotationInterpolation ? 3 : 2);
        int billboardIndex = ENTITY_METADATA_BASE + (hasPositionRotationInterpolation ? 7 : 6);
        int viewRangeIndex = ENTITY_METADATA_BASE + (hasPositionRotationInterpolation ? 9 : 8);
        int textIndex = ENTITY_METADATA_BASE + (hasPositionRotationInterpolation ? 15 : 14);

        DisplayGeometry.TextProfile profile = spec.profile();
        DisplayGeometry.Vector3 scale = profile.scale();

        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 1, EntityDataTypes.INT, 0));
        if (hasPositionRotationInterpolation) {
            metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 2, EntityDataTypes.INT, 0));
        }
        metadata.add(new EntityData<>(translationIndex, EntityDataTypes.VECTOR3F, Vector3f.zero()));
        metadata.add(new EntityData<>(translationIndex + 1, EntityDataTypes.VECTOR3F,
                new Vector3f((float) scale.x(), (float) scale.y(), (float) scale.z())));
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

        private VirtualText(int entityId, UUID uuid, TextDisplaySpec spec) {
            this.entityId = entityId;
            this.uuid = uuid;
            this.spec = spec;
        }
    }
}
