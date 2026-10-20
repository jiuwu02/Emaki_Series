package emaki.jiuwu.craft.dungeon.script;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EntryRegistry {

    public record Entry(String id, String effect, String description) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public void register(Entry entry) {
        if (entry == null || entry.id() == null || entry.id().isBlank()) {
            throw new IllegalArgumentException("entry id 不能为空");
        }
        entries.put(entry.id(), entry);
    }

    public boolean remove(String id) {
        return entries.remove(id) != null;
    }

    public boolean has(String id) {
        return entries.containsKey(id);
    }

    public Entry get(String id) {
        return entries.get(id);
    }

    public String[] ids() {
        return entries.keySet().toArray(new String[0]);
    }

    public List<Entry> values() {
        return List.copyOf(entries.values());
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
