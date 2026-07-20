package emaki.jiuwu.craft.cooking.service.display;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitCookingTextDisplayService implements CookingTextDisplayService {

    private final JavaPlugin plugin;
    @SuppressWarnings("unused")
    private final ExecutionDispatcher executionDispatcher;
    @SuppressWarnings("unused")
    private final ThreadOwnership threadOwnership;
    private final Map<String, TextDisplay> displays = new LinkedHashMap<>();
    private final Map<String, Set<String>> displaysByStation = new LinkedHashMap<>();

    public BukkitCookingTextDisplayService(JavaPlugin plugin,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
    }

    @Override
    public void upsert(CookingTextDisplaySpec spec) {
        if (spec == null) {
            return;
        }
        if (!spec.hasText()) {
            remove(spec.stationType(), spec.stationCoordinates(), spec.displayKey());
            return;
        }
        Location location = spec.displayLocation();
        if (location == null || location.getWorld() == null) {
            remove(spec.stationType(), spec.stationCoordinates(), spec.displayKey());
            return;
        }
        String key = spec.runtimeKey();
        TextDisplay display = displays.get(key);
        if (display == null) {
            spawnAtLocation(spec);
            return;
        }
        executionDispatcher.runEntity(plugin, display, () -> {
            if (display.isDead() || !sameWorld(display.getLocation(), location)) {
                removeKeyOwned(spec.stationRuntimeKey(), key, display);
                spawnAtLocation(spec);
                return;
            }
            display.teleport(location);
            apply(display, spec);
        }, () -> {
            removeMapOnly(spec.stationRuntimeKey(), key);
            spawnAtLocation(spec);
        });
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
        for (Map.Entry<String, TextDisplay> entry : Map.copyOf(displays).entrySet()) {
            TextDisplay display = entry.getValue();
            if (display != null) {
                executionDispatcher.runEntity(plugin, display, () -> {
                    if (!display.isDead()) {
                        display.remove();
                    }
                }, () -> {
                });
            }
        }
        displays.clear();
        displaysByStation.clear();
    }

    @Override
    public String backendName() {
        return "bukkit";
    }

    private void apply(TextDisplay display, CookingTextDisplaySpec spec) {
        CookingSettingsService.TextDisplayProfile profile = spec.profile();
        display.text(spec.component());
        display.setBillboard(billboard(profile.billboard()));
        display.setTransformation(spec.transformation());
        display.setInterpolationDuration(0);
        display.setInterpolationDelay(0);
        display.setSeeThrough(profile.seeThrough());
        display.setShadowed(profile.shadow());
        display.setBackgroundColor(backgroundColor(profile.backgroundArgb()));
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setGravity(false);
    }

    private Display.Billboard billboard(String value) {
        return switch (value == null ? "center" : value) {
            case "fixed" -> Display.Billboard.FIXED;
            case "vertical" -> Display.Billboard.VERTICAL;
            case "horizontal" -> Display.Billboard.HORIZONTAL;
            default -> Display.Billboard.CENTER;
        };
    }

    private Color backgroundColor(int argb) {
        int alpha = (argb >> 24) & 0xFF;
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return Color.fromARGB(alpha, red, green, blue);
    }

    private void spawnAtLocation(CookingTextDisplaySpec spec) {
        Location location = spec.displayLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }
        executionDispatcher.runAtLocation(plugin, location, () -> {
            String key = spec.runtimeKey();
            if (displays.containsKey(key)) {
                return;
            }
            TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
            displays.put(key, display);
            displaysByStation.computeIfAbsent(spec.stationRuntimeKey(), ignored -> new LinkedHashSet<>()).add(key);
            apply(display, spec);
        });
    }

    private void removeStationKey(String stationKey) {
        Set<String> keys = displaysByStation.remove(stationKey);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : Set.copyOf(keys)) {
            removeKey(stationKey, key);
        }
    }

    private void removeKey(String stationKey, String key) {
        TextDisplay display = displays.get(key);
        if (display == null) {
            removeMapOnly(stationKey, key);
            return;
        }
        executionDispatcher.runEntity(plugin, display, () -> removeKeyOwned(stationKey, key, display), () ->
                removeMapOnly(stationKey, key));
    }

    private void removeKeyOwned(String stationKey, String key, TextDisplay display) {
        removeMapOnly(stationKey, key);
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    private void removeMapOnly(String stationKey, String key) {
        displays.remove(key);
        Set<String> stationKeys = displaysByStation.get(stationKey);
        if (stationKeys == null) {
            return;
        }
        stationKeys.remove(key);
        if (stationKeys.isEmpty()) {
            displaysByStation.remove(stationKey);
        }
    }

    private boolean sameWorld(Location left, Location right) {
        return left != null
                && right != null
                && left.getWorld() != null
                && left.getWorld().equals(right.getWorld());
    }
}
