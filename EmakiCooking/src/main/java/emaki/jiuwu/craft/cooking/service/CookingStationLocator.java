package emaki.jiuwu.craft.cooking.service;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationSnapshot;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.CookingStationTracker.RecentStation;

public final class CookingStationLocator {

    private static final double MAX_STATION_DISTANCE = 5.0D;
    private static final double MAX_STATION_DISTANCE_SQUARED = MAX_STATION_DISTANCE * MAX_STATION_DISTANCE;

    private final EmakiCookingPlugin plugin;

    public CookingStationLocator(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<StationSnapshot> snapshotForViewer(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        Optional<StationSnapshot> recent = snapshotFromRecentInteraction(player);
        if (recent.isPresent()) {
            return recent;
        }
        return snapshotFromOpenSession(player.getUniqueId());
    }

    private Optional<StationSnapshot> snapshotFromRecentInteraction(Player player) {
        if (plugin.stationTracker() == null) {
            return Optional.empty();
        }
        Optional<RecentStation> recent = plugin.stationTracker().recent(player.getUniqueId());
        if (recent.isEmpty()) {
            return Optional.empty();
        }
        RecentStation station = recent.get();
        if (!withinReach(player, station.coordinates())) {
            return Optional.empty();
        }
        return snapshotByType(station.type(), station.coordinates());
    }

    private boolean withinReach(Player player, StationCoordinates coordinates) {
        if (player == null || coordinates == null || player.getWorld() == null) {
            return false;
        }
        if (!player.getWorld().getName().equals(coordinates.world())) {
            return false;
        }
        Location center = coordinates.location(0.5D, 0.5D, 0.5D);
        if (center == null || center.getWorld() == null) {
            return false;
        }
        return player.getLocation().distanceSquared(center) <= MAX_STATION_DISTANCE_SQUARED;
    }

    private Optional<StationSnapshot> snapshotFromOpenSession(UUID viewerId) {
        Optional<StationCoordinates> steamer = plugin.steamerRuntimeService().viewingStation(viewerId);
        if (steamer.isPresent()) {
            return plugin.steamerRuntimeService().snapshotAt(steamer.get());
        }
        Optional<StationCoordinates> oven = plugin.ovenRuntimeService().viewingStation(viewerId);
        if (oven.isPresent()) {
            return plugin.ovenRuntimeService().snapshotAt(oven.get());
        }
        Optional<StationCoordinates> juicer = plugin.juicerRuntimeService().viewingStation(viewerId);
        if (juicer.isPresent()) {
            return plugin.juicerRuntimeService().snapshotAt(juicer.get());
        }
        Optional<StationCoordinates> barrel = plugin.fermentationBarrelRuntimeService().viewingStation(viewerId);
        if (barrel.isPresent()) {
            return plugin.fermentationBarrelRuntimeService().snapshotAt(barrel.get());
        }
        return Optional.empty();
    }

    private Optional<StationSnapshot> snapshotByType(StationType type, StationCoordinates coordinates) {
        if (type == null || coordinates == null) {
            return Optional.empty();
        }
        return switch (type) {
            case CHOPPING_BOARD -> plugin.choppingBoardRuntimeService().snapshotAt(coordinates);
            case WOK -> plugin.wokRuntimeService().snapshotAt(coordinates);
            case GRINDER -> plugin.grinderRuntimeService().snapshotAt(coordinates);
            case STEAMER -> plugin.steamerRuntimeService().snapshotAt(coordinates);
            case OVEN -> plugin.ovenRuntimeService().snapshotAt(coordinates);
            case JUICER -> plugin.juicerRuntimeService().snapshotAt(coordinates);
            case FERMENTATION_BARREL -> plugin.fermentationBarrelRuntimeService().snapshotAt(coordinates);
        };
    }
}
