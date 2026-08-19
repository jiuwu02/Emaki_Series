package emaki.jiuwu.craft.item.api;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.bukkit.persistence.PersistentDataType;

/** Supported persistent item-state value types. */
public enum ItemStateType {
    INTEGER(Integer.class, PersistentDataType.INTEGER),
    LONG(Long.class, PersistentDataType.LONG),
    DOUBLE(Double.class, PersistentDataType.DOUBLE),
    BOOLEAN(Boolean.class, PersistentDataType.BYTE),
    STRING(String.class, PersistentDataType.STRING);

    private final Class<?> javaType;
    private final PersistentDataType<?, ?> persistentType;

    ItemStateType(Class<?> javaType, PersistentDataType<?, ?> persistentType) {
        this.javaType = javaType;
        this.persistentType = persistentType;
    }

    public Class<?> javaType() {
        return javaType;
    }

    public PersistentDataType<?, ?> persistentType() {
        return persistentType;
    }

    public boolean numeric() {
        return this == INTEGER || this == LONG || this == DOUBLE;
    }

    public Object coerce(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return switch (this) {
                case INTEGER -> coerceInteger(value);
                case LONG -> coerceLong(value);
                case DOUBLE -> coerceDouble(value);
                case BOOLEAN -> coerceBoolean(value);
                case STRING -> value instanceof CharSequence sequence ? sequence.toString() : null;
            };
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    private static Integer coerceInteger(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        BigInteger integer = integralValue(value);
        return integer == null ? null : integer.intValueExact();
    }

    private static Long coerceLong(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        BigInteger integer = integralValue(value);
        return integer == null ? null : integer.longValueExact();
    }

    private static Double coerceDouble(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double converted = number.doubleValue();
        return Double.isFinite(converted) ? converted : null;
    }

    private static Boolean coerceBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            BigInteger integer = integralValue(number);
            if (BigInteger.ZERO.equals(integer)) {
                return false;
            }
            if (BigInteger.ONE.equals(integer)) {
                return true;
            }
        }
        if (value instanceof CharSequence sequence) {
            String normalized = sequence.toString().trim();
            if ("true".equalsIgnoreCase(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equalsIgnoreCase(normalized) || "0".equals(normalized)) {
                return false;
            }
        }
        return null;
    }

    private static BigInteger integralValue(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        if (number instanceof BigInteger integer) {
            return integer;
        }
        if (number instanceof BigDecimal decimal) {
            return decimal.toBigIntegerExact();
        }
        return new BigDecimal(number.toString()).toBigIntegerExact();
    }
}
