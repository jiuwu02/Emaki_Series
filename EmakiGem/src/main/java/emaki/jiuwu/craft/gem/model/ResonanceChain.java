package emaki.jiuwu.craft.gem.model;

import java.util.List;

import emaki.jiuwu.craft.corelib.text.Texts;

public record ResonanceChain(
        String mode,
        List<ResonancePatternEntry> pattern) {

    public ResonanceChain {
        mode = normalizeMode(mode);
        pattern = pattern == null ? List.of() : List.copyOf(pattern);
    }

    public boolean isOrdered() {
        return "ordered".equals(mode);
    }

    public boolean isUnordered() {
        return "unordered".equals(mode);
    }

    private static String normalizeMode(String mode) {
        String lower = Texts.lower(mode);
        return "ordered".equals(lower) ? "ordered" : "unordered";
    }
}
