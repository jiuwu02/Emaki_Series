package emaki.jiuwu.craft.cooking.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.cooking.api.event.CookingStationInteractEvent;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;









public final class CookingStationTracker implements Listener {

    private final Map<UUID, RecentStation> recentInteractions = new ConcurrentHashMap<>();







    @EventHandler(priority = EventPriority.MONITOR)
    public void onStationInteract(CookingStationInteractEvent event) {
        Player player = event.getPlayer();
        Location location = event.getLocation();
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        StationType type = parseStationType(event.getStationType());
        if (type == null) {
            return;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(location.getBlock());
        if (coordinates == null) {
            return;
        }
        recentInteractions.put(player.getUniqueId(), new RecentStation(type, coordinates));
    }






    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer() != null) {
            recentInteractions.remove(event.getPlayer().getUniqueId());
        }
    }







    public Optional<RecentStation> recent(UUID playerId) {
        return playerId == null ? Optional.empty() : Optional.ofNullable(recentInteractions.get(playerId));
    }


    public void clear() {
        recentInteractions.clear();
    }

    private StationType parseStationType(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return null;
        }
        for (StationType type : StationType.values()) {
            if (type.folderName().equals(folderName)) {
                return type;
            }
        }
        return null;
    }







    public record RecentStation(StationType type, StationCoordinates coordinates) {
    }
}
