package emaki.jiuwu.craft.storage.service;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.SearchQuery;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;

/**
 * Substring filtering over pre-computed entry text.
 *
 * <p>No pattern is ever compiled. A hostile regular expression can stall the whole server through
 * catastrophic backtracking, and there is no admin-only escape hatch for the same reason. Every
 * term is a {@link String#contains(CharSequence)} test against text that was already lower-cased
 * and stripped of formatting when the entry was created, so filtering does no per-keystroke work
 * beyond the comparison itself.
 *
 * <p>Filtering is pure computation and may run off the owner thread; only applying the resulting
 * view to a GUI must happen back on the owner thread.
 */
public final class StorageSearchService {

    /**
     * Filters a storage into a view of matching keys, preserving the persisted order.
     *
     * @param storage the storage to filter
     * @param query   the parsed query; an empty query returns the full order
     * @return the matching keys in slot order
     */
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

    /**
     * {@return whether an entry satisfies every term}
     *
     * <p>Terms are combined with AND. An excluding term rejects the entry when it matches.
     */
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
