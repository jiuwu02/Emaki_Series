package emaki.jiuwu.craft.cooking.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.cooking.model.NutritionTypeConfig;




public final class NutritionTypeRegistry {

    private volatile Map<String, NutritionTypeConfig> types = Map.of();

    public void reload(Map<String, NutritionTypeConfig> loaded) {
        this.types = loaded == null ? Map.of() : Map.copyOf(loaded);
    }

    public Optional<NutritionTypeConfig> type(String id) {
        if (Texts.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(types.get(Texts.normalizeId(id)));
    }

    public boolean contains(String id) {
        return Texts.isNotBlank(id) && types.containsKey(Texts.normalizeId(id));
    }

    public Collection<NutritionTypeConfig> all() {
        return types.values();
    }

    public Map<String, NutritionTypeConfig> asMap() {
        return new LinkedHashMap<>(types);
    }

    public int size() {
        return types.size();
    }
}
