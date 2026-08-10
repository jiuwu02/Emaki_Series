package emaki.jiuwu.craft.cooking.service;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;




public final class CookingCompletionStateDigest {

    private static final Set<String> IGNORED_STATION_METADATA = Set.of(
            "station_saved_at_ms",
            "station_format_version",
            "station_state_version",
            "station_tombstone"
    );

    private CookingCompletionStateDigest() {
    }

    public static String digest(Object value) {
        MessageDigest digest = sha256();
        append(digest, value);
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String sha256(Object value) {
        return digest(value);
    }

    private static void append(MessageDigest digest, Object value) {
        if (value == null) {
            token(digest, "null");
            return;
        }
        if (value instanceof Map<?, ?> map) {
            appendMap(digest, map);
            return;
        }
        if (value instanceof Collection<?> collection) {
            appendList(digest, collection);
            return;
        }
        if (value.getClass().isArray()) {
            appendArray(digest, value);
            return;
        }
        if (value instanceof CharSequence sequence) {
            scalar(digest, "string", sequence.toString());
            return;
        }
        if (value instanceof Character character) {
            scalar(digest, "string", character.toString());
            return;
        }
        if (value instanceof Boolean bool) {
            token(digest, bool ? "boolean:true" : "boolean:false");
            return;
        }
        if (value instanceof Number number) {
            scalar(digest, "number", canonicalNumber(number));
            return;
        }
        if (value instanceof Enum<?> enumeration) {
            scalar(digest, "enum", enumeration.name());
            return;
        }
        throw new IllegalArgumentException("Unsupported state value: " + value.getClass().getName());
    }

    private static void appendMap(MessageDigest digest, Map<?, ?> map) {
        token(digest, "map[");
        List<MapEntry> entries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            if (!IGNORED_STATION_METADATA.contains(key)) {
                entries.add(new MapEntry(key, entry.getValue()));
            }
        }
        entries.sort(Comparator.comparing(MapEntry::key));
        scalar(digest, "size", Integer.toString(entries.size()));
        for (MapEntry entry : entries) {
            scalar(digest, "key", entry.key());
            append(digest, entry.value());
        }
        token(digest, "]");
    }

    private static void appendList(MessageDigest digest, Collection<?> list) {
        token(digest, "list[");
        scalar(digest, "size", Integer.toString(list.size()));
        for (Object entry : list) {
            append(digest, entry);
        }
        token(digest, "]");
    }

    private static void appendArray(MessageDigest digest, Object array) {
        token(digest, "list[");
        int length = Array.getLength(array);
        scalar(digest, "size", Integer.toString(length));
        for (int index = 0; index < length; index++) {
            append(digest, Array.get(array, index));
        }
        token(digest, "]");
    }

    private static String canonicalNumber(Number number) {
        if (number instanceof Double value && !Double.isFinite(value)) {
            return Double.toString(value);
        }
        if (number instanceof Float value && !Float.isFinite(value)) {
            return Float.toString(value);
        }
        if (number instanceof BigDecimal decimal) {
            return normalizedDecimal(decimal);
        }
        if (number instanceof BigInteger integer) {
            return integer.toString();
        }
        try {
            return normalizedDecimal(new BigDecimal(number.toString()));
        } catch (NumberFormatException exception) {
            return number.toString();
        }
    }

    private static String normalizedDecimal(BigDecimal decimal) {
        BigDecimal normalized = decimal.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static void scalar(MessageDigest digest, String type, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        token(digest, type + ":" + bytes.length + ":");
        digest.update(bytes);
        token(digest, ";");
    }

    private static void token(MessageDigest digest, String token) {
        digest.update(token.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record MapEntry(String key, Object value) {
    }
}
