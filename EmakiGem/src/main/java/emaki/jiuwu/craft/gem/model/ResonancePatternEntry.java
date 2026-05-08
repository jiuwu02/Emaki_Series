package emaki.jiuwu.craft.gem.model;

import emaki.jiuwu.craft.corelib.text.Texts;

public record ResonancePatternEntry(
        String id,
        String type) {

    public ResonancePatternEntry {
        id = Texts.isBlank(id) ? "" : Texts.lower(id);
        type = Texts.isBlank(type) ? "" : Texts.lower(type);
    }

    public boolean matchesAny() {
        return id.isEmpty() && type.isEmpty();
    }

    public boolean matches(GemDefinition gem) {
        if (gem == null) {
            return false;
        }
        if (!id.isEmpty()) {
            return id.equals(gem.id());
        }
        if (!type.isEmpty()) {
            return type.equals(gem.gemType());
        }
        return true;
    }
}
