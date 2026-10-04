package emaki.jiuwu.craft.item.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemEditService {

    private static final String BACKUP_DIRECTORY = "edit-backups";

    private final EmakiItemPlugin plugin;
    private final Map<UUID, ItemDefinitionDocument> sessions = new ConcurrentHashMap<>();
    private final Map<Path, ItemDefinitionDocument> pendingFlushes = new ConcurrentHashMap<>();

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
        return mutate(session(player), mutation);
    }

    public synchronized ItemDefinitionDocument.SaveResult mutate(ItemDefinitionDocument document,
            Consumer<YamlTextDocument> mutation) {
        if (document == null) {
            return new ItemDefinitionDocument.SaveResult(ItemDefinitionDocument.SaveStatus.VALIDATION_FAILED,
                    0L, "no_edit_session");
        }
        ItemDefinitionDocument.SaveResult result = document.mutate(mutation);
        if (result.saved()) {
            pendingFlushes.put(document.path(), document);
        }
        return result;
    }

    public synchronized void flushAll() {
        for (Map.Entry<Path, ItemDefinitionDocument> entry : pendingFlushes.entrySet()) {
            ItemDefinitionDocument.SaveResult result = entry.getValue().flush(backupRoot());
            if (result.saved()) {
                pendingFlushes.remove(entry.getKey());
            } else {
                plugin.getLogger().warning("Deferred EmakiItem edit not persisted for " + entry.getKey()
                        + ": " + result.detail());
            }
        }
    }

    public ItemDefinitionDocument openSet(java.nio.file.Path file) throws IOException {
        return ItemDefinitionDocument.open(plugin.getLogger(), file, ItemEditService::validateSet);
    }

    private static String validateSet(YamlTextDocument candidate, String itemId, java.nio.file.Path source) {
        Object rawId = candidate.value("id");
        if (rawId == null || emaki.jiuwu.craft.corelib.api.text.Texts.isBlank(
                emaki.jiuwu.craft.corelib.api.text.Texts.toStringSafe(rawId))) {
            return "definition_parse_failed";
        }
        return itemId.equals(emaki.jiuwu.craft.corelib.api.text.Texts.normalizeId(
                emaki.jiuwu.craft.corelib.api.text.Texts.toStringSafe(rawId))) ? null : "id_changed";
    }

    public Path backupRoot() {
        return plugin.getDataFolder().toPath().resolve(BACKUP_DIRECTORY);
    }
}
