package emaki.jiuwu.craft.cooking.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.cooking.model.NutritionTypeConfig;

/**
 * 营养类型注册表，持有当前加载的全部营养类型定义并提供查询能力。
 */
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
