package emaki.jiuwu.craft.dungeon.rewardbox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File root;

    public RewardBoxStore(Plugin plugin, ExecutionDispatcher dispatcher, File dataFolder) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.root = new File(dataFolder, "rewardbox");
    }

    public synchronized Box load(UUID playerId) {
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

    public void saveAsync(UUID playerId, Box box) {
        dispatcher.runAsync(plugin, () -> saveSync(playerId, box));
    }

    private synchronized void saveSync(UUID playerId, Box box) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("version", CURRENT_VERSION);
        values.put("auto-flow", box.autoFlow());
        List<String> encoded = new ArrayList<>(box.items().size());
        for (ItemStack stack : box.items()) {
            encoded.add(Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
        }
        values.put("items", encoded);
        File file = fileOf(playerId);
        try {
            Files.createDirectories(root.toPath());
            Path target = file.toPath();
            Path temp = target.resolveSibling(target.getFileName() + "." + java.util.UUID.randomUUID() + ".tmp");
            YamlFiles.save(temp.toFile(), values);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
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
