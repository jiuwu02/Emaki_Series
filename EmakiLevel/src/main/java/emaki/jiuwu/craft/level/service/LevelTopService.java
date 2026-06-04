package emaki.jiuwu.craft.level.service;

import java.util.Comparator;
import java.util.List;

import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;

public final class LevelTopService {

    private final PlayerLevelDataStore dataStore;

    public LevelTopService(PlayerLevelDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public List<TopEntry> top(String typeId, int limit) {
        return dataStore.cachedData().values().stream()
                .map(data -> toEntry(data, typeId))
                .filter(entry -> entry != null)
                .sorted(Comparator.comparingInt(TopEntry::level).reversed()
                        .thenComparing(Comparator.comparingDouble(TopEntry::totalExp).reversed()))
                .limit(Math.max(1, limit))
                .toList();
    }

    private TopEntry toEntry(PlayerLevelData data, String typeId) {
        PlayerLevelEntry entry = data.entry(typeId);
        return entry == null ? null : new TopEntry(data.name(), entry.level(), entry.totalExp());
    }

    public record TopEntry(String name, int level, double totalExp) {
    }
}
