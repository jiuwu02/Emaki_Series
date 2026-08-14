package emaki.jiuwu.craft.corelib.display.bukkit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.display.DisplayKey;
import emaki.jiuwu.craft.corelib.display.ItemDisplayService;
import emaki.jiuwu.craft.corelib.display.ItemDisplaySpec;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

/** 用真实 {@link ItemDisplay} 实体实现的物品展示服务。 */
public final class BukkitItemDisplayService implements ItemDisplayService {

    private final Plugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final Map<String, ItemDisplay> displays = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> displaysByGroup = new ConcurrentHashMap<>();
    private final Set<String> animatingGroups = ConcurrentHashMap.newKeySet();
    private final Map<String, TaskToken> expiryTasks = new ConcurrentHashMap<>();

    public BukkitItemDisplayService(Plugin plugin, ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
    }

    @Override
    public void upsert(ItemDisplaySpec spec) {
        if (!isValidSpec(spec)) {
            return;
        }
        Location location = spec.displayLocation();
        if (location == null || location.getWorld() == null) {
            remove(spec.key());
            return;
        }
        String key = spec.runtimeKey();
        ItemDisplay display = displays.get(key);
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
        for (Map.Entry<String, ItemDisplay> entry : Map.copyOf(displays).entrySet()) {
            ItemDisplay display = entry.getValue();
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
        animatingGroups.clear();
    }

    @Override
    public String backendName() {
        return "bukkit";
    }

    @Override
    public void playTransformAnimation(String namespace,
            String group,
            Location anchor,
            double heightOffset,
            String rotationAxis,
            double rotationDegrees,
            int durationTicks) {
        if (namespace == null || group == null || anchor == null) {
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

        Map<String, Transformation> originals = new LinkedHashMap<>();
        for (String key : Set.copyOf(keys)) {
            ItemDisplay display = displays.get(key);
            if (display == null) {
                continue;
            }
            executionDispatcher.runEntity(plugin, display, () -> {
                if (!display.isDead()) {
                    originals.put(key, display.getTransformation());
                }
            }, () -> removeMapOnly(groupKey, key));
        }

        // 抬升阶段：逐段累加高度与角度
        for (int segment = 0; segment < segments; segment++) {
            int delay = segment * ticksPerSegment;
            int segmentIndex = segment + 1;
            Runnable task = () -> applySegment(groupKey, originals, ticksPerSegment,
                    heightPerSegment * segmentIndex, rotationAxis, degreesPerSegment * segmentIndex);
            if (delay == 0) {
                task.run();
            } else {
                executionDispatcher.runAtLocationLater(plugin, anchor, task, delay);
            }
        }

        // 回落阶段：按剩余比例递减回原状
        int riseEndTick = segments * ticksPerSegment;
        for (int segment = 0; segment < segments; segment++) {
            int delay = riseEndTick + segment * ticksPerSegment;
            int segmentIndex = segment + 1;
            double remaining = 1.0D - ((double) segmentIndex / segments);
            Runnable task = () -> applySegment(groupKey, originals, ticksPerSegment,
                    heightOffset * remaining, rotationAxis, rotationDegrees * remaining);
            executionDispatcher.runAtLocationLater(plugin, anchor, task, delay);
        }

        int totalTicks = riseEndTick + segments * ticksPerSegment;
        executionDispatcher.runAtLocationLater(plugin, anchor,
                () -> animatingGroups.remove(groupKey), totalTicks);
    }

    private void applySegment(String groupKey,
            Map<String, Transformation> originals,
            int ticksPerSegment,
            double height,
            String rotationAxis,
            double degrees) {
        Set<String> currentKeys = displaysByGroup.get(groupKey);
        if (currentKeys == null || currentKeys.isEmpty()) {
            return;
        }
        for (String key : Set.copyOf(currentKeys)) {
            ItemDisplay display = displays.get(key);
            if (display == null) {
                continue;
            }
            executionDispatcher.runEntity(plugin, display, () -> {
                if (display.isDead()) {
                    removeMapOnly(groupKey, key);
                    return;
                }
                Transformation original =
                        originals.computeIfAbsent(key, ignored -> display.getTransformation());
                display.setInterpolationDuration(ticksPerSegment);
                display.setTransformation(
                        buildAnimatedTransformation(original, height, rotationAxis, degrees));
                display.setInterpolationDelay(0);
            }, () -> removeMapOnly(groupKey, key));
        }
    }

    private Transformation buildAnimatedTransformation(Transformation base,
            double heightOffset,
            String rotationAxis,
            double rotationDegrees) {
        Vector3f translation = new Vector3f(base.getTranslation());
        translation.y += (float) heightOffset;

        Quaternionf leftRotation = new Quaternionf(base.getLeftRotation());
        leftRotation.mul(buildAxisRotation(rotationAxis, rotationDegrees));

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

    private void apply(ItemDisplay display, ItemDisplaySpec spec) {
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

    private void spawnAtLocation(ItemDisplaySpec spec) {
        Location location = spec.displayLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }
        executionDispatcher.runAtLocation(plugin, location, () -> {
            String key = spec.runtimeKey();
            if (displays.containsKey(key)) {
                return;
            }
            ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class);
            displays.put(key, display);
            displaysByGroup.computeIfAbsent(spec.groupKey(), ignored -> ConcurrentHashMap.newKeySet()).add(key);
            apply(display, spec);
            scheduleExpiry(spec);
        });
    }

    /** 与文本后端同理：重排到期任务前必须取消旧任务。 */
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
            // 任务可能已结束，忽略
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
        ItemDisplay display = displays.get(key);
        if (display == null) {
            removeMapOnly(groupKey, key);
            return;
        }
        executionDispatcher.runEntity(plugin, display, () -> removeKeyOwned(groupKey, key, display), () ->
                removeMapOnly(groupKey, key));
    }

    private void removeKeyOwned(String groupKey, String key, ItemDisplay display) {
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

    private boolean isValidSpec(ItemDisplaySpec spec) {
        return spec != null
                && DisplayKey.isValid(spec.key())
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
