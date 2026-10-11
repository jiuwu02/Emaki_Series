package emaki.jiuwu.craft.structure.template;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class StructureTemplateCache {

    public record Insert(boolean stored, boolean shouldWarn) {
    }

    private final int maxEntries;
    private final long maxBytes;
    private final LinkedHashMap<String, StructureTemplate> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<String> oversizeWarned = new HashSet<>();
    private long estimatedBytes;

    public StructureTemplateCache(int maxEntries, long maxBytes) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries " + maxEntries);
        }
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes " + maxBytes);
        }
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
    }

    public synchronized Optional<StructureTemplate> get(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    public synchronized Insert insert(String id, StructureTemplate template) {
        Objects.requireNonNull(template, "template");
        long estimate = template.estimatedBytes();
        if (estimate > maxBytes) {
            return new Insert(false, oversizeWarned.add(id));
        }
        StructureTemplate replaced = entries.remove(id);
        if (replaced != null) {
            estimatedBytes -= replaced.estimatedBytes();
        }
        entries.put(id, template);
        estimatedBytes += estimate;
        evictOverflow();
        return new Insert(true, false);
    }

    public synchronized void invalidateAll() {
        entries.clear();
        oversizeWarned.clear();
        estimatedBytes = 0;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized long estimatedBytes() {
        return estimatedBytes;
    }

    private void evictOverflow() {
        while (entries.size() > maxEntries) {
            Iterator<Map.Entry<String, StructureTemplate>> eldest = entries.entrySet().iterator();
            Map.Entry<String, StructureTemplate> victim = eldest.next();
            eldest.remove();
            estimatedBytes -= victim.getValue().estimatedBytes();
        }
    }
}
