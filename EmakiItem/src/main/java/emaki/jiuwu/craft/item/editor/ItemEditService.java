package emaki.jiuwu.craft.item.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemEditService {

    private static final long RELOAD_DEBOUNCE_TICKS = 10L;
    private static final String BACKUP_DIRECTORY = "edit-backups";

    private final EmakiItemPlugin plugin;
    private final Map<UUID, ItemDefinitionDocument> sessions = new ConcurrentHashMap<>();
    private volatile boolean reloadScheduled;

    public ItemEditService(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized ItemDefinitionDocument open(Player player, Path file) throws IOException {
        ItemDefinitionDocument document = ItemDefinitionDocument.open(plugin.getLogger(), file,
                ItemDefinitionDocument.definitionParserValidator(plugin.getLogger()));
        sessions.put(player.getUniqueId(), document);
        return document;
    }

    public ItemDefinitionDocument session(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public synchronized void close(Player player) {
        if (player != null) {
            sessions.remove(player.getUniqueId());
        }
    }

    public synchronized void closeAll() {
        sessions.clear();
    }

    public ItemDefinitionDocument.SaveResult mutate(Player player, Consumer<YamlTextDocument> mutation) {
        ItemDefinitionDocument document = session(player);
        if (document == null) {
            return new ItemDefinitionDocument.SaveResult(ItemDefinitionDocument.SaveStatus.VALIDATION_FAILED,
                    0L, "no_edit_session");
        }
        ItemDefinitionDocument.SaveResult result = document.mutate(mutation, backupRoot());
        if (result.saved()) {
            scheduleReload();
        }
        return result;
    }

    public Path backupRoot() {
        return plugin.getDataFolder().toPath().resolve(BACKUP_DIRECTORY);
    }

    private void scheduleReload() {
        if (reloadScheduled) {
            return;
        }
        reloadScheduled = true;
        TaskToken token = plugin.scheduling().runGlobalLater(plugin, () -> {
            reloadScheduled = false;
            try {
                plugin.reloadPluginStateAsync();
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Reload after EmakiItem edit failed: " + failure, failure);
            }
        }, RELOAD_DEBOUNCE_TICKS);
        if (token == TaskToken.UNAVAILABLE) {
            reloadScheduled = false;
        }
    }
}
