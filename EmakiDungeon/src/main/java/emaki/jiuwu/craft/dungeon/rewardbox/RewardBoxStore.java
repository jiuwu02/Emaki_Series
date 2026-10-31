package emaki.jiuwu.craft.dungeon.rewardbox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class RewardBoxStore {

    public static final class Box {

        private final List<ItemStack> items;
        private boolean autoFlow;

        public Box(List<ItemStack> items, boolean autoFlow) {
            this.items = items == null ? new ArrayList<>() : items;
            this.autoFlow = autoFlow;
        }

        public List<ItemStack> items() {
            return items;
        }

        public boolean autoFlow() {
            return autoFlow;
        }

        public void setAutoFlow(boolean autoFlow) {
            this.autoFlow = autoFlow;
        }
    }

    private static final String CURRENT_VERSION = "1.0.0";
    private static final long DRAIN_TIMEOUT_MILLIS = 5000L;

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File root;
    private final Map<UUID, Object> lanes = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Boolean>> pendingWrites = new ConcurrentHashMap<>();

    public RewardBoxStore(Plugin plugin, ExecutionDispatcher dispatcher, File dataFolder) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.root = new File(dataFolder, "rewardbox");
    }

    public Box load(UUID playerId) {
        return read(playerId);
    }

    public boolean update(UUID playerId, Consumer<Box> mutator) {
        if (playerId == null || mutator == null) {
            return false;
        }
        synchronized (laneOf(playerId)) {
            Box box = read(playerId);
            Map<String, Object> before = encode(box);
            mutator.accept(box);
            Map<String, Object> after = encode(box);
            if (before.equals(after)) {
                return true;
            }
            return persist(playerId, after);
        }
    }

    public CompletableFuture<Boolean> updateAsync(UUID playerId, Consumer<Box> mutator) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (playerId == null || mutator == null) {
            future.complete(Boolean.FALSE);
            return future;
        }
        TaskToken token = dispatcher.runAsync(plugin, () -> {
            boolean saved;
            try {
                saved = update(playerId, mutator);
            } catch (RuntimeException failure) {
                warn(playerId, "rewardbox update failed", failure);
                saved = false;
            }
            future.complete(saved);
        });
        if (token == null || token.cancelled()) {
            warn(playerId, "rewardbox update rejected | reason=调度被拒绝", null);
            future.complete(Boolean.FALSE);
            return future;
        }
        pendingWrites.put(playerId, future);
        return future;
    }

    public CompletableFuture<Boolean> drainWrites() {
        List<CompletableFuture<Boolean>> pending = List.copyOf(pendingWrites.values());
        if (pending.isEmpty()) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }
        CompletableFuture<?>[] handles = pending.toArray(new CompletableFuture<?>[0]);
        return CompletableFuture.allOf(handles)
                .orTimeout(DRAIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .handle((ignored, throwable) -> {
                    int unsettled = 0;
                    for (CompletableFuture<Boolean> future : pending) {
                        if (!Boolean.TRUE.equals(future.getNow(Boolean.FALSE))) {
                            unsettled++;
                        }
                    }
                    if (unsettled == 0) {
                        pendingWrites.values().removeAll(pending);
                        return Boolean.TRUE;
                    }
                    warn(null, "rewardbox drain unsettled | count=" + unsettled
                            + " | reason=" + describe(throwable), null);
                    return Boolean.FALSE;
                });
    }

    private Box read(UUID playerId) {
        File file = fileOf(playerId);
        if (!file.isFile()) {
            return new Box(new ArrayList<>(), false);
        }
        YamlSection section = YamlFiles.load(file);
        if (section == null || section.isEmpty()) {
            return new Box(new ArrayList<>(), false);
        }
        List<ItemStack> items = new ArrayList<>();
        List<?> rawItems = section.getList("items");
        if (rawItems != null) {
            for (Object raw : rawItems) {
                ItemStack stack = decode(String.valueOf(raw));
                if (stack != null && stack.getAmount() > 0) {
                    items.add(stack);
                }
            }
        }
        return new Box(items, section.getBoolean("auto-flow", false));
    }

    private Map<String, Object> encode(Box box) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("version", CURRENT_VERSION);
        values.put("auto-flow", box.autoFlow());
        List<String> encoded = new ArrayList<>(box.items().size());
        for (ItemStack stack : box.items()) {
            encoded.add(Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
        }
        values.put("items", encoded);
        return values;
    }

    private boolean persist(UUID playerId, Map<String, Object> values) {
        File file = fileOf(playerId);
        try {
            root.mkdirs();
            YamlFiles.save(file, values);
            return true;
        } catch (IOException exception) {
            warn(playerId, "rewardbox save failed | file=" + file.getAbsolutePath()
                    + " | error=" + exception.getMessage(), null);
            return false;
        }
    }

    private Object laneOf(UUID playerId) {
        return lanes.computeIfAbsent(playerId, id -> new Object());
    }

    private void warn(UUID playerId, String detail, Throwable throwable) {
        plugin.getLogger().warning("[EmakiDungeon] " + detail
                + (playerId == null ? "" : " | player=" + playerId)
                + (throwable == null ? "" : " | cause=" + describe(throwable)));
    }

    private String describe(Throwable throwable) {
        if (throwable == null) {
            return "none";
        }
        return throwable.getClass().getSimpleName()
                + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage());
    }

    private File fileOf(UUID playerId) {
        return new File(root, playerId + ".yml");
    }

    private static ItemStack decode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
