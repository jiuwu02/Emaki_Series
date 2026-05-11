package emaki.jiuwu.craft.gem.model;

import emaki.jiuwu.craft.corelib.text.Texts;

public record ResonancePatternEntry(
        String id,
        String type,
        int minLevel) {

    public ResonancePatternEntry(String id, String type) {
        this(id, type, 0);
    }

    public ResonancePatternEntry {
        id = Texts.isBlank(id) ? "" : Texts.lower(id);
        type = Texts.isBlank(type) ? "" : Texts.lower(type);
        minLevel = Math.max(0, minLevel);
    }

    public boolean matchesAny() {
        return id.isEmpty() && type.isEmpty();
    }

    public boolean matches(GemDefinition gem) {
        return matches(gem, Integer.MAX_VALUE);
    }

    public boolean matches(GemDefinition gem, int gemLevel) {
        if (gem == null) {
            return false;
        }
        if (minLevel > 0 && gemLevel < minLevel) {
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
