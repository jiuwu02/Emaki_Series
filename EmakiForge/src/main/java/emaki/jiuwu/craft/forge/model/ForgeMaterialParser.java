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
import emaki.jiuwu.craft.forge.model.ForgeMaterial.MaterialEffect;

final class ForgeMaterialParser {

    private ForgeMaterialParser() {
    }

    static ForgeMaterial parse(Object raw) {
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
            MaterialEffect effect = parseMaterialEffect(effectRaw);
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

    static MaterialEffect parseMaterialEffect(Object raw) {
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
}
