package emaki.jiuwu.craft.corelib.api.pdc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * SHA-256 content signatures for cache keys, definition fingerprints and change detection.
 *
 * <p>Signatures are the lower-case hex form of the digest. {@link #stableSignature(Object)}
 * canonicalizes the value first: map keys are sorted, so map iteration order never changes the
 * result; integral numbers render without a fraction; floating numbers without trailing zeros;
 * collections keep their iteration order deliberately. Every call builds its own digest, so the
 * class is safe to use from any thread.
 */
public final class SignatureUtil {

    private SignatureUtil() {
    }

    /**
     * Hashes text with SHA-256.
     *
     * @param value text to hash; {@code null} is treated as an empty string
     * @return the 64-character lower-case hex digest, never {@code null}
     * @throws IllegalStateException if the SHA-256 algorithm is unavailable in this JVM
     */
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Texts.toStringSafe(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("创建签名摘要失败", exception);
        }
    }

    /**
     * Hashes the canonical form of a value.
     *
     * <p>Two values that canonicalize to the same text (for example maps that differ only in
     * iteration order) produce the same signature.
     *
     * @param value value to canonicalize; {@code null} canonicalizes to the text {@code null}
     * @return the signature of the canonical text
     */
    public static String stableSignature(Object value) {
        return sha256(canonicalize(value));
    }

    /**
     * Convenience overload of {@link #stableSignature(Object)} for maps; the behavior is
     * identical.
     *
     * @param values map to sign; {@code null} is accepted
     * @return the signature of the canonical map form
     */
    public static String stableSignature(Map<String, ?> values) {
        return stableSignature((Object) values);
    }

    /**
     * Convenience overload of {@link #stableSignature(Object)} for collections; the behavior is
     * identical and the element order is part of the signature.
     *
     * @param values collection to sign; {@code null} is accepted
     * @return the signature of the canonical collection form
     */
    public static String stableSignature(Collection<?> values) {
        return stableSignature((Object) values);
    }

    /**
     * Signs several strings as one ordered list.
     *
     * <p>Each element is stringified ({@code null} becomes an empty string) and the digest is
     * taken over the canonical list form, so reordering the arguments changes the result. A
     * {@code null} or empty array is hashed as the empty text.
     *
     * @param values ordered parts; may be {@code null} or empty
     * @return the combined signature
     */
    public static String combine(String... values) {
        if (values == null || values.length == 0) {
            return sha256("");
        }
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            parts.add(Texts.toStringSafe(value));
        }
        return stableSignature(parts);
    }

    private static String canonicalize(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Number number) {
            return canonicalNumber(number);
        }
        if (value instanceof Boolean bool) {
            return Boolean.toString(bool);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                normalized.put(String.valueOf(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
            }
            List<Map.Entry<String, Object>> entries = new ArrayList<>(normalized.entrySet());
            entries.sort(Comparator.comparing(Map.Entry::getKey));
            StringBuilder builder = new StringBuilder("{");
            for (int index = 0; index < entries.size(); index++) {
                Map.Entry<String, Object> entry = entries.get(index);
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(canonicalize(entry.getKey())).append(':').append(canonicalize(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder builder = new StringBuilder("[");
            int index = 0;
            for (Object entry : collection) {
                if (index++ > 0) {
                    builder.append(',');
                }
                builder.append(canonicalize(ConfigNodes.toPlainData(entry)));
            }
            return builder.append(']').toString();
        }
        if (value instanceof Object[] array) {
            return canonicalize(toList(array));
        }
        if (value instanceof int[] array) {
            return canonicalize(toList(array));
        }
        if (value instanceof long[] array) {
            return canonicalize(toList(array));
        }
        if (value instanceof double[] array) {
            return canonicalize(toList(array));
        }
        if (value instanceof float[] array) {
            return canonicalize(toList(array));
        }
        if (value instanceof boolean[] array) {
            return canonicalize(toList(array));
        }
        if (value instanceof byte[] array) {
            return canonicalize(toList(array));
        }
        if (value instanceof short[] array) {
            return canonicalize(toList(array));
        }
        if (value instanceof char[] array) {
            return canonicalize(toList(array));
        }
        Object plain = ConfigNodes.toPlainData(value);
        if (plain != value) {
            return canonicalize(plain);
        }
        return Texts.toStringSafe(value);
    }

    private static String canonicalNumber(Number number) {
        if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
            return String.valueOf(number.longValue());
        }
        try {
            return BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros().toPlainString();
        } catch (Exception _) {
            return String.valueOf(number);
        }
    }

    private static List<Object> toList(Object[] array) {
        List<Object> entries = new ArrayList<>();
        if (array != null) {
            for (Object entry : array) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static List<Object> toList(int[] array) {
        List<Object> entries = new ArrayList<>();
        for (int entry : array) {
            entries.add(entry);
        }
        return entries;
    }

    private static List<Object> toList(long[] array) {
        List<Object> entries = new ArrayList<>();
        for (long entry : array) {
            entries.add(entry);
        }
        return entries;
    }

    private static List<Object> toList(double[] array) {
        List<Object> entries = new ArrayList<>();
        for (double entry : array) {
            entries.add(entry);
        }
        return entries;
    }

    private static List<Object> toList(float[] array) {
        List<Object> entries = new ArrayList<>();
        for (float entry : array) {
            entries.add(entry);
        }
        return entries;
    }

    private static List<Object> toList(boolean[] array) {
        List<Object> entries = new ArrayList<>();
        for (boolean entry : array) {
            entries.add(entry);
        }
        return entries;
    }

    private static List<Object> toList(byte[] array) {
        List<Object> entries = new ArrayList<>();
        for (byte entry : array) {
            entries.add(entry);
        }
        return entries;
    }

    private static List<Object> toList(short[] array) {
        List<Object> entries = new ArrayList<>();
        for (short entry : array) {
            entries.add(entry);
        }
        return entries;
    }

    private static List<Object> toList(char[] array) {
        List<Object> entries = new ArrayList<>();
        for (char entry : array) {
            entries.add(String.valueOf(entry));
        }
        return entries;
    }
}
