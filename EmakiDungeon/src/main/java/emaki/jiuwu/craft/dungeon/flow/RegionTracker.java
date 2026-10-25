package emaki.jiuwu.craft.dungeon.flow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.EventIngress;
import emaki.jiuwu.craft.dungeon.instance.InstanceState;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;

public final class RegionTracker {

    private static final long PERIOD_TICKS = 10L;
    private static final double POINT_RADIUS = 2.0D;

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final InstanceRegistry registry;

    private final Map<String, Boolean> presence = new HashMap<>();
    private final Map<String, Integer> lastRange = new HashMap<>();
    private final Map<String, Long> lastEffectAt = new HashMap<>();

    private TaskToken task = TaskToken.UNAVAILABLE;

    public RegionTracker(Plugin plugin, ExecutionDispatcher dispatcher, InstanceRegistry registry) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.registry = registry;
    }

    public void start() {
        task = dispatcher.runGlobalTimer(plugin, this::sweep, PERIOD_TICKS, PERIOD_TICKS);
    }

    public void shutdown() {
        task.cancel();
    }

    private void sweep() {
        Set<String> activeBases = new HashSet<>();
        for (DungeonInstance instance : registry.all()) {
            if (instance.state() != InstanceState.RUNNING || instance.flowRuntime() == null) {
                continue;
            }
            World world = Bukkit.getWorld(instance.instanceId());
            if (world == null) {
                continue;
            }
            FlowRuntime runtime = instance.flowRuntime();
            for (Player player : world.getPlayers()) {
                trackPlayer(runtime, world, player, activeBases);
            }
        }
        presence.keySet().removeIf(key -> !activeBases.contains(baseOf(key)));
        lastRange.keySet().removeIf(key -> !activeBases.contains(baseOf(key)));
        lastEffectAt.keySet().removeIf(key -> !activeBases.contains(baseOf(key)));
    }

    private static String baseOf(String key) {
        int index = key.indexOf(':');
        return index < 0 ? key : key.substring(0, index);
    }

    private void trackPlayer(FlowRuntime runtime, World world, Player player, Set<String> activeBases) {
        Location location = player.getLocation();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        FlowDefinition definition = runtime.definition();
        Map<String, Boolean> now = new HashMap<>();
        String base = runtime.instance().instanceId() + '|' + player.getUniqueId();
        activeBases.add(base);

        for (String zoneId : definition.triggerZones().keySet()) {
            FlowRegion region = definition.triggerZones().get(zoneId).region();
            zoneTransition(runtime, player, zoneId, region != null && region.contains(x, y, z), now, base);
        }
        for (String zoneId : definition.checkpoints().keySet()) {
            FlowModules.CheckpointDef checkpoint = definition.checkpoints().get(zoneId);
            FlowRegion region = checkpoint.region() != null
                    ? checkpoint.region()
                    : (checkpoint.point() == null ? null : pointRegion(checkpoint.point()));
            zoneTransition(runtime, player, zoneId, region != null && region.contains(x, y, z), now, base);
        }
        for (String zoneId : definition.teleportZones().keySet()) {
            FlowRegion region = definition.teleportZones().get(zoneId).region();
            zoneTransition(runtime, player, zoneId, region != null && region.contains(x, y, z), now, base);
        }
        for (String zoneId : definition.boundaryZones().keySet()) {
            FlowRegion region = definition.boundaryZones().get(zoneId).region();
            zoneTransition(runtime, player, zoneId, region != null && region.contains(x, y, z), now, base);
        }
        for (String zoneId : definition.effectZones().keySet()) {
            FlowRegion region = definition.effectZones().get(zoneId).region();
            boolean present = region != null && region.contains(x, y, z);
            zoneTransition(runtime, player, zoneId, present, now, base);
            if (present) {
                int interval = Math.max(1, definition.effectZones().get(zoneId).intervalTicks());
                String key = base + ":" + zoneId;
                long nowMillis = System.currentTimeMillis();
                long last = lastEffectAt.getOrDefault(key, 0L);
                if (nowMillis - last >= interval * 50L) {
                    lastEffectAt.put(key, nowMillis);
                    Map<String, Object> payload = basePayload(player);
                    payload.put(Facts.REGION_ENTERED, true);
                    payload.put("effect_tick", true);
                    runtime.instance().ingress().post(
                            new EventIngress.IngressEvent(
                                    EventIngressKeys.ZONE, zoneId, payload));
                }
            }
        }
        for (String zoneId : definition.roomTimers().keySet()) {
            FlowRegion region = definition.roomTimers().get(zoneId).region();
            zoneTransition(runtime, player, zoneId, region != null && region.contains(x, y, z), now, base);
        }
        for (FlowModules.RoomDef room : definition.rooms()) {
            zoneTransition(runtime, player, room.id(),
                    room.region() != null && room.region().contains(x, y, z), now, base);
        }
        for (String optionId : definition.options().keySet()) {
            FlowModules.OptionDef option = definition.options().get(optionId);
            FlowPoint point = option.point();
            boolean present = point != null && distanceSquared(point, x, y, z) <= POINT_RADIUS * POINT_RADIUS;
            zoneTransition(runtime, player, optionId, present, now, base);
        }
        for (String spawnId : definition.spawnPoints().keySet()) {
            FlowModules.SpawnPointModuleDef spawnPoint = definition.spawnPoints().get(spawnId);
            if (!spawnPoint.head().enabled()) {
                continue;
            }
            int count = 0;
            double rangeSquared = (double) spawnPoint.range() * spawnPoint.range();
            FlowPoint center = spawnPoint.point();
            for (Player member : world.getPlayers()) {
                Location memberLocation = member.getLocation();
                double dx = memberLocation.getX() - center.x();
                double dy = memberLocation.getY() - center.y();
                double dz = memberLocation.getZ() - center.z();
                if (dx * dx + dy * dy + dz * dz <= rangeSquared) {
                    count++;
                }
            }
            String rangeKey = base + ":" + spawnId;
            int previous = lastRange.getOrDefault(rangeKey, 0);
            lastRange.put(rangeKey, count);
            if (previous < spawnPoint.minPlayers() && count >= spawnPoint.minPlayers()) {
                Map<String, Object> payload = basePayload(player);
                payload.put(Facts.RANGE_PLAYERS, count);
                runtime.instance().ingress().post(
                        new EventIngress.IngressEvent(
                                EventIngressKeys.RANGE, spawnId, payload));
            }
        }

        String prefix = base + ":";
        presence.keySet().removeIf(key -> key.startsWith(prefix) && !now.containsKey(key.substring(prefix.length())));
        for (Map.Entry<String, Boolean> entry : now.entrySet()) {
            presence.put(prefix + entry.getKey(), entry.getValue());
        }
    }

    private void zoneTransition(FlowRuntime runtime, Player player, String zoneId, boolean present,
            Map<String, Boolean> now, String base) {
        String key = base + ":" + zoneId;
        boolean previous = presence.getOrDefault(key, false);
        now.put(zoneId, present);
        if (present == previous) {
            return;
        }
        Map<String, Object> payload = basePayload(player);
        if (present) {
            payload.put(Facts.REGION_ENTERED, true);
        } else {
            payload.put(Facts.REGION_LEFT, true);
        }
        runtime.instance().ingress().post(
                new EventIngress.IngressEvent(
                        EventIngressKeys.ZONE, zoneId, payload));
    }

    private static Map<String, Object> basePayload(Player player) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(EventIngressKeys.KEY_PLAYER, player.getUniqueId());
        return payload;
    }

    private static FlowRegion pointRegion(FlowPoint point) {
        return new FlowRegion(point.x(), point.y(), point.z(), point.x(), point.y(), point.z());
    }

    private static double distanceSquared(FlowPoint point, double x, double y, double z) {
        double dx = point.x() - x;
        double dy = point.y() - y;
        double dz = point.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
