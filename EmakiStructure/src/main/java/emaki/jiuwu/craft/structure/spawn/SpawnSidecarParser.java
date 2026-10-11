package emaki.jiuwu.craft.structure.spawn;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.structure.config.NameRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SpawnSidecarParser {

    private static final List<String> ALLOWED_PREFIXES = List.of("minecraft:", "mythicmobs:", "emakimobs:");
    private static final List<String> ALLOWED_FACING = List.of("north", "south", "east", "west");
    private static final int MAX_RADIUS = 64;
    private static final int MAX_COUNT = 16;

    public record Issue(int index, String reasonKey, Map<String, Object> placeholders) {
    }

    public record Result(String templateId,
                         boolean enabled,
                         boolean fileRejected,
                         String fileRejectKey,
                         Map<String, Object> fileRejectPlaceholders,
                         List<SpawnEntry> entries,
                         List<Issue> issues) {

        public int totalRequested() {
            int total = 0;
            for (SpawnEntry entry : entries) {
                total += entry.count();
            }
            return total;
        }
    }

    private SpawnSidecarParser() {
    }

    public static Result emptyResult(String templateId) {
        return empty(templateId, false);
    }

    public static Result fileRejectedResult(String templateId, String reasonKey,
            Map<String, Object> placeholders) {
        return fileRejected(templateId, reasonKey, placeholders);
    }

    public static Result parse(YamlSection section, String fileName, int sizeX, int sizeY, int sizeZ,
            int maxPerGeneration) {
        String templateId = stripExtension(fileName);
        if (!NameRules.isValid(templateId)) {
            return fileRejected(templateId, "error.name.invalid", Map.of("value", String.valueOf(fileName)));
        }
        if (section == null) {
            return empty(templateId, false);
        }
        boolean enabled = section.getBoolean("enabled", true);
        if (!enabled) {
            return empty(templateId, false);
        }
        List<SpawnEntry> entries = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        List<Map<?, ?>> rawEntries = section.getMapList("spawns");
        for (int index = 0; index < rawEntries.size(); index++) {
            Map<?, ?> raw = rawEntries.get(index);
            SpawnEntry parsed = parseEntry(raw, index, sizeX, sizeY, sizeZ, issues);
            if (parsed != null) {
                entries.add(parsed);
            }
        }
        int requested = 0;
        for (SpawnEntry entry : entries) {
            requested += entry.count();
        }
        if (requested > maxPerGeneration) {
            return fileRejected(templateId, "error.spawn.total_exceeded",
                    Map.of("total", requested, "limit", maxPerGeneration));
        }
        return new Result(templateId, true, false, null, Map.of(), List.copyOf(entries), List.copyOf(issues));
    }

    private static SpawnEntry parseEntry(Map<?, ?> raw, int index, int sizeX, int sizeY, int sizeZ,
            List<Issue> issues) {
        Object typeValue = raw.get("type");
        if (!(typeValue instanceof String typeText)) {
            issues.add(issue(index, "error.spawn.type_missing", Map.of()));
            return null;
        }
        String type = typeText.trim().toLowerCase(Locale.ROOT);
        if (!hasAllowedPrefix(type)) {
            issues.add(issue(index, "error.spawn.type_prefix",
                    Map.of("type", type, "allowed", String.join(", ", ALLOWED_PREFIXES))));
            return null;
        }
        boolean hasPos = raw.containsKey("pos");
        boolean hasRegion = raw.containsKey("region");
        if (hasPos == hasRegion) {
            issues.add(issue(index, "error.spawn.pos_region_exclusive", Map.of("type", type)));
            return null;
        }
        int count = intValue(raw.get("count"), -1);
        if (count < 1 || count > MAX_COUNT) {
            issues.add(issue(index, "error.spawn.count_range",
                    Map.of("type", type, "value", String.valueOf(raw.get("count")), "expected", "1-" + MAX_COUNT)));
            return null;
        }
        double chance = doubleValue(raw.get("chance"), 1.0d);
        if (Double.isNaN(chance) || chance < 0.0d || chance > 1.0d) {
            issues.add(issue(index, "error.spawn.chance_range",
                    Map.of("type", type, "value", String.valueOf(raw.get("chance")))));
            return null;
        }
        String facing = raw.get("facing") instanceof String text ? text.trim().toLowerCase(Locale.ROOT) : null;
        if (facing != null && !ALLOWED_FACING.contains(facing)) {
            issues.add(issue(index, "error.spawn.facing_invalid",
                    Map.of("type", type, "value", facing, "allowed", String.join(", ", ALLOWED_FACING))));
            return null;
        }
        Integer level = raw.get("level") instanceof Number number ? number.intValue() : null;

        if (hasRegion) {
            return parseRegion(raw, index, type, sizeX, sizeY, sizeZ, count, chance, facing, level, issues);
        }
        return parsePosition(raw, index, type, sizeX, sizeY, sizeZ, count, chance, facing, level, issues);
    }

    private static SpawnEntry parsePosition(Map<?, ?> raw, int index, String type,
            int sizeX, int sizeY, int sizeZ, int count, double chance, String facing, Integer level,
            List<Issue> issues) {
        int[] pos = intTriple(raw.get("pos"));
        if (pos == null) {
            issues.add(issue(index, "error.spawn.pos_invalid", Map.of("type", type)));
            return null;
        }
        if (!inside(pos, sizeX, sizeY, sizeZ)) {
            issues.add(issue(index, "error.spawn.pos_out_of_bounds",
                    Map.of("type", type, "size", sizeX + "x" + sizeY + "x" + sizeZ)));
            return null;
        }
        int radius = 0;
        if (raw.containsKey("radius")) {
            if (!isWholeNumber(raw.get("radius"))) {
                issues.add(issue(index, "error.spawn.radius_range",
                        Map.of("type", type, "value", String.valueOf(raw.get("radius")),
                                "expected", "0-" + MAX_RADIUS)));
                return null;
            }
            radius = ((Number) raw.get("radius")).intValue();
        }
        if (radius < 0 || radius > MAX_RADIUS) {
            issues.add(issue(index, "error.spawn.radius_range",
                    Map.of("type", type, "value", String.valueOf(raw.get("radius")), "expected", "0-" + MAX_RADIUS)));
            return null;
        }
        if (radius > 0 && !sphereInside(pos, radius, sizeX, sizeY, sizeZ)) {
            issues.add(issue(index, "error.spawn.radius_out_of_bounds",
                    Map.of("type", type, "radius", radius)));
            return null;
        }
        SpawnEntry.Mode mode = radius > 0 ? SpawnEntry.Mode.SPHERE : SpawnEntry.Mode.FIXED;
        return new SpawnEntry(type, mode, pos, radius, new int[0], new int[0], count, chance, facing, level);
    }

    private static SpawnEntry parseRegion(Map<?, ?> raw, int index, String type,
            int sizeX, int sizeY, int sizeZ, int count, double chance, String facing, Integer level,
            List<Issue> issues) {
        if (!(raw.get("region") instanceof Map<?, ?> region)) {
            issues.add(issue(index, "error.spawn.region_invalid", Map.of("type", type)));
            return null;
        }
        int[] min = intTriple(region.get("min"));
        int[] max = intTriple(region.get("max"));
        if (min == null || max == null) {
            issues.add(issue(index, "error.spawn.region_invalid", Map.of("type", type)));
            return null;
        }
        for (int axis = 0; axis < 3; axis++) {
            if (min[axis] > max[axis]) {
                issues.add(issue(index, "error.spawn.region_order", Map.of("type", type)));
                return null;
            }
            if (min[axis] < 0) {
                issues.add(issue(index, "error.spawn.region_out_of_bounds",
                        Map.of("type", type, "size", sizeX + "x" + sizeY + "x" + sizeZ)));
                return null;
            }
        }
        if (max[0] >= sizeX || max[1] >= sizeY || max[2] >= sizeZ) {
            issues.add(issue(index, "error.spawn.region_out_of_bounds",
                    Map.of("type", type, "size", sizeX + "x" + sizeY + "x" + sizeZ)));
            return null;
        }
        return new SpawnEntry(type, SpawnEntry.Mode.REGION, new int[0], 0, min, max,
                count, chance, facing, level);
    }

    private static boolean sphereInside(int[] pos, int radius, int sizeX, int sizeY, int sizeZ) {
        return pos[0] - radius >= 0 && pos[0] + radius < sizeX
                && pos[1] - radius >= 0 && pos[1] + radius < sizeY
                && pos[2] - radius >= 0 && pos[2] + radius < sizeZ;
    }

    private static boolean inside(int[] pos, int sizeX, int sizeY, int sizeZ) {
        return pos[0] >= 0 && pos[0] < sizeX
                && pos[1] >= 0 && pos[1] < sizeY
                && pos[2] >= 0 && pos[2] < sizeZ;
    }

    private static boolean hasAllowedPrefix(String type) {
        for (String prefix : ALLOWED_PREFIXES) {
            if (type.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static int[] intTriple(Object value) {
        if (!(value instanceof List<?> list) || list.size() < 3) {
            return null;
        }
        if (!(list.get(0) instanceof Number first)
                || !(list.get(1) instanceof Number second)
                || !(list.get(2) instanceof Number third)) {
            return null;
        }
        return new int[] { first.intValue(), second.intValue(), third.intValue() };
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean isWholeNumber(Object value) {
        return value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte;
    }

    private static double doubleValue(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static Issue issue(int index, String reasonKey, Map<String, Object> placeholders) {
        return new Issue(index, reasonKey, placeholders);
    }

    private static Result fileRejected(String templateId, String reasonKey, Map<String, Object> placeholders) {
        return new Result(templateId, false, true, reasonKey, placeholders, List.of(), List.of());
    }

    private static Result empty(String templateId, boolean enabled) {
        return new Result(templateId, enabled, false, null, Map.of(), List.of(), List.of());
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
