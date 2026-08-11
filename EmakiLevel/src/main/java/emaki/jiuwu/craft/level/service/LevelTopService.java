package emaki.jiuwu.craft.level.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;

public final class LevelTopService {

    private static final Comparator<TopEntry> TOP_ENTRY_COMPARATOR = Comparator
            .comparingInt(TopEntry::level).reversed()
            .thenComparing(Comparator.comparingDouble(TopEntry::totalExp).reversed())
            .thenComparing(TopEntry::name, String.CASE_INSENSITIVE_ORDER);

    private final PlayerLevelDataStore dataStore;
    private final LevelTypeRegistry typeRegistry;
    private final Map<String, List<TopEntry>> snapshot = new ConcurrentHashMap<>();

    public LevelTopService(PlayerLevelDataStore dataStore, LevelTypeRegistry typeRegistry) {
        this.dataStore = dataStore;
        this.typeRegistry = typeRegistry;
    }

    public void rebuild() {
        rebuildAsync();
    }

    public CompletableFuture<Void> rebuildAsync() {
        return dataStore.allKnownDataAsync(typeRegistry.asMap())
                .thenAccept(allData -> {
                    Map<String, List<TopEntry>> next = new LinkedHashMap<>();
                    if (allData != null) {
                        for (PlayerLevelData data : allData) {
                            collect(next, data);
                        }
                    }
                    replaceSnapshot(next);
                });
    }

    public void update(PlayerLevelData data) {
        if (data == null) {
            return;
        }
        for (LevelTypeConfig type : typeRegistry.all()) {
            List<TopEntry> entries = new ArrayList<>(snapshot.getOrDefault(type.id(), List.of()));
            entries.removeIf(entry -> data.uuid().equals(entry.uuid()));
            TopEntry entry = toEntry(data, type.id());
            if (entry != null) {
                entries.add(entry);
            }
            entries.sort(TOP_ENTRY_COMPARATOR);
            snapshot.put(type.id(), List.copyOf(entries));
        }
    }

    public List<TopEntry> top(String typeId, int limit) {
        String normalizedType = Texts.normalizeId(typeId);
        List<TopEntry> entries = Texts.isBlank(normalizedType)
                ? List.of()
                : snapshot.getOrDefault(normalizedType, List.of());
        int safeLimit = limit <= 0 ? 1 : limit;
        if (safeLimit >= entries.size()) {
            return entries;
        }
        return entries.subList(0, safeLimit);
    }

    public int count(String typeId) {
        return snapshot.getOrDefault(Texts.normalizeId(typeId), List.of()).size();
    }

    private void collect(Map<String, List<TopEntry>> target, PlayerLevelData data) {
        if (data == null) {
            return;
        }
        for (LevelTypeConfig type : typeRegistry.all()) {
            TopEntry entry = toEntry(data, type.id());
            if (entry != null) {
                target.computeIfAbsent(type.id(), ignored -> new ArrayList<>()).add(entry);
            }
        }
    }

    private void replaceSnapshot(Map<String, List<TopEntry>> next) {
        snapshot.clear();
        for (LevelTypeConfig type : typeRegistry.all()) {
            List<TopEntry> entries = next.getOrDefault(type.id(), List.of());
            List<TopEntry> sorted = new ArrayList<>(entries);
            sorted.sort(TOP_ENTRY_COMPARATOR);
            snapshot.put(type.id(), List.copyOf(sorted));
        }
    }

    private TopEntry toEntry(PlayerLevelData data, String typeId) {
        PlayerLevelEntry entry = data.entry(typeId);
        if (entry == null) {
            return null;
        }
        String name = Texts.isBlank(data.name()) ? data.uuid().toString() : data.name();
        return new TopEntry(data.uuid(), name, typeId, entry.level(), entry.exp(), entry.totalExp(), entry.updatedAt());
    }

    public record TopEntry(UUID uuid, String name, String typeId, int level, double exp, double totalExp, long updatedAt) {
    }
}
