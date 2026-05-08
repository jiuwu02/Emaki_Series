package emaki.jiuwu.craft.gem.model;

import java.util.List;

import emaki.jiuwu.craft.corelib.text.Texts;

public record ResonanceLoreSection(
        String sectionId,
        int order,
        List<String> lines) {

    public ResonanceLoreSection {
        sectionId = Texts.isBlank(sectionId) ? "gem_resonance" : Texts.lower(sectionId);
        order = Math.max(0, order);
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
