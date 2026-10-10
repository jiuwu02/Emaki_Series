package emaki.jiuwu.craft.corelib.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class EditorEditService {

    private static final String BACKUP_DIRECTORY = "edit-backups";

    private final JavaPlugin plugin;
    private final Map<UUID, EditorDocument> sessions = new ConcurrentHashMap<>();
    private final Map<Path, EditorDocument> pendingFlushes = new ConcurrentHashMap<>();

    public EditorEditService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized EditorDocument open(Player player, Path file, EditorValidator validator) throws IOException {
        EditorDocument document = EditorDocument.open(plugin.getLogger(), file, validator);
        sessions.put(player.getUniqueId(), document);
        return document;
    }

    public EditorDocument openDetached(Path file, EditorValidator validator) throws IOException {
        return EditorDocument.open(plugin.getLogger(), file, validator);
    }

    public EditorDocument session(Player player) {
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

    public EditorDocument.SaveResult mutate(Player player, Consumer<YamlTextDocument> mutation) {
        return mutate(session(player), mutation, null);
    }

    public synchronized EditorDocument.SaveResult mutate(EditorDocument document,
            Consumer<YamlTextDocument> mutation,
            Function<YamlTextDocument, String> extraCheck) {
        if (document == null) {
            return new EditorDocument.SaveResult(EditorDocument.SaveStatus.VALIDATION_FAILED,
                    0L, "no_edit_session");
        }
        EditorDocument.SaveResult result = document.mutate(mutation, extraCheck);
        if (result.saved()) {
            pendingFlushes.put(document.path(), document);
        }
        return result;
    }

    public synchronized void flushAll() {
        for (Map.Entry<Path, EditorDocument> entry : pendingFlushes.entrySet()) {
            EditorDocument.SaveResult result = entry.getValue().flush(backupRoot());
            if (result.saved()) {
                pendingFlushes.remove(entry.getKey());
            } else {
                plugin.getLogger().warning("[editor] 编辑器延迟写入未持久化 " + entry.getKey()
                        + ": " + result.detail());
            }
        }
    }

    public Path backupRoot() {
        return plugin.getDataFolder().toPath().resolve(BACKUP_DIRECTORY);
    }
}
