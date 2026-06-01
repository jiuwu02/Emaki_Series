package emaki.jiuwu.craft.cooking.service.display;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BukkitCookingDisplayService implements CookingDisplayService {

    private final JavaPlugin plugin;
    private final Map<String, ItemDisplay> displays = new LinkedHashMap<>();
    private final Map<String, Set<String>> displaysByStation = new LinkedHashMap<>();
    private final Set<String> animatingStations = new LinkedHashSet<>();

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
        String stationKey = stationType.folderName() + ":" + coordinates.runtimeKey();
        animatingStations.remove(stationKey);
        removeStationKey(stationKey);
    }

    @Override
    public void removeStationType(StationType stationType) {
        if (stationType == null) {
            return;
        }
        String prefix = stationType.folderName() + ":";
        for (String stationKey : Set.copyOf(displaysByStation.keySet())) {
            if (stationKey.startsWith(prefix)) {
                animatingStations.remove(stationKey);
                removeStationKey(stationKey);
            }
        }
    }

    @Override
    public void playStirAnimation(StationType stationType, StationCoordinates coordinates,
                                  double heightOffset, String rotationAxis,
                                  double rotationDegrees, int durationTicks) {
        if (stationType == null || coordinates == null) {
            return;
        }
        String stationKey = stationType.folderName() + ":" + coordinates.runtimeKey();
        if (animatingStations.contains(stationKey)) {
            return;
        }
        Set<String> keys = displaysByStation.get(stationKey);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        animatingStations.add(stationKey);

        int segments = Math.max(1, (int) Math.ceil(Math.abs(rotationDegrees) / 180.0D));
        int halfTicks = Math.max(segments, durationTicks / 2);
        int ticksPerSegment = Math.max(1, halfTicks / segments);
        double degreesPerSegment = rotationDegrees / segments;
        double heightPerSegment = heightOffset / segments;

        Map<String, Transformation> originalTransformations = new LinkedHashMap<>();
        for (String key : Set.copyOf(keys)) {
            ItemDisplay display = displays.get(key);
            if (display == null || display.isDead()) {
                continue;
            }
            originalTransformations.put(key, display.getTransformation());
        }

        for (int segment = 0; segment < segments; segment++) {
            int delay = segment * ticksPerSegment;
            int segmentIndex = segment + 1;
            Runnable segmentTask = () -> {
                Set<String> currentKeys = displaysByStation.get(stationKey);
                if (currentKeys == null || currentKeys.isEmpty()) {
                    return;
                }
                for (String key : Set.copyOf(currentKeys)) {
                    ItemDisplay display = displays.get(key);
                    if (display == null || display.isDead()) {
                        continue;
                    }
                    Transformation original = originalTransformations.get(key);
                    if (original == null) {
                        continue;
                    }
                    double cumulativeDegrees = degreesPerSegment * segmentIndex;
                    double cumulativeHeight = heightPerSegment * segmentIndex;
                    Transformation target = buildAnimatedTransformation(
                            original, cumulativeHeight, rotationAxis, cumulativeDegrees);
                    display.setInterpolationDuration(ticksPerSegment);
                    display.setTransformation(target);
                    display.setInterpolationDelay(0);
                }
            };
            if (delay == 0) {
                segmentTask.run();
            } else {
                FoliaSchedulerAdapter.runTaskLater(plugin, segmentTask, delay);
            }
        }

        int riseEndTick = segments * ticksPerSegment;
        for (int segment = 0; segment < segments; segment++) {
            int delay = riseEndTick + segment * ticksPerSegment;
            int segmentIndex = segment + 1;
            Runnable segmentTask = () -> {
                Set<String> currentKeys = displaysByStation.get(stationKey);
                if (currentKeys == null || currentKeys.isEmpty()) {
                    return;
                }
                for (String key : Set.copyOf(currentKeys)) {
                    ItemDisplay display = displays.get(key);
                    if (display == null || display.isDead()) {
                        continue;
                    }
                    Transformation original = originalTransformations.get(key);
                    if (original == null) {
                        continue;
                    }
                    double remainingFraction = 1.0D - ((double) segmentIndex / segments);
                    double currentDegrees = rotationDegrees * remainingFraction;
                    double currentHeight = heightOffset * remainingFraction;
                    Transformation target = buildAnimatedTransformation(
                            original, currentHeight, rotationAxis, currentDegrees);
                    display.setInterpolationDuration(ticksPerSegment);
                    display.setTransformation(target);
                    display.setInterpolationDelay(0);
                }
            };
            FoliaSchedulerAdapter.runTaskLater(plugin, segmentTask, delay);
        }

        int totalTicks = riseEndTick + segments * ticksPerSegment;
        FoliaSchedulerAdapter.runTaskLater(plugin, () -> animatingStations.remove(stationKey), totalTicks);
    }

    @Override
    public boolean isAnimating(StationType stationType, StationCoordinates coordinates) {
        if (stationType == null || coordinates == null) {
            return false;
        }
        return animatingStations.contains(stationType.folderName() + ":" + coordinates.runtimeKey());
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
        animatingStations.clear();
    }

    @Override
    public String backendName() {
        return "bukkit";
    }

    private Transformation buildAnimatedTransformation(Transformation base,
                                                       double heightOffset,
                                                       String rotationAxis,
                                                       double rotationDegrees) {
        Vector3f translation = new Vector3f(base.getTranslation());
        translation.y += (float) heightOffset;

        Quaternionf leftRotation = new Quaternionf(base.getLeftRotation());
        Quaternionf deltaRotation = buildAxisRotation(rotationAxis, rotationDegrees);
        leftRotation.mul(deltaRotation);

        return new Transformation(
                translation,
                leftRotation,
                new Vector3f(base.getScale()),
                new Quaternionf(base.getRightRotation())
        );
    }

    private Quaternionf buildAxisRotation(String axis, double degrees) {
        float radians = (float) Math.toRadians(degrees);
        return switch (axis == null ? "x" : axis) {
            case "y" -> new Quaternionf().rotateY(radians);
            case "z" -> new Quaternionf().rotateZ(radians);
            default -> new Quaternionf().rotateX(radians);
        };
    }

    private void apply(ItemDisplay display, CookingDisplaySpec spec) {
        ItemStack itemStack = spec.itemStack().clone();
        itemStack.setAmount(1);
        display.setItemStack(itemStack);
        display.setInterpolationDuration(0);
        display.setInterpolationDelay(0);
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
