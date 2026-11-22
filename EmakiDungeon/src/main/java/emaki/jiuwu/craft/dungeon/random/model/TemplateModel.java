package emaki.jiuwu.craft.dungeon.random.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public final class TemplateModel {

    public static final String TYPE_ROOM = "room";
    public static final String TYPE_CORRIDOR = "corridor";

    public static final String FACING_N = "north";
    public static final String FACING_E = "east";
    public static final String FACING_S = "south";
    public static final String FACING_W = "west";

    public static final String POLICY_SEAL = "seal";
    public static final String POLICY_KEEP = "keep";
    public static final String POLICY_REJECT = "reject";

    public static final String INTRUSION_ALLOW = "allow";
    public static final String INTRUSION_REJECT = "reject";

    public record DoorAnchor(String id, int x, int y, int z, String facing, int width, int height,
            int baseY, int floor, String policy, String sealBlock) {

        public DoorAnchor {
            facing = normalizeFacing(facing);
            policy = policy == null || policy.isBlank() ? "" : normalizePolicy(policy);
        }

        public String policyOrDefault(String fallback) {
            return policy == null || policy.isBlank() ? fallback : policy;
        }
    }

    public record ModuleNode(String moduleType, String nodeId, String anchorId,
            double dx, double dy, double dz, double yaw, Map<String, Object> params) {

        public ModuleNode {
            anchorId = anchorId == null ? "" : anchorId;
            params = params == null ? Map.of() : new LinkedHashMap<>(params);
        }
    }

    public static String normalizeFacing(String raw) {
        if (raw == null) {
            return FACING_N;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "north" -> FACING_N;
            case "east" -> FACING_E;
            case "south" -> FACING_S;
            case "west" -> FACING_W;
            default -> FACING_N;
        };
    }

    public static String normalizePolicy(String raw) {
        if (raw == null) {
            return POLICY_SEAL;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case POLICY_KEEP -> POLICY_KEEP;
            case POLICY_REJECT -> POLICY_REJECT;
            default -> POLICY_SEAL;
        };
    }

    public static String normalizeIntrusion(String raw) {
        if (raw != null && INTRUSION_REJECT.equalsIgnoreCase(raw.trim())) {
            return INTRUSION_REJECT;
        }
        return INTRUSION_ALLOW;
    }

    public static int facingDx(String facing) {
        return switch (normalizeFacing(facing)) {
            case FACING_E -> 1;
            case FACING_W -> -1;
            default -> 0;
        };
    }

    public static int facingDz(String facing) {
        return switch (normalizeFacing(facing)) {
            case FACING_S -> 1;
            case FACING_N -> -1;
            default -> 0;
        };
    }

    public static String oppositeFacing(String facing) {
        return switch (normalizeFacing(facing)) {
            case FACING_N -> FACING_S;
            case FACING_S -> FACING_N;
            case FACING_E -> FACING_W;
            default -> FACING_E;
        };
    }

    public static String rotateFacing(String facing, int rotations) {
        String current = normalizeFacing(facing);
        int steps = Math.floorMod(rotations, 4);
        for (int i = 0; i < steps; i++) {
            current = switch (current) {
                case FACING_N -> FACING_E;
                case FACING_E -> FACING_S;
                case FACING_S -> FACING_W;
                default -> FACING_N;
            };
        }
        return current;
    }

    public static String mirrorFacing(String facing) {
        return switch (normalizeFacing(facing)) {
            case FACING_E -> FACING_W;
            case FACING_W -> FACING_E;
            default -> normalizeFacing(facing);
        };
    }

    public static long packCell(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (long) (z & 0x3FFFFF);
    }

    public static int unpackX(long packed) {
        int value = (int) (packed >> 42) & 0x3FFFFF;
        return value >= 0x200000 ? value - 0x400000 : value;
    }

    public static int unpackY(long packed) {
        int value = (int) (packed >> 21) & 0x1FFFFF;
        return value >= 0x100000 ? value - 0x200000 : value;
    }

    public static int unpackZ(long packed) {
        int value = (int) packed & 0x3FFFFF;
        return value >= 0x200000 ? value - 0x400000 : value;
    }

    public static List<Object> anchorToMap(DoorAnchor anchor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", anchor.id());
        map.put("x", anchor.x());
        map.put("y", anchor.y());
        map.put("z", anchor.z());
        map.put("facing", anchor.facing());
        map.put("width", anchor.width());
        map.put("height", anchor.height());
        map.put("base-y", anchor.baseY());
        map.put("floor", anchor.floor());
        if (anchor.policy() != null && !anchor.policy().isBlank()) {
            map.put("policy", anchor.policy());
        }
        if (anchor.sealBlock() != null && !anchor.sealBlock().isBlank()) {
            map.put("seal-block", anchor.sealBlock());
        }
        return new ArrayList<>(List.of(map));
    }

    private TemplateModel() {
    }
}
