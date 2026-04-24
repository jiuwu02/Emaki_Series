package emaki.jiuwu.craft.corelib.assembly;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record EmakiNameContribution(String slotId,
        NamePosition position,
        int order,
        String contentTemplate,
        String sourceNamespace) {

    public EmakiNameContribution {
        slotId = Texts.isBlank(slotId) ? "unknown" : Texts.toStringSafe(slotId);
        position = position == null ? NamePosition.POSTFIX : position;
        order = Math.max(0, order);
        contentTemplate = Texts.toStringSafe(contentTemplate);
        sourceNamespace = Texts.toStringSafe(sourceNamespace);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slot_id", slotId);
        map.put("position", position.name());
        map.put("order", order);
        map.put("content_template", contentTemplate);
        map.put("source_namespace", sourceNamespace);
        return map;
    }

    public static EmakiNameContribution fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String slotId = Texts.toStringSafe(map.get("slot_id"));
        if (Texts.isBlank(slotId)) {
            return null;
        }
        return new EmakiNameContribution(
                slotId,
                NamePosition.fromValue(map.get("position")),
                Numbers.tryParseInt(map.get("order"), 0),
                Texts.toStringSafe(map.get("content_template")),
                Texts.toStringSafe(map.get("source_namespace"))
        );
    }
}
