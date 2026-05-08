package emaki.jiuwu.craft.gem.model;

import emaki.jiuwu.craft.corelib.text.Texts;

public record ResonanceNameModification(
        String position,
        String template) {

    public ResonanceNameModification {
        position = normalizePosition(position);
        template = Texts.toStringSafe(template);
    }

    public boolean isPrefix() {
        return "prefix".equals(position);
    }

    public boolean isSuffix() {
        return "suffix".equals(position);
    }

    private static String normalizePosition(String position) {
        String lower = Texts.lower(position);
        return "suffix".equals(lower) ? "suffix" : "prefix";
    }
}
