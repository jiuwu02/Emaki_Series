package emaki.jiuwu.craft.level.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerLevelData {

    private final UUID uuid;
    private String name;
    private final Map<String, PlayerLevelEntry> levels = new LinkedHashMap<>();
    private boolean dirty;

    public PlayerLevelData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
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

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }
}
