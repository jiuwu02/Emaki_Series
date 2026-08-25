package emaki.jiuwu.craft.codex.codex.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CodexCategory(String categoryId,
        String title,
        String icon,
        int order,
        Map<String, CodexEntry> entries) {

    public CodexCategory {
        entries = entries == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public CodexEntry entry(String entryId) {
        return entryId == null ? null : entries.get(entryId);
    }

    public List<CodexEntry> orderedEntries() {
        return List.copyOf(entries.values());
    }
}
