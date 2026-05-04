package emaki.jiuwu.craft.cooking.service.display;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitCookingDisplayService implements CookingDisplayService {

    private final JavaPlugin plugin;
    private final Map<String, ItemDisplay> displays = new LinkedHashMap<>();
    private final Map<String, Set<String>> displaysByStation = new LinkedHashMap<>();

    public BukkitCookingDisplayService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void upsert(CookingDisplaySpec spec) {
        if (!isValidSpec(spec)) {
            return;
        }
        Location location = spec.displayLocation();
        if (location == null || location.getWorld() == null) {
            remove(spec.stationType(), spec.stationCoordinates(), spec.displayKey());
            return;
        }
        String key = spec.runtimeKey();
        ItemDisplay display = displays.get(key);
        if (display == null || display.isDead() || !sameWorld(display.getLocation(), location)) {
            remove(spec.stationType(), spec.stationCoordinates(), spec.displayKey());
            display = location.getWorld().spawn(location, ItemDisplay.class);
            displays.put(key, display);
            displaysByStation.computeIfAbsent(spec.stationRuntimeKey(), ignored -> new LinkedHashSet<>()).add(key);
        } else {
            display.teleport(location);
        }
        apply(display, spec);
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
        for (ItemDisplay display : Set.copyOf(displays.values())) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
        displays.clear();
        displaysByStation.clear();
    }

    @Override
    public String backendName() {
        return "bukkit";
    }

    private void apply(ItemDisplay display, CookingDisplaySpec spec) {
        ItemStack itemStack = spec.itemStack().clone();
        itemStack.setAmount(1);
        display.setItemStack(itemStack);
        display.setTransformation(spec.transformation());
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setSilent(true);
        display.setGravity(false);
    }

    private void removeStationKey(String stationKey) {
        Set<String> keys = displaysByStation.remove(stationKey);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : Set.copyOf(keys)) {
            ItemDisplay display = displays.remove(key);
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
    }

    private void removeKey(String stationKey, String key) {
        ItemDisplay display = displays.remove(key);
        if (display != null && !display.isDead()) {
            display.remove();
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
                && spec.baseLocation().getWorld() != null;
    }

    private boolean sameWorld(Location left, Location right) {
        return left != null
                && right != null
                && left.getWorld() != null
                && left.getWorld().equals(right.getWorld());
    }
}
