package emaki.jiuwu.craft.storage.service;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.SearchQuery;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;

public final class StorageSearchService {

    public List<StorageKey> filter(PlayerStorage storage, SearchQuery query) {
        if (storage == null) {
            return List.of();
        }
        if (query == null || query.isEmpty()) {
            return storage.entryOrder();
        }
        List<StorageKey> matches = new ArrayList<>();
        for (StorageKey key : storage.entryOrder()) {
            StorageEntry entry = storage.entry(key);
            if (entry != null && matches(entry, query)) {
                matches.add(key);
            }
        }
        return matches;
    }

    public boolean matches(StorageEntry entry, SearchQuery query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String searchText = entry.searchText();
        for (SearchQuery.Term term : query.terms()) {
            boolean hit = scopeText(searchText, term.scope()).contains(term.text());
            if (hit == term.exclude()) {
                return false;
            }
        }
        return true;
    }

    private String scopeText(String searchText, SearchQuery.Scope scope) {
        return switch (scope) {
            case NAME -> StorageTextIndexer.namePart(searchText);
            case LORE -> StorageTextIndexer.lorePart(searchText);
            case ID -> StorageTextIndexer.idPart(searchText);
            case ANY -> StorageTextIndexer.anyPart(searchText);
        };
    }
}
