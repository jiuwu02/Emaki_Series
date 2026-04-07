package emaki.jiuwu.craft.forge.service;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.text.Texts;

final class EditorStateManager {

    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, UUID> locks = new ConcurrentHashMap<>();

    EditorSession get(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    void put(EditorSession session) {
        if (session == null) {
            return;
        }
        sessions.put(session.playerId(), session);
    }

    void remove(Player player) {
        if (player != null) {
            remove(player.getUniqueId());
        }
    }

    void remove(UUID playerId) {
        if (playerId == null) {
            return;
        }
        EditorSession removed = sessions.remove(playerId);
        if (removed != null && removed.resourceType() != null && Texts.isNotBlank(removed.originalId())) {
            releaseLock(removed.resourceType(), removed.originalId(), playerId);
        }
    }

    Map<UUID, EditorSession> all() {
        return Map.copyOf(sessions);
    }

    boolean tryLock(EditableResourceType type, String resourceId, UUID playerId) {
        if (type == null || Texts.isBlank(resourceId) || playerId == null) {
            return true;
        }
        String key = lockKey(type, resourceId);
        UUID owner = locks.get(key);
        if (owner != null && !owner.equals(playerId)) {
            return false;
        }
        locks.put(key, playerId);
        return true;
    }

    boolean isLockedByOther(EditableResourceType type, String resourceId, UUID playerId) {
        if (type == null || Texts.isBlank(resourceId)) {
            return false;
        }
        UUID owner = locks.get(lockKey(type, resourceId));
        return owner != null && !owner.equals(playerId);
    }

    void releaseLock(EditableResourceType type, String resourceId, UUID playerId) {
        if (type == null || Texts.isBlank(resourceId) || playerId == null) {
            return;
        }
        String key = lockKey(type, resourceId);
        UUID owner = locks.get(key);
        if (playerId.equals(owner)) {
            locks.remove(key);
        }
    }

    void clear() {
        sessions.clear();
        locks.clear();
    }

    private String lockKey(EditableResourceType type, String resourceId) {
        return type.id() + ":" + resourceId.trim().toLowerCase(Locale.ROOT);
    }
}
