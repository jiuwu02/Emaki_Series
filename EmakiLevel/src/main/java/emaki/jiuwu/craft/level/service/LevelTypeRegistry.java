package emaki.jiuwu.craft.level.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;

public final class LevelTypeRegistry {

    private Map<String, LevelTypeConfig> types = Map.of();

    public void reload(Map<String, LevelTypeConfig> types) {
        this.types = types == null ? Map.of() : Map.copyOf(types);
    }

    public Optional<LevelTypeConfig> type(String id) {
        return Optional.ofNullable(types.get(Texts.normalizeId(id)));
    }

    public Collection<LevelTypeConfig> all() {
        return types.values();
    }

    public Map<String, LevelTypeConfig> asMap() {
        return types;
    }
}
