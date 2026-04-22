package emaki.jiuwu.craft.gem.model;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record GemItemInstance(String gemId, int level, long updatedAt) {

    public GemItemInstance {
        gemId = Texts.lower(gemId);
        level = Math.max(1, level);
        updatedAt = updatedAt <= 0L ? System.currentTimeMillis() : updatedAt;
    }

    public String token() {
        return gemId + ":" + level;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("gem_id", gemId);
        map.put("level", level);
        map.put("updated_at", updatedAt);
        return map;
    }

    public static GemItemInstance fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String gemId = Texts.lower(map.get("gem_id"));
        if (Texts.isBlank(gemId)) {
            return null;
        }
        return new GemItemInstance(
                gemId,
                Numbers.tryParseInt(map.get("level"), 1),
                Numbers.tryParseLong(map.get("updated_at"), System.currentTimeMillis())
        );
    }

    public static GemItemInstance fromToken(String token) {
        if (Texts.isBlank(token)) {
            return null;
        }
        String[] parts = Texts.toStringSafe(token).split(":", 2);
        String gemId = Texts.lower(parts[0]);
        if (Texts.isBlank(gemId)) {
            return null;
        }
        int level = parts.length >= 2 ? Numbers.tryParseInt(parts[1], 1) : 1;
        return new GemItemInstance(gemId, level, System.currentTimeMillis());
    }
}
