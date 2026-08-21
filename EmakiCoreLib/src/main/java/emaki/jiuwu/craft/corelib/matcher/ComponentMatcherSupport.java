package emaki.jiuwu.craft.corelib.matcher;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.item.ComponentValueParser;
import emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog;

final class ComponentMatcherSupport {

    static final Logger LOGGER = Logger.getLogger(Matcher.class.getName());

    private static final MinecraftItemComponentCatalog CATALOG = new MinecraftItemComponentCatalog();

    private static final double EPSILON = 1e-9;

    private ComponentMatcherSupport() {
    }

    static boolean isNonValued(String componentId) {
        MinecraftItemComponentCatalog.Entry entry = CATALOG.entry(componentId);
        return entry != null && entry.nonValued();
    }

    static boolean compare(Object actual, Object expected, Matcher.ComponentOperator operator) {
        return switch (operator) {
            case EXISTS, ABSENT -> false;
            case EQUALS -> equalsValue(actual, expected);
            case NOT_EQUALS -> !equalsValue(actual, expected);
            case GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL -> compareNumeric(actual, expected, operator);
            case CONTAINS -> textContains(actual, expected);
            case STARTS_WITH -> anyText(actual, text -> text.startsWith(plain(expected)));
            case ENDS_WITH -> anyText(actual, text -> text.endsWith(plain(expected)));
            case REGEX -> matchesRegex(actual, expected);
            case HAS_KEY -> hasKey(actual, expected);
            case HAS_VALUE -> hasValue(actual, expected);
            case SIZE -> sizeEquals(actual, expected);
        };
    }

    private static boolean equalsValue(Object actual, Object expected) {
        Double actualNumber = numeric(actual);
        Double expectedNumber = numeric(expected);
        if (actualNumber != null && expectedNumber != null) {
            return Math.abs(actualNumber - expectedNumber) < EPSILON;
        }
        if (actual instanceof Boolean || expected instanceof Boolean) {
            Boolean actualFlag = booleanOf(actual);
            Boolean expectedFlag = booleanOf(expected);
            return actualFlag != null && actualFlag.equals(expectedFlag);
        }
        return anyText(actual, text -> text.equals(plain(expected)));
    }

    private static boolean compareNumeric(Object actual, Object expected, Matcher.ComponentOperator operator) {
        Double actualNumber = numeric(actual);
        Double expectedNumber = numeric(expected);
        if (actualNumber == null || expectedNumber == null) {
            return false;
        }
        return switch (operator) {
            case GREATER -> actualNumber > expectedNumber;
            case GREATER_OR_EQUAL -> actualNumber >= expectedNumber;
            case LESS -> actualNumber < expectedNumber;
            case LESS_OR_EQUAL -> actualNumber <= expectedNumber;
            default -> false;
        };
    }

    private static boolean textContains(Object actual, Object expected) {
        return anyText(actual, text -> text.contains(plain(expected)));
    }

    private static boolean matchesRegex(Object actual, Object expected) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(plain(expected));
        } catch (RuntimeException exception) {
            LOGGER.warning("Component matcher regex is invalid and evaluates to false: "
                    + plain(expected) + ", cause=" + exception.getClass().getSimpleName());
            return false;
        }
        return anyText(actual, text -> pattern.matcher(text).find());
    }

    private static boolean hasKey(Object actual, Object expected) {
        if (!(actual instanceof Map<?, ?> map)) {
            return false;
        }
        String key = plain(expected);
        for (Object candidate : map.keySet()) {
            String text = Texts.toStringSafe(candidate);
            if (text.equals(key) || stripMinecraft(text).equals(stripMinecraft(key))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasValue(Object actual, Object expected) {
        Collection<?> values = valuesOf(actual);
        if (values == null) {
            return false;
        }
        for (Object candidate : values) {
            if (equalsValue(candidate, expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sizeEquals(Object actual, Object expected) {
        Double expectedNumber = numeric(expected);
        if (expectedNumber == null) {
            return false;
        }
        Integer size = sizeOf(actual);
        return size != null && Math.abs(size - expectedNumber) < EPSILON;
    }

    private static Integer sizeOf(Object actual) {
        if (actual instanceof Collection<?> collection) {
            return collection.size();
        }
        if (actual instanceof Map<?, ?> map) {
            return map.size();
        }
        if (actual instanceof String text) {
            return text.length();
        }
        return null;
    }

    private static Collection<?> valuesOf(Object actual) {
        if (actual instanceof Collection<?> collection) {
            return collection;
        }
        if (actual instanceof Map<?, ?> map) {
            return map.values();
        }
        return null;
    }

    private static boolean anyText(Object actual, TextPredicate predicate) {
        List<String> texts = flattenText(actual);
        for (String text : texts) {
            if (predicate.matches(text)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> flattenText(Object actual) {
        if (actual == null) {
            return List.of();
        }
        if (actual instanceof Collection<?> collection) {
            return collection.stream().map(ComponentMatcherSupport::normalizeText).toList();
        }
        return List.of(normalizeText(actual));
    }

    private static String normalizeText(Object value) {
        if (isTextComponentShape(value)) {
            return MiniMessages.plainText(renderTextComponent(value));
        }
        return Texts.toStringSafe(value);
    }

    private static boolean isTextComponentShape(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.containsKey("text") || map.containsKey("extra") || map.containsKey("translate");
        }
        return false;
    }

    private static String renderTextComponent(Object value) {
        StringBuilder builder = new StringBuilder();
        appendTextComponent(builder, value);
        return builder.toString();
    }

    private static void appendTextComponent(StringBuilder builder, Object value) {
        if (value instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text != null) {
                builder.append(Texts.toStringSafe(text));
            }
            Object translate = map.get("translate");
            if (text == null && translate != null) {
                builder.append(Texts.toStringSafe(translate));
            }
            Object extra = map.get("extra");
            if (extra instanceof Collection<?> children) {
                for (Object child : children) {
                    appendTextComponent(builder, child);
                }
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object child : collection) {
                appendTextComponent(builder, child);
            }
            return;
        }
        builder.append(Texts.toStringSafe(value));
    }

    private static String plain(Object expected) {
        return normalizeText(expected);
    }

    private static String stripMinecraft(String value) {
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
    }

    private static Double numeric(Object value) {
        if (value instanceof Number number) {
            double result = number.doubleValue();
            return Double.isFinite(result) ? result : null;
        }
        if (value instanceof Boolean) {
            return null;
        }
        String text = Texts.toStringSafe(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        Object parsed = ComponentValueParser.parseScalar(text);
        if (parsed instanceof Number number) {
            double result = number.doubleValue();
            return Double.isFinite(result) ? result : null;
        }
        return null;
    }

    private static Boolean booleanOf(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        String text = Texts.toStringSafe(value).trim();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private interface TextPredicate {
        boolean matches(String text);
    }
}
