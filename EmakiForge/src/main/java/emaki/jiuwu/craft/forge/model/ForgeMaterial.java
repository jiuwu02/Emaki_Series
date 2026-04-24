package emaki.jiuwu.craft.forge.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

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

    public record QualityModifier(String mode, String tier) {

        public static QualityModifier fromEffect(MaterialEffect effect) {
            if (effect == null || !"quality_modify".equals(Texts.lower(effect.type()))) {
                return null;
            }
            String mode = Texts.lower(effect.get("mode"));
            String tier = Texts.toStringSafe(effect.get("tier"));
            if (Texts.isBlank(tier)) {
                tier = Texts.toStringSafe(effect.get("quality"));
            }
            if (Texts.isBlank(mode) || Texts.isBlank(tier)) {
                return null;
            }
            return new QualityModifier(mode, tier);
        }

        public boolean forceMode() {
            return "force".equals(Texts.lower(mode));
        }

        public boolean minimumMode() {
            return "minimum".equals(Texts.lower(mode));
        }
    }

    private final String item;
    private final int amount;
    private final boolean optional;
    private final int capacityCost;
    private final List<MaterialEffect> effects;
    private final ItemSource source;

    public ForgeMaterial(String item,
            int amount,
            boolean optional,
            int capacityCost,
            List<MaterialEffect> effects,
            ItemSource source) {
        this.item = item;
        this.amount = amount;
        this.optional = optional;
        this.capacityCost = capacityCost;
        this.effects = List.copyOf(effects);
        this.source = source;
    }

    public static ForgeMaterial fromConfig(YamlSection section) {
        return fromConfig((Object) section);
    }

    public static ForgeMaterial fromConfig(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof YamlSection section
                && (section.contains("id") || section.contains("display_name") || section.contains("description"))) {
            return null;
        }
        String item = ConfigNodes.string(raw, "item", null);
        if (Texts.isBlank(item)) {
            return null;
        }
        ItemSource source = ItemSourceUtil.parseShorthand(item);
        if (source == null) {
            return null;
        }
        int amount = Numbers.tryParseInt(ConfigNodes.get(raw, "amount"), 1);
        if (amount == 0) {
            return null;
        }
        List<MaterialEffect> effects = new ArrayList<>();
        for (Object effectRaw : ConfigNodes.asObjectList(ConfigNodes.get(raw, "effects"))) {
            MaterialEffect effect = MaterialEffect.fromConfig(effectRaw);
            if (effect == null) {
                return null;
            }
            effects.add(effect);
        }
        return new ForgeMaterial(
                item,
                amount,
                ConfigNodes.bool(raw, "optional", false),
                Numbers.roundToInt(ExpressionEngine.evaluateRandomConfig(ConfigNodes.get(raw, "capacity_cost"))),
                effects,
                source
        );
    }

    public boolean matches(ItemSource other) {
        return other != null && ItemSourceUtil.matches(source, other);
    }

    public String key() {
        String shorthand = ItemSourceUtil.toShorthand(source);
        return shorthand == null ? "" : Texts.lower(shorthand);
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
                double value = resolveStatValue(entry.getValue());
                result.merge(entry.getKey(), value, Double::sum);
            }
        }
        return result;
    }

    public Map<String, Double> eaAttributeContributions() {
        Map<String, Double> result = new LinkedHashMap<>();
        for (MaterialEffect effect : effects) {
            if (!"ea_attribute".equals(Texts.lower(effect.type()))) {
                continue;
            }
            for (Map.Entry<String, Object> entry : ConfigNodes.entries(effect.get("attributes")).entrySet()) {
                Double value = Numbers.tryParseDouble(entry.getValue(), null);
                if (value != null) {
                    result.merge(Texts.lower(entry.getKey()), value, Double::sum);
                }
            }
        }
        return result;
    }

    public Map<String, String> eaAttributeMeta() {
        Map<String, String> result = new LinkedHashMap<>();
        for (MaterialEffect effect : effects) {
            if (!"ea_attribute".equals(Texts.lower(effect.type()))) {
                continue;
            }
            for (Map.Entry<String, Object> entry : ConfigNodes.entries(effect.get("meta")).entrySet()) {
                if (entry.getValue() != null) {
                    result.putIfAbsent(Texts.lower(entry.getKey()), Texts.toStringSafe(entry.getValue()));
                }
            }
        }
        return result;
    }

    public List<String> skillIds() {
        List<String> result = new ArrayList<>();
        for (MaterialEffect effect : effects) {
            if (!"skill".equals(Texts.lower(effect.type()))) {
                continue;
            }
            for (Object rawSkill : ConfigNodes.asObjectList(effect.get("skills"))) {
                String skillId = Texts.normalizeId(Texts.toStringSafe(rawSkill));
                if (Texts.isNotBlank(skillId) && !result.contains(skillId)) {
                    result.add(skillId);
                }
            }
            String skillId = Texts.normalizeId(ConfigNodes.string(effect.data(), "skill", ""));
            if (Texts.isNotBlank(skillId) && !result.contains(skillId)) {
                result.add(skillId);
            }
        }
        return List.copyOf(result);
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

    public List<Object> structuredPresentations() {
        List<Object> result = new ArrayList<>();
        for (MaterialEffect effect : effects) {
            if (!"structured_presentation".equals(Texts.lower(effect.type()))) {
                continue;
            }
            result.add(ConfigNodes.toPlainData(effect.data()));
        }
        return result;
    }

    public List<QualityModifier> qualityModifiers() {
        List<QualityModifier> result = new ArrayList<>();
        for (MaterialEffect effect : effects) {
            QualityModifier modifier = QualityModifier.fromEffect(effect);
            if (modifier != null) {
                result.add(modifier);
            }
        }
        return result;
    }

    public Map<String, Object> definitionSignatureData() {
        List<Map<String, Object>> effectData = new ArrayList<>();
        for (MaterialEffect effect : effects) {
            if (effect == null) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", effect.type());
            map.put("data", effect.data());
            effectData.add(map);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("item", item);
        result.put("amount", amount);
        result.put("optional", optional);
        result.put("capacity_cost", capacityCost);
        result.put("effects", effectData);
        return result;
    }

    private static double resolveStatValue(Object raw) {
        return ExpressionEngine.evaluateRandomConfig(raw);
    }

    private static boolean isForgeCapacityBonusEffect(String type) {
        return "capacity_bonus".equals(Texts.lower(type));
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

    public String item() {
        return item;
    }

    public String id() {
        return key();
    }

    public String displayName() {
        return item;
    }

    public int amount() {
        return amount;
    }

    public boolean optional() {
        return optional;
    }

    public int capacityCost() {
        return capacityCost;
    }

    public int priority() {
        return 0;
    }

    public List<MaterialEffect> effects() {
        return effects;
    }

    public ItemSource source() {
        return source;
    }
}
