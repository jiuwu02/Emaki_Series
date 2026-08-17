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

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;

/**
 * 统一的物品匹配器接口，用于配方材料校验、条件判断等场景。
 * <p>
 * 支持六种匹配器类型：
 * <ul>
 *   <li>{@code item_source}: 按物品类型识别（原版/自定义物品源）</li>
 *   <li>{@code pdc_match}: 按 PDC 键值匹配，支持类型和比较符</li>
 *   <li>{@code lore_match}: 按 Lore 文本匹配（包含/完全/正则）</li>
 *   <li>{@code variable_expr}: 表达式或 PAPI 条件</li>
 *   <li>{@code compare_target}: 与目标装备比较</li>
 *   <li>{@code all}/{@code any}: 组合逻辑运算</li>
 * </ul>
 * <p>
 * 配置格式示例：
 * <pre>{@code
 * matcher:
 *   type: item_source
 *   sources:
 *     - "minecraft:diamond"
 *     - "mmoitems:SWORD:FLAME_BLADE"
 *
 * matcher:
 *   type: pdc_match
 *   key: "emakiforge:enchant_level"
 *   operator: ">="
 *   value: 5
 *   data_type: integer
 *
 * matcher:
 *   type: lore_match
 *   pattern: "传说级"
 *   mode: contains
 *
 * matcher:
 *   type: variable_expr
 *   expression: "player_level >= 10 && pdc_emakiforge_quality > 3"
 *
 * matcher:
 *   type: all
 *   matchers:
 *     - { type: item_source, sources: ["minecraft:diamond_sword"] }
 *     - { type: pdc_match, key: "emakiforge:enchant_level", operator: ">=", value: 5 }
 * }</pre>
 */
public sealed interface Matcher permits
        Matcher.ItemSourceMatcher,
        Matcher.PdcMatcher,
        Matcher.LoreMatcher,
        Matcher.VariableExprMatcher,
        Matcher.CompareTargetMatcher,
        Matcher.AllMatcher,
        Matcher.AnyMatcher {

    /**
     * 测试物品是否匹配。
     *
     * @param context 匹配上下文
     * @return true 表示匹配成功
     */
    boolean test(@NotNull MatchContext context);

    /**
     * 从配置节加载 Matcher。
     *
     * @param config 配置对象
     * @return Matcher 实例
     */
    static @NotNull Matcher fromConfig(@Nullable Object config) {
        if (config == null) {
            return new AllMatcher(List.of());
        }
        if (!(config instanceof YamlSection section)) {
            return new AllMatcher(List.of());
        }
        String type = Texts.lower(section.getString("type", "item_source"));
        return switch (type) {
            case "item_source" -> parseItemSource(section);
            case "pdc_match", "pdc" -> parsePdcMatch(section);
            case "lore_match", "lore" -> parseLoreMatch(section);
            case "variable_expr", "expr", "expression" -> parseVariableExpr(section);
            case "compare_target", "target" -> parseCompareTarget(section);
            case "all", "and" -> parseAll(section);
            case "any", "or" -> parseAny(section);
            default -> new AllMatcher(List.of());
        };
    }

    private static @NotNull Matcher parseItemSource(@NotNull YamlSection section) {
        List<String> sourceList = section.getStringList("sources");
        if (sourceList.isEmpty()) {
            sourceList = section.getStringList("source");
        }
        List<ItemSourceRef> sources = new ArrayList<>();
        for (String token : sourceList) {
            ItemSourceRef ref = ItemSourceUtil.parseShorthand(token);
            if (ref != null) {
                sources.add(ref);
            }
        }
        return new ItemSourceMatcher(sources);
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
        List<Matcher> matchers = new ArrayList<>();
        List<?> matcherList = section.getList("matchers");
        if (matcherList != null) {
            for (Object item : matcherList) {
                matchers.add(fromConfig(item));
            }
        }
        return new AllMatcher(matchers);
    }

    private static @NotNull Matcher parseAny(@NotNull YamlSection section) {
        List<Matcher> matchers = new ArrayList<>();
        List<?> matcherList = section.getList("matchers");
        if (matcherList != null) {
            for (Object item : matcherList) {
                matchers.add(fromConfig(item));
            }
        }
        return new AnyMatcher(matchers);
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

    record ItemSourceMatcher(@NotNull List<ItemSourceRef> sources) implements Matcher {
        public ItemSourceMatcher {
            sources = List.copyOf(sources);
        }

        @Override
        public boolean test(@NotNull MatchContext context) {
            if (context.item() == null || context.item().getType().isAir()) {
                return false;
            }
            if (sources.isEmpty()) {
                return true;
            }
            if (context.itemSource() == null) {
                return false;
            }
            for (ItemSourceRef source : sources) {
                if (ItemSourceUtil.matches(source, context.itemSource())) {
                    return true;
                }
            }
            return false;
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
}
