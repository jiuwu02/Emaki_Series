package emaki.jiuwu.craft.forge.model;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class ForgeMaterial {

    public static final class MaterialEffect {
        private final String type;
        private final Map<String, Object> data;

        public MaterialEffect(String type, Map<String, Object> data) {
            this.type = type;
            this.data = Map.copyOf(data);
        }

        public static MaterialEffect fromConfig(Object raw) {
            if (raw == null) {
                return null;
            }
            String type = ConfigNodes.string(raw, "type", null);
            if (Texts.isBlank(type)) {
                return null;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
                if ("type".equals(entry.getKey())) {
                    continue;
                }
                data.put(entry.getKey(), ConfigNodes.toPlainData(entry.getValue()));
            }
            return new MaterialEffect(type, data);
        }

        public String type() {
            return type;
        }

        public Map<String, Object> data() {
            return data;
        }

        public Object get(String key) {
            return data.get(key);
        }
    }

    private final String id;
    private final String displayName;
    private final List<String> description;
    private final ItemSource source;
    private final int capacityCost;
    private final int priority;
    private final List<MaterialEffect> effects;

    public ForgeMaterial(String id,
                         String displayName,
                         List<String> description,
                         ItemSource source,
                         int capacityCost,
                         int priority,
                         List<MaterialEffect> effects) {
        this.id = id;
        this.displayName = displayName;
        this.description = List.copyOf(description);
        this.source = source;
        this.capacityCost = capacityCost;
        this.priority = priority;
        this.effects = List.copyOf(effects);
    }

    public static ForgeMaterial fromConfig(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String id = section.getString("id");
        if (Texts.isBlank(id)) {
            return null;
        }
        ItemSource source = ItemSourceUtil.parse(section);
        if (source == null) {
            return null;
        }
        List<MaterialEffect> effects = new ArrayList<>();
        for (Object raw : ConfigNodes.asObjectList(section.get("effects"))) {
            MaterialEffect effect = MaterialEffect.fromConfig(raw);
            if (effect != null) {
                effects.add(effect);
            }
        }
        return new ForgeMaterial(
            id,
            section.getString("display_name", id),
            Texts.asStringList(section.get("description")),
            source,
            Numbers.roundToInt(ExpressionEngine.evaluateRandomConfig(section.get("capacity_cost"))),
            Numbers.roundToInt(ExpressionEngine.evaluateRandomConfig(section.get("priority"))),
            effects
        );
    }

    public int forgeCapacityBonus() {
        int total = 0;
        for (MaterialEffect effect : effects) {
            if (effect == null || !isForgeCapacityBonusEffect(effect.type())) {
                continue;
            }
            total += resolveEffectAmount(effect);
        }
        return Math.max(0, total);
    }

    public boolean expandsForgeCapacity() {
        return forgeCapacityBonus() > 0;
    }

    public int effectiveCapacityCost() {
        return expandsForgeCapacity() ? 0 : capacityCost;
    }

    public Map<String, Double> statContributions() {
        Map<String, Double> result = new LinkedHashMap<>();
        for (MaterialEffect effect : effects) {
            if (!"stat_contribution".equals(Texts.lower(effect.type()))) {
                continue;
            }
            for (Map.Entry<String, Object> entry : ConfigNodes.entries(effect.get("stats")).entrySet()) {
                double value = ExpressionEngine.evaluateRandomConfig(entry.getValue());
                result.merge(entry.getKey(), value, Double::sum);
            }
        }
        return result;
    }

    public List<Map<String, Object>> nameModifications() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MaterialEffect effect : effects) {
            if ("name_modify".equals(Texts.lower(effect.type()))) {
                result.add(effect.data());
            }
        }
        return result;
    }

    public List<Map<String, Object>> loreActions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MaterialEffect effect : effects) {
            if (!"lore_action".equals(Texts.lower(effect.type()))) {
                continue;
            }
            for (Object raw : ConfigNodes.asObjectList(effect.get("action"))) {
                Object plain = ConfigNodes.toPlainData(raw);
                if (!(plain instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                result.add(normalized);
            }
        }
        return result;
    }

    private static boolean isForgeCapacityBonusEffect(String type) {
        String normalized = Texts.lower(type);
        return "forge_capacity_bonus".equals(normalized)
            || "capacity_bonus".equals(normalized)
            || "capacity_expand".equals(normalized)
            || "forge_capacity".equals(normalized);
    }

    private static int resolveEffectAmount(MaterialEffect effect) {
        if (effect == null) {
            return 0;
        }
        Object raw = effect.get("amount");
        if (raw == null) {
            raw = effect.get("bonus");
        }
        if (raw == null) {
            raw = effect.get("value");
        }
        if (raw == null) {
            raw = effect.get("capacity");
        }
        if (raw == null) {
            return 0;
        }
        return Numbers.roundToInt(ExpressionEngine.evaluateRandomConfig(raw));
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> description() {
        return description;
    }

    public ItemSource source() {
        return source;
    }

    public int capacityCost() {
        return capacityCost;
    }

    public int priority() {
        return priority;
    }

    public List<MaterialEffect> effects() {
        return effects;
    }
}
