package emaki.jiuwu.craft.codex.codex.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.session.SessionData;

public final class PlayerCodex implements SessionData<PlayerCodex> {

    private final UUID playerId;
    private final Map<String, CodexEntryState> entries = new LinkedHashMap<>();
    private String playerName = "";
    private long revision;
    private long persistedRevision;

    public PlayerCodex(UUID playerId) {
        this.playerId = playerId;
    }

    public static String compositeKey(String categoryId, String entryId) {
        String category = Texts.normalizeId(categoryId);
        String entry = Texts.normalizeId(entryId);
        if (Texts.isBlank(category) || Texts.isBlank(entry)) {
            return "";
        }
        return category + "/" + entry;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public void playerName(String playerName) {
        if (Texts.isNotBlank(playerName) && !playerName.equals(this.playerName)) {
            this.playerName = playerName;
            markDirty();
        }
    }

    public Map<String, CodexEntryState> entries() {
        return Map.copyOf(entries);
    }

    public Set<String> entryKeys() {
        return Set.copyOf(entries.keySet());
    }

    public CodexEntryState state(String categoryId, String entryId) {
        return entries.get(compositeKey(categoryId, entryId));
    }

    public boolean unlocked(String categoryId, String entryId) {
        return entries.containsKey(compositeKey(categoryId, entryId));
    }

    public boolean activated(String categoryId, String entryId) {
        CodexEntryState state = state(categoryId, entryId);
        return state != null && state.activated();
    }

    public boolean claimed(String categoryId, String entryId) {
        CodexEntryState state = state(categoryId, entryId);
        return state != null && state.claimed();
    }

    public boolean unlock(String categoryId, String entryId, long timestamp) {
        String key = compositeKey(categoryId, entryId);
        if (Texts.isBlank(key) || entries.containsKey(key)) {
            return false;
        }
        entries.put(key, CodexEntryState.unlockedNow(timestamp));
        markDirty();
        return true;
    }

    public boolean activate(String categoryId, String entryId) {
        String key = compositeKey(categoryId, entryId);
        CodexEntryState state = entries.get(key);
        if (state == null || state.activated()) {
            return false;
        }
        entries.put(key, state.withActivated(true));
        markDirty();
        return true;
    }

    public boolean claim(String categoryId, String entryId) {
        String key = compositeKey(categoryId, entryId);
        CodexEntryState state = entries.get(key);
        if (state == null || state.claimed()) {
            return false;
        }
        entries.put(key, state.withClaimed(true));
        markDirty();
        return true;
    }

    public boolean forget(String categoryId, String entryId) {
        if (entries.remove(compositeKey(categoryId, entryId)) == null) {
            return false;
        }
        markDirty();
        return true;
    }

    public int forgetAll() {
        if (entries.isEmpty()) {
            return 0;
        }
        int removed = entries.size();
        entries.clear();
        markDirty();
        return removed;
    }

    public Set<String> activatedKeys() {
        Set<String> activated = new LinkedHashSet<>();
        entries.forEach((key, state) -> {
            if (state.activated()) {
                activated.add(key);
            }
        });
        return Collections.unmodifiableSet(activated);
    }

    public void installLoaded(Map<String, CodexEntryState> loaded) {
        entries.clear();
        if (loaded != null) {
            loaded.forEach((key, state) -> {
                String normalized = Texts.trim(key);
                if (Texts.isNotBlank(normalized) && state != null) {
                    entries.put(normalized, state);
                }
            });
        }
    }

    @Override
    public PlayerCodex copy() {
        PlayerCodex copy = new PlayerCodex(playerId);
        copy.playerName = playerName;
        copy.entries.putAll(entries);
        copy.revision = revision;
        copy.persistedRevision = persistedRevision;
        return copy;
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
        if (revision > persistedRevision) {
            persistedRevision = revision;
        }
    }

    @Override
    public void clearDirty() {
        persistedRevision = revision;
    }
}
