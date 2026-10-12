package emaki.jiuwu.craft.structure.furniture;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.structure.integration.FurnitureIdAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FurnitureSidecarParser {

    public static final int CURRENT_VERSION = 1;

    public record Issue(int index, String reasonKey, Map<String, Object> placeholders) {
    }

    public record Result(String templateId, boolean fileRejected, String fileRejectKey,
                         Map<String, Object> fileRejectPlaceholders,
                         List<FurnitureEntry> entries, List<Issue> issues) {
    }

    private FurnitureSidecarParser() {
    }

    public static Result emptyResult(String templateId) {
        return new Result(templateId, false, null, Map.of(), List.of(), List.of());
    }

    public static Result fileRejectedResult(String templateId, String reasonKey,
            Map<String, Object> placeholders) {
        return new Result(templateId, true, reasonKey, placeholders, List.of(), List.of());
    }

    public static Result parse(YamlSection section, String fileName, String templateId,
            int sizeX, int sizeY, int sizeZ) {
        Object rawVersion = section.get("version");
        if (rawVersion instanceof Number number && number.intValue() != CURRENT_VERSION) {
            return fileRejectedResult(templateId, "error.furniture.version_unsupported",
                    Map.of("file", fileName, "value", String.valueOf(number.intValue())));
        }
        Object rawEnabled = section.get("enabled");
        if (Boolean.FALSE.equals(rawEnabled)) {
            return emptyResult(templateId);
        }
        Object rawList = section.get("furniture");
        if (rawList == null) {
            return emptyResult(templateId);
        }
        if (!(rawList instanceof List<?> list)) {
            return fileRejectedResult(templateId, "error.furniture.list_invalid",
                    Map.of("file", fileName));
        }
        List<FurnitureEntry> entries = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            Object rawEntry = list.get(index);
            if (!(rawEntry instanceof Map<?, ?> map)) {
                issues.add(new Issue(index, "error.furniture.entry_invalid", Map.of()));
                continue;
            }
            FurnitureEntry entry = parseEntry(map);
            if (entry == null) {
                issues.add(new Issue(index, "error.furniture.entry_invalid", Map.of()));
                continue;
            }
            if (!FurnitureIdAdapter.knownNamespace(FurnitureIdAdapter.namespaceOf(entry.id()))) {
                issues.add(new Issue(index, "error.furniture.id_prefix",
                        Map.of("id", entry.id())));
                continue;
            }
            if (entry.x() < 0 || entry.x() >= sizeX
                    || entry.y() < 0 || entry.y() >= sizeY
                    || entry.z() < 0 || entry.z() >= sizeZ) {
                issues.add(new Issue(index, "error.furniture.pos_invalid",
                        Map.of("id", entry.id())));
                continue;
            }
            if (!(entry.yaw() >= 0.0d) || !(entry.yaw() < 360.0d)) {
                issues.add(new Issue(index, "error.furniture.yaw_invalid",
                        Map.of("id", entry.id(), "value", String.valueOf(entry.yaw()))));
                continue;
            }
            entries.add(entry);
        }
        return new Result(templateId, false, null, Map.of(), List.copyOf(entries),
                List.copyOf(issues));
    }

    private static FurnitureEntry parseEntry(Map<?, ?> map) {
        Object rawId = map.get("id");
        if (!(rawId instanceof String id) || id.isBlank()) {
            return null;
        }
        int[] pos = position(map.get("pos"));
        if (pos == null) {
            return null;
        }
        double yaw = 0.0d;
        Object rawYaw = map.get("yaw");
        if (rawYaw != null) {
            if (!(rawYaw instanceof Number number)) {
                return null;
            }
            yaw = number.doubleValue();
        }
        return new FurnitureEntry(id.toLowerCase(Locale.ROOT), pos[0], pos[1], pos[2],
                yaw, facingOf(yaw));
    }

    private static int[] position(Object raw) {
        if (!(raw instanceof List<?> values) || values.size() < 3) {
            return null;
        }
        if (!(values.get(0) instanceof Number x)
                || !(values.get(1) instanceof Number y)
                || !(values.get(2) instanceof Number z)) {
            return null;
        }
        return new int[] { x.intValue(), y.intValue(), z.intValue() };
    }

    public static double normalizeYaw(double yaw) {
        double normalized = yaw % 360.0d;
        if (normalized < 0.0d) {
            normalized += 360.0d;
        }
        return normalized;
    }

    public static String facingOf(double yaw) {
        int quadrant = (int) Math.floor(normalizeYaw(yaw) / 90.0d + 0.5d) % 4;
        return switch (quadrant) {
            case 0 -> "south";
            case 1 -> "west";
            case 2 -> "north";
            default -> "east";
        };
    }

    public static String encode(List<FurnitureEntry> entries) {
        StringBuilder builder = new StringBuilder();
        builder.append("version: ").append(CURRENT_VERSION).append('\n');
        builder.append("enabled: true\n");
        builder.append("furniture:\n");
        for (FurnitureEntry entry : entries) {
            builder.append("  - id: \"").append(entry.id()).append("\"\n");
            builder.append("    pos: [").append(entry.x()).append(", ")
                    .append(entry.y()).append(", ").append(entry.z()).append("]\n");
            builder.append("    yaw: ")
                    .append(String.format(Locale.ROOT, "%.1f", entry.yaw())).append('\n');
            builder.append("    facing: \"").append(entry.facing()).append("\"\n");
        }
        return builder.toString();
    }
}
