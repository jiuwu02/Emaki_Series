package emaki.jiuwu.craft.corelib.assembly;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record EmakiPresentationEntry(String type, String anchor, String template, int sequence, String sourceId) {

    public EmakiPresentationEntry     {
        type = Texts.isBlank(type) ? "unknown" : Texts.lower(type);
        anchor = Texts.toStringSafe(anchor);
        template = Texts.toStringSafe(template);
        sourceId = Texts.toStringSafe(sourceId);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("anchor", anchor);
        map.put("template", template);
        map.put("sequence", sequence);
        map.put("source_id", sourceId);
        return map;
    }

    public static EmakiPresentationEntry fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String type = Texts.toStringSafe(map.get("type"));
        if (Texts.isBlank(type)) {
            return null;
        }
        return new EmakiPresentationEntry(
                type,
                Texts.toStringSafe(map.get("anchor")),
                Texts.toStringSafe(map.get("template")),
                Numbers.tryParseInt(map.get("sequence"), 0),
                Texts.toStringSafe(map.get("source_id"))
        );
    }
}
