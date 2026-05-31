package emaki.jiuwu.craft.cooking.service.display;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 基于真实 {@link TextDisplay} 实体的文本展示后端。
 */
public final class BukkitCookingTextDisplayService implements CookingTextDisplayService {

    private final JavaPlugin plugin;
    private final Map<String, TextDisplay> displays = new LinkedHashMap<>();
    private final Map<String, Set<String>> displaysByStation = new LinkedHashMap<>();

    public BukkitCookingTextDisplayService(JavaPlugin plugin) {
        this.plugin = plugin;
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
        if (display == null || display.isDead() || !sameWorld(display.getLocation(), location)) {
            remove(spec.stationType(), spec.stationCoordinates(), spec.displayKey());
            display = location.getWorld().spawn(location, TextDisplay.class);
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
        for (TextDisplay display : Set.copyOf(displays.values())) {
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

    private void apply(TextDisplay display, CookingTextDisplaySpec spec) {
        CookingSettingsService.TextDisplayProfile profile = spec.profile();
        // Spigot（非 Paper）TextDisplay#setText 接收 legacy 字符串
        display.setText(MiniMessages.legacy(spec.component()));
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

    private void removeStationKey(String stationKey) {
        Set<String> keys = displaysByStation.remove(stationKey);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : Set.copyOf(keys)) {
            TextDisplay display = displays.remove(key);
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
    }

    private void removeKey(String stationKey, String key) {
        TextDisplay display = displays.remove(key);
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

    private boolean sameWorld(Location left, Location right) {
        return left != null
                && right != null
                && left.getWorld() != null
                && left.getWorld().equals(right.getWorld());
    }
}
