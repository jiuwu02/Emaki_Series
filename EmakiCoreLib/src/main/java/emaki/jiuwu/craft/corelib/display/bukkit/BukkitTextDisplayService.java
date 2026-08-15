package emaki.jiuwu.craft.corelib.display.bukkit;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.display.DisplayGeometry;
import emaki.jiuwu.craft.corelib.display.DisplayKey;
import emaki.jiuwu.craft.corelib.display.DisplayMotionRunner;
import emaki.jiuwu.craft.corelib.display.TextDisplayService;
import emaki.jiuwu.craft.corelib.display.TextDisplaySpec;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class BukkitTextDisplayService implements TextDisplayService {

    private final Plugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final DisplayMotionRunner motionRunner;
    private final Map<String, TextDisplay> displays = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> displaysByGroup = new ConcurrentHashMap<>();
    private final Map<String, TaskToken> expiryTasks = new ConcurrentHashMap<>();

    public BukkitTextDisplayService(Plugin plugin, ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        this.motionRunner = new DisplayMotionRunner(plugin, executionDispatcher);
    }

    @Override
    public void upsert(TextDisplaySpec spec) {
        if (spec == null || !DisplayKey.isValid(spec.key())) {
            return;
        }
        if (!spec.hasText()) {
            remove(spec.key());
            return;
        }
        Location location = spec.displayLocation();
        if (location == null || location.getWorld() == null) {
            remove(spec.key());
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
                removeKeyOwned(spec.groupKey(), key, display);
                spawnAtLocation(spec);
                return;
            }
            display.teleport(location);
            apply(display, spec);
            startMotion(spec, display);
            scheduleExpiry(spec);
        }, () -> {
            removeMapOnly(spec.groupKey(), key);
            spawnAtLocation(spec);
        });
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
        for (TaskToken handle : Map.copyOf(expiryTasks).values()) {
            cancelQuietly(handle);
        }
        expiryTasks.clear();
        motionRunner.shutdown();
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
        displaysByGroup.clear();
    }

    @Override
    public String backendName() {
        return "bukkit";
    }

    private void apply(TextDisplay display, TextDisplaySpec spec) {
        DisplayGeometry.TextProfile profile = spec.profile();
        display.text(spec.component());
        display.setBillboard(billboard(profile.billboard()));
        display.setInterpolationDuration(0);
        display.setTransformation(spec.hasMotion()
                ? spec.transformation(spec.motion().translationAt(0), spec.motion().scaleFactorAt(0))
                : spec.transformation());
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

    private void spawnAtLocation(TextDisplaySpec spec) {
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
            displaysByGroup.computeIfAbsent(spec.groupKey(), ignored -> ConcurrentHashMap.newKeySet()).add(key);
            apply(display, spec);
            startMotion(spec, display);
            scheduleExpiry(spec);
        });
    }

    private void startMotion(TextDisplaySpec spec, TextDisplay display) {
        String key = spec.runtimeKey();
        if (!spec.hasMotion()) {
            motionRunner.cancel(key);
            return;
        }
        motionRunner.start(key, spec.motion(), (interpolationTicks, translation, scaleFactor) ->
                executionDispatcher.runEntity(plugin, display, () -> {
                    if (display.isDead()) {
                        motionRunner.cancel(key);
                        return;
                    }
                    display.setInterpolationDuration(interpolationTicks);
                    display.setTransformation(spec.transformation(translation, scaleFactor));
                    display.setInterpolationDelay(0);
                }, () -> motionRunner.cancel(key)));
    }

    private void scheduleExpiry(TextDisplaySpec spec) {
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
            removeKey(groupKey, key);
        }
    }

    private void removeKey(String groupKey, String key) {
        cancelQuietly(expiryTasks.remove(key));
        motionRunner.cancel(key);
        TextDisplay display = displays.get(key);
        if (display == null) {
            removeMapOnly(groupKey, key);
            return;
        }
        executionDispatcher.runEntity(plugin, display, () -> removeKeyOwned(groupKey, key, display), () ->
                removeMapOnly(groupKey, key));
    }

    private void removeKeyOwned(String groupKey, String key, TextDisplay display) {
        removeMapOnly(groupKey, key);
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    private void removeMapOnly(String groupKey, String key) {
        displays.remove(key);
        Set<String> groupKeys = displaysByGroup.get(groupKey);
        if (groupKeys == null) {
            return;
        }
        groupKeys.remove(key);
        if (groupKeys.isEmpty()) {
            displaysByGroup.remove(groupKey);
        }
    }

    private boolean sameWorld(Location left, Location right) {
        return left != null
                && right != null
                && left.getWorld() != null
                && left.getWorld().equals(right.getWorld());
    }
}
