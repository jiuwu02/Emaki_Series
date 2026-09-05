package emaki.jiuwu.craft.forge.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.matcher.MatcherDigest;
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
        List<ItemSourceRef> sources = ItemRequirement.parseSources(ConfigNodes.get(raw, "item_sources"));
        String item = sources.isEmpty() ? ConfigNodes.string(raw, "item", "") : emaki.jiuwu.craft.corelib.item.ItemSourceUtil.toShorthand(sources.get(0));
        Object matcherNode = ConfigNodes.get(raw, "matcher");
        Matcher matcher = matcherNode == null ? null : Matcher.fromConfig(matcherNode);
        String matcherKey = matcher == null ? "" : MatcherDigest.of(matcherNode);
        if (sources.isEmpty() && Texts.isBlank(matcherKey)) {
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
        String declaredMaterialId = ConfigNodes.string(raw, "material_id", null);
        String declaredCountKey = ConfigNodes.string(raw, "count_key", null);
        String declaredAuditId = ConfigNodes.string(raw, "audit_id", null);
        String materialId = Texts.isNotBlank(declaredMaterialId) ? declaredMaterialId : "";
        String countKey = Texts.isNotBlank(declaredCountKey) ? declaredCountKey : "";
        String auditId = Texts.isNotBlank(declaredAuditId) ? declaredAuditId : "";
        String legacyIdentity = ItemRequirement.sourceIdentity(sources);
        if (Texts.isBlank(materialId)) {
            materialId = legacyIdentity;
        }
        if (Texts.isBlank(countKey)) {
            countKey = legacyIdentity;
        }
        if (Texts.isBlank(auditId)) {
            auditId = legacyIdentity;
        }
        return new ForgeMaterial(item, amount, ConfigNodes.bool(raw, "optional", false),
                Numbers.roundToInt(ExpressionEngine.evaluateRandomConfig(ConfigNodes.get(raw, "capacity_cost"))),
                effects, sources, matcher, matcherKey, materialId, countKey, auditId,
                Texts.isNotBlank(declaredMaterialId), Texts.isNotBlank(declaredCountKey), Texts.isNotBlank(declaredAuditId));
    }

    private static String identity(Object raw, String... keys) {
        for (String key : keys) {
            String value = ConfigNodes.string(raw, key, null);
            if (Texts.isNotBlank(value)) {
                return value;
            }
        }
        return "";
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
            if (!"type".equals(entry.getKey())) {
                data.put(entry.getKey(), ConfigNodes.toPlainData(entry.getValue()));
            }
        }
        return new MaterialEffect(type, data);
    }
}
