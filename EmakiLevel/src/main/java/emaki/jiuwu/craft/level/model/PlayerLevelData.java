package emaki.jiuwu.craft.level.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.session.SessionData;

public final class PlayerLevelData implements SessionData<PlayerLevelData> {

    private final UUID uuid;
    private String name;
    private final Map<String, PlayerLevelEntry> levels = new LinkedHashMap<>();
    private long revision;
    private long persistedRevision;

    public PlayerLevelData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
    }

    @Override
    public PlayerLevelData copy() {
        PlayerLevelData copy = new PlayerLevelData(uuid, name);
        for (Map.Entry<String, PlayerLevelEntry> entry : levels.entrySet()) {
            copy.levels.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().copy());
        }
        copy.revision = revision;
        copy.persistedRevision = persistedRevision;
        return copy;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name == null ? "" : name;
        markDirty();
    }

    public Map<String, PlayerLevelEntry> levels() {
        return levels;
    }

    public PlayerLevelEntry entry(String typeId) {
        return levels.get(typeId);
    }

    public void put(String typeId, PlayerLevelEntry entry) {
        levels.put(typeId, entry);
        markDirty();
    }

    @Override
    public long revision() {
        return revision;
    }

    @Override
    public long persistedRevision() {
        return persistedRevision;
    }

    @Override
    public boolean dirty() {
        return revision > persistedRevision;
    }

    @Override
    public void markDirty() {
        revision++;
    }

    @Override
    public void markPersisted(long revision) {
        persistedRevision = Math.max(persistedRevision, revision);
    }

    @Override
    public void clearDirty() {
        persistedRevision = revision;
    }
}
