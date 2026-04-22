package emaki.jiuwu.craft.corelib.assembly;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record EmakiLoreSectionContribution(String sectionId,
        int order,
        List<String> lines,
        String sourceNamespace) {

    public EmakiLoreSectionContribution {
        sectionId = Texts.isBlank(sectionId) ? "unknown" : Texts.toStringSafe(sectionId);
        order = Math.max(0, order);
        lines = lines == null || lines.isEmpty() ? List.of() : List.copyOf(lines);
        sourceNamespace = Texts.toStringSafe(sourceNamespace);
    }

    public boolean isEmpty() {
        return lines == null || lines.isEmpty();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("section_id", sectionId);
        map.put("order", order);
        map.put("lines", lines);
        map.put("source_namespace", sourceNamespace);
        return map;
    }

    public static EmakiLoreSectionContribution fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String sectionId = Texts.toStringSafe(map.get("section_id"));
        if (Texts.isBlank(sectionId)) {
            return null;
        }
        return new EmakiLoreSectionContribution(
                sectionId,
                Numbers.tryParseInt(map.get("order"), 0),
                Texts.asStringList(ConfigNodes.toPlainData(map.get("lines"))),
                Texts.toStringSafe(map.get("source_namespace"))
        );
    }
}
