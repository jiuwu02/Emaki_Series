package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class EmakiNamespaceRegistry {

    private final Map<String, EmakiNamespaceDefinition> definitions = new LinkedHashMap<>();
    private volatile List<EmakiNamespaceDefinition> orderedDefinitions = List.of();

    public void register(EmakiNamespaceDefinition definition) {
        if (definition == null || Texts.isBlank(definition.id())) {
            return;
        }
        definitions.put(normalizeId(definition.id()), definition);
        refreshCache();
    }

    public void unregister(String namespaceId) {
        if (Texts.isBlank(namespaceId)) {
            return;
        }
        definitions.remove(normalizeId(namespaceId));
        refreshCache();
    }

    public EmakiNamespaceDefinition get(String namespaceId) {
        return Texts.isBlank(namespaceId) ? null : definitions.get(normalizeId(namespaceId));
    }

    public List<EmakiNamespaceDefinition> ordered() {
        return orderedDefinitions;
    }

    public List<String> orderNamespaces(Iterable<String> namespaceIds) {
        Map<String, String> unique = new LinkedHashMap<>();
        if (namespaceIds != null) {
            for (String namespaceId : namespaceIds) {
                String normalized = normalizeId(namespaceId);
                if (!normalized.isBlank()) {
                    unique.putIfAbsent(normalized, normalized);
                }
            }
        }
        List<String> ordered = new ArrayList<>(unique.values());
        ordered.sort(Comparator
                .comparingInt((String namespaceId) -> {
                    EmakiNamespaceDefinition definition = definitions.get(namespaceId);
                    return definition == null ? Integer.MAX_VALUE : definition.order();
                })
                .thenComparing(namespaceId -> namespaceId));
        return ordered.isEmpty() ? List.of() : List.copyOf(ordered);
    }

    private void refreshCache() {
        List<EmakiNamespaceDefinition> values = new ArrayList<>(definitions.values());
        values.sort(Comparator.comparingInt(EmakiNamespaceDefinition::order)
                .thenComparing(definition -> normalizeId(definition.id())));
        orderedDefinitions = values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
