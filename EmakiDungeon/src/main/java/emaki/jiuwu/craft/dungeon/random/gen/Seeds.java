package emaki.jiuwu.craft.dungeon.random.gen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record Seeds(long base, long topology, long layout, long assignment, long orientation, long link) {

    private static final long LAYER_TOPOLOGY = 0x9E3779B97F4A7C15L;
    private static final long LAYER_LAYOUT = 0xC2B2AE3D27D4EB4FL;
    private static final long LAYER_ASSIGNMENT = 0x165667B19E3779F9L;
    private static final long LAYER_ORIENTATION = 0x27D4EB2F165667C5L;
    private static final long LAYER_LINK = 0x85EBCA77C2B2AE63L;

    public static Seeds derive(long base) {
        return new Seeds(base,
                mix(base, LAYER_TOPOLOGY),
                mix(base, LAYER_LAYOUT),
                mix(base, LAYER_ASSIGNMENT),
                mix(base, LAYER_ORIENTATION),
                mix(base, LAYER_LINK));
    }

    public static long mix(long seed, long layer) {
        long value = seed ^ layer;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public static String configHash(String canonicalConfig) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonicalConfig.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                builder.append(Character.forDigit((bytes[i] >> 4) & 0xF, 16));
                builder.append(Character.forDigit(bytes[i] & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            return Long.toHexString(canonicalConfig.hashCode());
        }
    }
}
