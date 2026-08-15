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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;

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

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.display.DisplayKey;
import emaki.jiuwu.craft.corelib.display.DisplayRuntimeSettings;
import emaki.jiuwu.craft.corelib.display.ItemDisplayService;
import emaki.jiuwu.craft.corelib.display.ItemDisplaySpec;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.packet.VirtualEntityIds;

public final class PacketItemDisplayService implements ItemDisplayService, Listener {

    private static final int ENTITY_METADATA_BASE = 8;

    private final Plugin plugin;
    private final DisplayRuntimeSettings settings;
    private final ExecutionDispatcher executionDispatcher;
    private final Map<String, VirtualDisplay> displays = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> displaysByGroup = new ConcurrentHashMap<>();
    private final Set<String> animatingGroups = ConcurrentHashMap.newKeySet();
    private final Map<String, TaskToken> expiryTasks = new ConcurrentHashMap<>();
    private final TaskToken refreshTask;

    public PacketItemDisplayService(Plugin plugin,
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
    public void upsert(ItemDisplaySpec spec) {
        if (!isValidSpec(spec)) {
            return;
        }
        String key = spec.runtimeKey();
        VirtualDisplay display = displays.get(key);
        if (display == null) {
            display = new VirtualDisplay(VirtualEntityIds.next(), UUID.randomUUID(), spec);
            displays.put(key, display);
            displaysByGroup.computeIfAbsent(spec.groupKey(), ignored -> ConcurrentHashMap.newKeySet()).add(key);
            refreshEntry(display);
            scheduleExpiry(spec);
            return;
        }
        display.spec = spec;
        WrapperPlayServerEntityMetadata packet = metadataPacket(display);
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
        String groupKey = namespace + ":" + group;
        animatingGroups.remove(groupKey);
        removeGroupKey(groupKey);
    }

    @Override
    public void removeGroupPrefix(String namespace, String groupPrefix) {
        if (namespace == null || groupPrefix == null) {
            return;
        }
        String prefix = namespace + ":" + groupPrefix;
        for (String groupKey : Set.copyOf(displaysByGroup.keySet())) {
            if (groupKey.startsWith(prefix)) {
                animatingGroups.remove(groupKey);
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
                animatingGroups.remove(groupKey);
                removeGroupKey(groupKey);
            }
        }
    }

    @Override
    public boolean isAnimating(String namespace, String group) {
        if (namespace == null || group == null) {
            return false;
        }
        return animatingGroups.contains(namespace + ":" + group);
    }

    @Override
    public void shutdown() {
        for (TaskToken handle : Map.copyOf(expiryTasks).values()) {
            cancelQuietly(handle);
        }
        expiryTasks.clear();
        cancelQuietly(refreshTask);
        HandlerList.unregisterAll(this);
        for (VirtualDisplay display : Set.copyOf(displays.values())) {
            destroyForAllVisible(display);
        }
        displays.clear();
        displaysByGroup.clear();
        animatingGroups.clear();
    }

    @Override
    public String backendName() {
        return "packet";
    }

    @Override
    public void playTransformAnimation(String namespace,
            String group,
            Location anchor,
            double heightOffset,
            String rotationAxis,
            double rotationDegrees,
            int durationTicks) {
        if (namespace == null || group == null) {
            return;
        }
        String groupKey = namespace + ":" + group;
        if (animatingGroups.contains(groupKey)) {
            return;
        }
        Set<String> keys = displaysByGroup.get(groupKey);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        animatingGroups.add(groupKey);

        int segments = Math.max(1, (int) Math.ceil(Math.abs(rotationDegrees) / 180.0D));
        int halfTicks = Math.max(segments, durationTicks / 2);
        int ticksPerSegment = Math.max(1, halfTicks / segments);
        double degreesPerSegment = rotationDegrees / segments;
        double heightPerSegment = heightOffset / segments;

        for (int segment = 0; segment < segments; segment++) {
            int delay = segment * ticksPerSegment;
            int segmentIndex = segment + 1;
            Runnable task = () -> animateSegment(groupKey, ticksPerSegment,
                    heightPerSegment * segmentIndex, rotationAxis, degreesPerSegment * segmentIndex);
            if (delay == 0) {
                task.run();
            } else {
                executionDispatcher.runGlobalLater(plugin, task, delay);
            }
        }

        int riseEndTick = segments * ticksPerSegment;
        for (int segment = 0; segment < segments; segment++) {
            int delay = riseEndTick + segment * ticksPerSegment;
            int segmentIndex = segment + 1;
            double remaining = 1.0D - ((double) segmentIndex / segments);
            Runnable task = () -> animateSegment(groupKey, ticksPerSegment,
                    heightOffset * remaining, rotationAxis, rotationDegrees * remaining);
            executionDispatcher.runGlobalLater(plugin, task, delay);
        }

        int totalTicks = riseEndTick + segments * ticksPerSegment;
        executionDispatcher.runGlobalLater(plugin, () -> animatingGroups.remove(groupKey), totalTicks);
    }

    private void animateSegment(String groupKey,
            int ticksPerSegment,
            double height,
            String rotationAxis,
            double degrees) {
        Set<String> currentKeys = displaysByGroup.get(groupKey);
        if (currentKeys == null || currentKeys.isEmpty()) {
            return;
        }
        for (String key : Set.copyOf(currentKeys)) {
            VirtualDisplay display = displays.get(key);
            if (display != null) {
                sendAnimationMetadata(display, ticksPerSegment, height, rotationAxis, degrees);
            }
        }
    }

    private void sendAnimationMetadata(VirtualDisplay display,
            int interpolationDuration,
            double heightOffset,
            String rotationAxis,
            double rotationDegrees) {
        int translationIndex = ENTITY_METADATA_BASE + 3;

        Transformation transformation = display.spec.transformation();
        org.joml.Vector3f originalTranslation = transformation.getTranslation();
        org.joml.Vector3f scale = transformation.getScale();
        Quaternionf rightRotation = transformation.getRightRotation();

        Vector3f animatedTranslation = new Vector3f(
                originalTranslation.x(),
                originalTranslation.y() + (float) heightOffset,
                originalTranslation.z()
        );
        Quaternionf animatedLeftRotation =
                new Quaternionf(transformation.getLeftRotation());
        animatedLeftRotation.mul(buildAxisRotation(rotationAxis, rotationDegrees));

        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 1, EntityDataTypes.INT, interpolationDuration));
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 2, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(translationIndex, EntityDataTypes.VECTOR3F, animatedTranslation));
        metadata.add(new EntityData<>(translationIndex + 1, EntityDataTypes.VECTOR3F,
                new Vector3f(scale.x(), scale.y(), scale.z())));
        metadata.add(new EntityData<>(translationIndex + 2, EntityDataTypes.QUATERNION,
                quaternion(animatedLeftRotation)));
        metadata.add(new EntityData<>(translationIndex + 3, EntityDataTypes.QUATERNION,
                quaternion(rightRotation)));

        WrapperPlayServerEntityMetadata packet =
                new WrapperPlayServerEntityMetadata(display.entityId, metadata);
        for (UUID playerId : Set.copyOf(display.visiblePlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                sendPacket(player, packet);
            }
        }
    }

    private Quaternionf buildAxisRotation(String axis, double degrees) {
        float radians = (float) Math.toRadians(degrees);
        return switch (axis == null ? "x" : axis) {
            case "y" -> new Quaternionf().rotateY(radians);
            case "z" -> new Quaternionf().rotateZ(radians);
            default -> new Quaternionf().rotateX(radians);
        };
    }

    private void refreshAll() {
        for (VirtualDisplay display : List.copyOf(displays.values())) {
            refreshEntry(display);
        }
    }

    private void refreshEntry(VirtualDisplay display) {
        if (display == null || display.spec == null) {
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

    private void refreshVisibilityForPlayer(VirtualDisplay display, Player player) {
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

    private boolean isVisible(Player player, ItemDisplaySpec spec) {
        if (player == null || spec == null) {
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

    private void scheduleExpiry(ItemDisplaySpec spec) {
        String key = spec.runtimeKey();
        cancelQuietly(expiryTasks.remove(key));
        if (!spec.hasLifetime()) {
            return;
        }
        String groupKey = spec.groupKey();
        TaskToken handle = executionDispatcher.runGlobalLater(
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

    private void cancelQuietly(TaskToken handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.cancel();
        } catch (RuntimeException _) {

        }
    }

    private void removeGroupKey(String groupKey) {
        Set<String> keys = displaysByGroup.remove(groupKey);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : Set.copyOf(keys)) {
            cancelQuietly(expiryTasks.remove(key));
            VirtualDisplay display = displays.remove(key);
            if (display != null) {
                destroyForAllVisible(display);
            }
        }
    }

    private void removeKey(String groupKey, String key) {
        cancelQuietly(expiryTasks.remove(key));
        VirtualDisplay display = displays.remove(key);
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
        executionDispatcher.runGlobal(plugin, this::refreshAll);
    }

    private void spawnFor(Player player, VirtualDisplay display) {
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
                EntityTypes.ITEM_DISPLAY,
                packetLocation,
                0F,
                0,
                Vector3d.zero()
        );
        sendPacket(player, spawnPacket);
        sendPacket(player, metadataPacket(display));
    }

    private WrapperPlayServerEntityMetadata metadataPacket(VirtualDisplay display) {
        return new WrapperPlayServerEntityMetadata(display.entityId, metadata(display.spec));
    }

    private List<EntityData<?>> metadata(ItemDisplaySpec spec) {
        int translationIndex = ENTITY_METADATA_BASE + 3;
        int viewRangeIndex = ENTITY_METADATA_BASE + 9;
        int itemStackIndex = ENTITY_METADATA_BASE + 15;

        Transformation transformation = spec.transformation();
        org.joml.Vector3f scale = transformation.getScale();

        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 1, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(ENTITY_METADATA_BASE + 2, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(translationIndex, EntityDataTypes.VECTOR3F, Vector3f.zero()));
        metadata.add(new EntityData<>(translationIndex + 1, EntityDataTypes.VECTOR3F,
                new Vector3f(scale.x(), scale.y(), scale.z())));
        metadata.add(new EntityData<>(translationIndex + 2, EntityDataTypes.QUATERNION,
                quaternion(transformation.getLeftRotation())));
        metadata.add(new EntityData<>(translationIndex + 3, EntityDataTypes.QUATERNION,
                quaternion(transformation.getRightRotation())));
        metadata.add(new EntityData<>(viewRangeIndex, EntityDataTypes.FLOAT,
                (float) settings.viewDistanceBlocks()));
        metadata.add(new EntityData<>(itemStackIndex, EntityDataTypes.ITEMSTACK,
                packetItemStack(spec.itemStack())));
        metadata.add(new EntityData<>(itemStackIndex + 1, EntityDataTypes.BYTE, (byte) 0));
        return metadata;
    }

    private Quaternion4f quaternion(Quaternionf quaternion) {
        return new Quaternion4f(quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w());
    }

    private com.github.retrooper.packetevents.protocol.item.ItemStack packetItemStack(ItemStack itemStack) {
        ItemStack clone = itemStack.clone();
        clone.setAmount(1);
        return SpigotConversionUtil.fromBukkitItemStack(clone);
    }

    private void destroyFor(Player player, VirtualDisplay display) {
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

    private void destroyForAllVisible(VirtualDisplay display) {
        for (UUID playerId : Set.copyOf(display.visiblePlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                destroyFor(player, display);
            }
        }
        display.visiblePlayers.clear();
    }

    private boolean isValidSpec(ItemDisplaySpec spec) {
        return spec != null
                && DisplayKey.isValid(spec.key())
                && spec.itemStack() != null
                && !spec.itemStack().getType().isAir()
                && spec.displayLocation() != null
                && spec.displayLocation().getWorld() != null;
    }

    private static final class VirtualDisplay {

        private final int entityId;
        private final UUID uuid;
        private final Set<UUID> visiblePlayers = ConcurrentHashMap.newKeySet();
        private volatile ItemDisplaySpec spec;

        private VirtualDisplay(int entityId, UUID uuid, ItemDisplaySpec spec) {
            this.entityId = entityId;
            this.uuid = uuid;
            this.spec = spec;
        }
    }
}
