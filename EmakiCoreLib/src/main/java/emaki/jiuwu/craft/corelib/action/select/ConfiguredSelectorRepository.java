package emaki.jiuwu.craft.corelib.action.select;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ConfiguredSelectorRepository {

    public static final String SELECT_SOURCE_ID = "select";

    private final Map<String, SelectorDefinition> definitions;

    private ConfiguredSelectorRepository(Map<String, SelectorDefinition> definitions) {
        this.definitions = definitions;
    }

    public static @NotNull ConfiguredSelectorRepository build(@Nullable List<SelectorDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return empty();
        }
        Map<String, SelectorDefinition> indexed = new LinkedHashMap<>();
        for (SelectorDefinition definition : definitions) {
            if (definition == null || Texts.isBlank(definition.id())) {
                continue;
            }
            indexed.put(Texts.lower(definition.id()), definition);
        }
        return indexed.isEmpty() ? empty() : new ConfiguredSelectorRepository(Map.copyOf(indexed));
    }

    public static @NotNull ConfiguredSelectorRepository empty() {
        return new ConfiguredSelectorRepository(Map.of());
    }

    public @Nullable SelectorDefinition find(@Nullable String id) {
        if (Texts.isBlank(id)) {
            return null;
        }
        return definitions.get(Texts.lower(id));
    }

    public @NotNull List<String> ids() {
        return definitions.keySet().stream().sorted().toList();
    }
}