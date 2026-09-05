package emaki.jiuwu.craft.corelib.matcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.item.ComponentPath;
import emaki.jiuwu.craft.corelib.item.ItemComponentSnapshot;

public sealed interface Matcher permits
        Matcher.PdcMatcher,
        Matcher.LoreMatcher,
        Matcher.ComponentMatcher,
        Matcher.VariableExprMatcher,
        Matcher.CompareTargetMatcher,
        Matcher.AllMatcher,
        Matcher.AnyMatcher,
        Matcher.NoneMatcher,
        Matcher.CountMatcher {

    boolean test(@NotNull MatchContext context);

    static @NotNull Matcher fromConfig(@Nullable Object config) {
        if (config == null) {
            return new AllMatcher(List.of());
        }
        YamlSection section = asSection(config);
        if (section == null) {
            ComponentMatcherSupport.LOGGER.warning("Matcher config is not a mapping and will never match: "
                    + config.getClass().getSimpleName() + " -> " + config);
            return new AnyMatcher(List.of());
        }
        if (containsItemSourceField(section)) {
            return reject("matcher must not contain item source fields; move them to the sibling '"
                    + ItemRequirement.KEY_ITEM_SOURCES + "' field, which is evaluated as AND with the matcher");
        }
        String type = Texts.lower(section.getString("type", ""));
        if (type.isEmpty()) {
            return reject("matcher is missing the required 'type' key; item source conditions now belong to the sibling '"
                    + ItemRequirement.KEY_ITEM_SOURCES + "' field");
        }
        return switch (type) {
            case "item_source", "item_sources", "source", "sources" -> reject(
                    "matcher no longer accepts item source conditions ('type: " + type + "'); move them to the sibling '"
                            + ItemRequirement.KEY_ITEM_SOURCES + "' field, which is evaluated as AND with the matcher");
            case "pdc_match", "pdc" -> parsePdcMatch(section);
            case "lore_match", "lore" -> parseLoreMatch(section);
            case "component", "component_match" -> parseComponentMatch(section);
            case "variable_expr", "expr", "expression" -> parseVariableExpr(section);
            case "compare_target", "target" -> parseCompareTarget(section);
            case "all_of", "all", "and" -> parseAll(section);
            case "any_of", "any", "or" -> parseAny(section);
            case "none_of", "none", "not" -> parseNone(section);
            case "at_least" -> parseCount(section, CountMode.AT_LEAST);
            case "exactly" -> parseCount(section, CountMode.EXACTLY);
            default -> reject("unknown matcher type '" + type + "'");
        };
    }

    private static boolean containsItemSourceField(@NotNull YamlSection section) {
        return section.contains("item_sources")
                || section.contains("item_source")
                || section.contains("sources")
                || section.contains("source");
    }

    private static @NotNull Matcher reject(@NotNull String reason) {
        ComponentMatcherSupport.LOGGER.warning("Matcher rejected at load time, it will never match: " + reason + ".");
        return new AnyMatcher(List.of());
    }

    private static @Nullable YamlSection asSection(@NotNull Object config) {
        if (config instanceof YamlSection section) {
            return section;
        }
        if (config instanceof Map<?, ?> map) {
            Map<String, Object> keyed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    keyed.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return new MapYamlSection(keyed);
        }
        return null;
    }

    private static @NotNull Matcher parsePdcMatch(@NotNull YamlSection section) {
        String keyStr = section.getString("key", "");
        if (Texts.isBlank(keyStr)) {
            return new AllMatcher(List.of());
        }
        NamespacedKey key = parseNamespacedKey(keyStr);
        if (key == null) {
            return new AllMatcher(List.of());
        }
        String operator = section.getString("operator", "==");
        String valueStr = section.getString("value", "");
        String dataType = Texts.lower(section.getString("data_type", "string"));
        PdcMatchType matchType = switch (dataType) {
            case "int", "integer" -> PdcMatchType.INTEGER;
            case "long" -> PdcMatchType.LONG;
            case "double", "number" -> PdcMatchType.DOUBLE;
            default -> PdcMatchType.STRING;
        };
        return new PdcMatcher(key, matchType, operator, valueStr);
    }

    private static @NotNull Matcher parseLoreMatch(@NotNull YamlSection section) {
        String pattern = section.getString("pattern", "");
        String modeStr = Texts.lower(section.getString("mode", "contains"));
        LoreMatchMode mode = switch (modeStr) {
            case "exact", "equals" -> LoreMatchMode.EXACT;
            case "regex", "pattern" -> LoreMatchMode.REGEX;
            default -> LoreMatchMode.CONTAINS;
        };
        return new LoreMatcher(pattern, mode);
    }

    private static @NotNull Matcher parseComponentMatch(@NotNull YamlSection section) {
        String componentId = ItemComponentSnapshot.normalizeComponentId(section.getString("component", ""));
        if (componentId.isEmpty()) {
            return rejectComponentMatcher("missing 'component' key");
        }
        Object expected = section.get("value");
        String operator = Texts.lower(section.getString("operator", expected == null ? "exists" : "=="));
        ComponentOperator resolved = ComponentOperator.fromConfig(operator);
        if (resolved == null) {
            return rejectComponentMatcher("unknown operator '" + operator + "' for component " + componentId);
        }
        if (resolved.requiresValue() && expected == null) {
            return rejectComponentMatcher("operator '" + operator + "' requires a 'value' for component " + componentId);
        }
        if (ComponentMatcherSupport.isNonValued(componentId)
                && resolved != ComponentOperator.EXISTS
                && resolved != ComponentOperator.ABSENT) {
            return rejectComponentMatcher(componentId
                    + " is a unit component and only supports exists/absent, got '" + operator + "'");
        }
        return new ComponentMatcher(componentId, ComponentPath.parse(section.getString("path", "")), resolved, expected);
    }

    private static @NotNull Matcher rejectComponentMatcher(@NotNull String reason) {
        ComponentMatcherSupport.LOGGER.warning("Component matcher rejected at load time, it will never match: " + reason + ".");
        return new AnyMatcher(List.of());
    }

    private static @NotNull Matcher parseVariableExpr(@NotNull YamlSection section) {
        String expression = section.getString("expression", "true");
        return new VariableExprMatcher(expression);
    }

    private static @NotNull Matcher parseCompareTarget(@NotNull YamlSection section) {
        String attribute = section.getString("attribute", "");
        String operator = section.getString("operator", "==");
        return new CompareTargetMatcher(attribute, operator);
    }

    private static @NotNull Matcher parseAll(@NotNull YamlSection section) {
        return new AllMatcher(parseChildren(section));
    }

    private static @NotNull Matcher parseAny(@NotNull YamlSection section) {
        return new AnyMatcher(parseChildren(section));
    }

    private static @NotNull Matcher parseNone(@NotNull YamlSection section) {
        return new NoneMatcher(parseChildren(section));
    }

    private static @NotNull Matcher parseCount(@NotNull YamlSection section, @NotNull CountMode mode) {
        List<Matcher> matchers = parseChildren(section);
        Integer configured = section.getInt("required_count", 1);
        int required = configured == null ? 1 : configured;
        return new CountMatcher(matchers, mode, Math.max(0, required));
    }

    private static @NotNull List<Matcher> parseChildren(@NotNull YamlSection section) {
        List<Matcher> matchers = new ArrayList<>();
        List<?> matcherList = section.getList("matchers");
        if (matcherList != null) {
            for (Object item : matcherList) {
                matchers.add(fromConfig(item));
            }
        }
        return matchers;
    }

    private static @Nullable NamespacedKey parseNamespacedKey(@NotNull String keyStr) {
        try {
            if (keyStr.indexOf(':') >= 0) {
                return NamespacedKey.fromString(keyStr);
            } else {
                return NamespacedKey.minecraft(keyStr);
            }
        } catch (Exception _) {
            return null;
        }
    }

    enum PdcMatchType {
        STRING(PersistentDataType.STRING),
        INTEGER(PersistentDataType.INTEGER),
        LONG(PersistentDataType.LONG),
        DOUBLE(PersistentDataType.DOUBLE);

        final PersistentDataType<?, ?> type;

        PdcMatchType(PersistentDataType<?, ?> type) {
            this.type = type;
        }
    }

    enum LoreMatchMode {
        CONTAINS,
        EXACT,
        REGEX
    }

    enum CountMode {
        AT_LEAST,
        EXACTLY
    }

    enum ComponentOperator {
        EXISTS(false),
        ABSENT(false),
        EQUALS(true),
        NOT_EQUALS(true),
        GREATER(true),
        GREATER_OR_EQUAL(true),
        LESS(true),
        LESS_OR_EQUAL(true),
        CONTAINS(true),
        STARTS_WITH(true),
        ENDS_WITH(true),
        REGEX(true),
        HAS_KEY(true),
        HAS_VALUE(true),
        SIZE(true);

        private final boolean requiresValue;

        ComponentOperator(boolean requiresValue) {
            this.requiresValue = requiresValue;
        }

        boolean requiresValue() {
            return requiresValue;
        }

        static @Nullable ComponentOperator fromConfig(@NotNull String operator) {
            return switch (operator) {
                case "exists", "present" -> EXISTS;
                case "absent", "missing" -> ABSENT;
                case "==", "equals", "=" -> EQUALS;
                case "!=", "not_equals" -> NOT_EQUALS;
                case ">", "greater_than" -> GREATER;
                case ">=", "greater_or_equal" -> GREATER_OR_EQUAL;
                case "<", "less_than" -> LESS;
                case "<=", "less_or_equal" -> LESS_OR_EQUAL;
                case "contains" -> CONTAINS;
                case "starts_with" -> STARTS_WITH;
                case "ends_with" -> ENDS_WITH;
                case "regex", "pattern" -> REGEX;
                case "has_key" -> HAS_KEY;
                case "has_value" -> HAS_VALUE;
                case "size" -> SIZE;
                default -> null;
            };
        }
    }

    record PdcMatcher(@NotNull NamespacedKey key, @NotNull PdcMatchType matchType, @NotNull String operator, @NotNull String valueStr) implements Matcher {
        @Override
        public boolean test(@NotNull MatchContext context) {
            if (context.item() == null || !context.item().hasItemMeta()) {
                return false;
            }
            ItemMeta meta = context.item().getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (!pdc.has(key)) {
                return false;
            }
            try {
                return switch (matchType) {
                    case STRING -> compareString(pdc.get(key, PersistentDataType.STRING), valueStr, operator);
                    case INTEGER -> compareNumber(pdc.get(key, PersistentDataType.INTEGER), parseDouble(valueStr), operator);
                    case LONG -> compareNumber(pdc.get(key, PersistentDataType.LONG), parseDouble(valueStr), operator);
                    case DOUBLE -> compareNumber(pdc.get(key, PersistentDataType.DOUBLE), parseDouble(valueStr), operator);
                };
            } catch (Exception _) {
                return false;
            }
        }

        private static boolean compareString(@Nullable String actual, @NotNull String expected, @NotNull String operator) {
            if (actual == null) {
                return false;
            }
            return switch (operator) {
                case "==", "equals" -> actual.equals(expected);
                case "!=", "not_equals" -> !actual.equals(expected);
                case "contains" -> actual.contains(expected);
                case "starts_with" -> actual.startsWith(expected);
                case "ends_with" -> actual.endsWith(expected);
                default -> false;
            };
        }

        private static boolean compareNumber(@Nullable Number actual, double expected, @NotNull String operator) {
            if (actual == null) {
                return false;
            }
            double actualValue = actual.doubleValue();
            return switch (operator) {
                case "==", "equals" -> Math.abs(actualValue - expected) < 1e-9;
                case "!=", "not_equals" -> Math.abs(actualValue - expected) >= 1e-9;
                case ">", "greater_than" -> actualValue > expected;
                case ">=", "greater_or_equal" -> actualValue >= expected;
                case "<", "less_than" -> actualValue < expected;
                case "<=", "less_or_equal" -> actualValue <= expected;
                default -> false;
            };
        }

        private static double parseDouble(@NotNull String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException _) {
                return 0.0;
            }
        }
    }

    record LoreMatcher(@NotNull String pattern, @NotNull LoreMatchMode mode) implements Matcher {
        @Override
        public boolean test(@NotNull MatchContext context) {
            if (context.item() == null || !context.item().hasItemMeta()) {
                return false;
            }
            ItemMeta meta = context.item().getItemMeta();
            if (!meta.hasLore()) {
                return false;
            }
            List<String> lore = meta.getLore();
            if (lore == null || lore.isEmpty()) {
                return false;
            }
            return switch (mode) {
                case CONTAINS -> lore.stream().anyMatch(line -> line.contains(pattern));
                case EXACT -> lore.stream().anyMatch(line -> line.equals(pattern));
                case REGEX -> {
                    try {
                        Pattern regex = Pattern.compile(pattern);
                        yield lore.stream().anyMatch(line -> regex.matcher(line).find());
                    } catch (Exception _) {
                        yield false;
                    }
                }
            };
        }
    }

    record ComponentMatcher(@NotNull String componentId,
            @NotNull ComponentPath path,
            @NotNull ComponentOperator operator,
            @Nullable Object expectedValue) implements Matcher {

        @Override
        public boolean test(@NotNull MatchContext context) {
            if (context.item() == null || context.item().getType().isAir()) {
                return false;
            }
            ItemComponentSnapshot snapshot = ItemComponentSnapshot.of(context.item());
            if (operator == ComponentOperator.EXISTS) {
                return snapshot.present(componentId);
            }
            if (operator == ComponentOperator.ABSENT) {
                return !snapshot.present(componentId);
            }
            List<Object> candidates = snapshot.resolve(componentId, path);
            if (candidates.isEmpty()) {
                return false;
            }
            for (Object candidate : candidates) {
                if (ComponentMatcherSupport.compare(candidate, expectedValue, operator)) {
                    return true;
                }
            }
            return false;
        }
    }

    record VariableExprMatcher(@NotNull String expression) implements Matcher {
        @Override
        public boolean test(@NotNull MatchContext context) {
            try {
                Map<String, Object> variables = new LinkedHashMap<>(context.variableContext().toMap());
                if (context.item() != null && context.item().hasItemMeta()) {
                    ItemMeta meta = context.item().getItemMeta();
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    for (NamespacedKey key : pdc.getKeys()) {
                        String varKey = "item_pdc_" + key.getNamespace() + "_" + key.getKey();
                        if (pdc.has(key, PersistentDataType.DOUBLE)) {
                            variables.put(varKey, pdc.get(key, PersistentDataType.DOUBLE));
                        } else if (pdc.has(key, PersistentDataType.INTEGER)) {
                            variables.put(varKey, pdc.get(key, PersistentDataType.INTEGER));
                        } else if (pdc.has(key, PersistentDataType.LONG)) {
                            variables.put(varKey, pdc.get(key, PersistentDataType.LONG));
                        } else if (pdc.has(key, PersistentDataType.STRING)) {
                            variables.put(varKey, pdc.get(key, PersistentDataType.STRING));
                        }
                    }
                }
                Boolean result = ExpressionEngine.evaluateBoolean(expression, variables);
                return result != null && result;
            } catch (Exception _) {
                return false;
            }
        }
    }

    record CompareTargetMatcher(@NotNull String attribute, @NotNull String operator) implements Matcher {
        @Override
        public boolean test(@NotNull MatchContext context) {
            if (context.item() == null || context.targetItem() == null) {
                return false;
            }
            if (!context.item().hasItemMeta() || !context.targetItem().hasItemMeta()) {
                return false;
            }
            try {
                NamespacedKey key = parseNamespacedKey(attribute);
                if (key == null) {
                    return false;
                }
                ItemMeta itemMeta = context.item().getItemMeta();
                ItemMeta targetMeta = context.targetItem().getItemMeta();
                PersistentDataContainer itemPdc = itemMeta.getPersistentDataContainer();
                PersistentDataContainer targetPdc = targetMeta.getPersistentDataContainer();
                if (!itemPdc.has(key) || !targetPdc.has(key)) {
                    return false;
                }
                Double itemValue = itemPdc.get(key, PersistentDataType.DOUBLE);
                Double targetValue = targetPdc.get(key, PersistentDataType.DOUBLE);
                if (itemValue == null || targetValue == null) {
                    return false;
                }
                return switch (operator) {
                    case ">", "greater_than" -> itemValue > targetValue;
                    case ">=", "greater_or_equal" -> itemValue >= targetValue;
                    case "<", "less_than" -> itemValue < targetValue;
                    case "<=", "less_or_equal" -> itemValue <= targetValue;
                    case "==", "equals" -> Math.abs(itemValue - targetValue) < 1e-9;
                    case "!=", "not_equals" -> Math.abs(itemValue - targetValue) >= 1e-9;
                    default -> false;
                };
            } catch (Exception _) {
                return false;
            }
        }
    }

    record AllMatcher(@NotNull List<Matcher> matchers) implements Matcher {
        public AllMatcher {
            matchers = List.copyOf(matchers);
        }

        @Override
        public boolean test(@NotNull MatchContext context) {
            if (matchers.isEmpty()) {
                return true;
            }
            for (Matcher matcher : matchers) {
                if (!matcher.test(context)) {
                    return false;
                }
            }
            return true;
        }
    }

    record AnyMatcher(@NotNull List<Matcher> matchers) implements Matcher {
        public AnyMatcher {
            matchers = List.copyOf(matchers);
        }

        @Override
        public boolean test(@NotNull MatchContext context) {
            if (matchers.isEmpty()) {
                return false;
            }
            for (Matcher matcher : matchers) {
                if (matcher.test(context)) {
                    return true;
                }
            }
            return false;
        }
    }

    record NoneMatcher(@NotNull List<Matcher> matchers) implements Matcher {
        public NoneMatcher {
            matchers = List.copyOf(matchers);
        }

        @Override
        public boolean test(@NotNull MatchContext context) {
            for (Matcher matcher : matchers) {
                if (matcher.test(context)) {
                    return false;
                }
            }
            return true;
        }
    }

    record CountMatcher(@NotNull List<Matcher> matchers, @NotNull CountMode mode, int requiredCount) implements Matcher {
        public CountMatcher {
            matchers = List.copyOf(matchers);
            requiredCount = Math.max(0, requiredCount);
        }

        @Override
        public boolean test(@NotNull MatchContext context) {
            int satisfied = 0;
            for (Matcher matcher : matchers) {
                if (matcher.test(context)) {
                    satisfied++;
                }
            }
            return switch (mode) {
                case AT_LEAST -> satisfied >= requiredCount;
                case EXACTLY -> satisfied == requiredCount;
            };
        }
    }
}
