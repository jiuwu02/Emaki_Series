package emaki.jiuwu.craft.dungeon.snapshot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class PlayersDataStore {

    public static final class PlayerData {

        private String restoreInstance = "";
        private String partyId = "";
        private boolean globalChat;
        private Map<String, Object> limits = Map.of();

        public String restoreInstance() {
            return restoreInstance;
        }

        public void setRestoreInstance(String restoreInstance) {
            this.restoreInstance = restoreInstance == null ? "" : restoreInstance;
        }

        public String partyId() {
            return partyId;
        }

        public void setPartyId(String partyId) {
            this.partyId = partyId == null ? "" : partyId;
        }

        public boolean globalChat() {
            return globalChat;
        }

        public void setGlobalChat(boolean globalChat) {
            this.globalChat = globalChat;
        }

        public Map<String, Object> limits() {
            return limits;
        }

        public void setLimits(Map<String, Object> limits) {
            this.limits = limits == null ? Map.of() : limits;
        }
    }

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File root;
    private final boolean globalChatDefault;
    private final Object ioLock = new Object();

    public PlayersDataStore(Plugin plugin, ExecutionDispatcher dispatcher, File dataFolder, boolean globalChatDefault) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.root = new File(dataFolder, "players");
        this.globalChatDefault = globalChatDefault;
    }

    public File root() {
        return root;
    }

    public PlayerData read(UUID playerId) {
        PlayerData data = new PlayerData();
        data.globalChat = globalChatDefault;
        File file = fileOf(playerId);
        if (!file.isFile()) {
            return data;
        }
        YamlSection section = YamlFiles.load(file);
        if (section == null || section.isEmpty()) {
            return data;
        }
        data.restoreInstance = section.getString("restore-instance", "");
        data.partyId = section.getString("party", "");
        data.globalChat = section.getBoolean("global-chat", globalChatDefault);
        YamlSection limits = section.getSection("limits");
        if (limits != null && !limits.isEmpty()) {
            data.limits = new LinkedHashMap<>(limits.asMap());
        }
        return data;
    }

    public void writeAsync(UUID playerId, PlayerData data) {
        dispatcher.runAsync(plugin, () -> {
            synchronized (ioLock) {
                writeSync(playerId, data);
            }
        });
    }

    public void updateAsync(UUID playerId, java.util.function.Consumer<PlayerData> mutator) {
        if (playerId == null || mutator == null) {
            return;
        }
        dispatcher.runAsync(plugin, () -> {
            synchronized (ioLock) {
                PlayerData data = read(playerId);
                mutator.accept(data);
                writeSync(playerId, data);
            }
        });
    }

    public void writeSync(UUID playerId, PlayerData data) {
        if (playerId == null || data == null) {
            return;
        }
        File file = fileOf(playerId);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("restore-instance", data.restoreInstance);
        values.put("party", data.partyId);
        values.put("global-chat", data.globalChat);
        if (!data.limits.isEmpty()) {
            values.put("limits", new LinkedHashMap<>(data.limits));
        }
        Path temp = file.toPath().resolveSibling(
                file.getName() + "." + java.util.UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(root.toPath());
            YamlFiles.save(temp.toFile(), values);
            try {
                Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    private File fileOf(UUID playerId) {
        return new File(root, playerId + ".yml");
    }
}
