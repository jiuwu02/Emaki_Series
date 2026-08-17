package emaki.jiuwu.craft.corelib.quantity;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.variable.VariableContext;

/**
 * 统一的数量/数值计算接口，支持固定值、公式计算、查找表三种类型。
 * <p>
 * 使用场景：费用、概率、容量、经验等所有配方中需要动态计算的数值。
 * <p>
 * 配置格式：
 * <pre>{@code
 * # 固定值
 * quantity: 100
 * # 或
 * quantity:
 *   type: fixed
 *   value: 100
 *
 * # 公式计算
 * quantity:
 *   type: formula
 *   expression: "player_level * 10 + 50"
 *
 * # 查找表
 * quantity:
 *   type: lookup_table
 *   key: player_level
 *   default: 0
 *   table:
 *     1: 10
 *     2: 20
 *     5: 50
 * }</pre>
 */
public sealed interface Quantity permits Quantity.Fixed, Quantity.Formula, Quantity.LookupTable {

    /**
     * 根据变量上下文解析为 double 值。
     *
     * @param context 变量上下文
     * @return 解析后的数值
     */
    double resolve(@NotNull VariableContext context);

    /**
     * 解析为 int 值。
     *
     * @param context 变量上下文
     * @return 解析后的整数值
     */
    default int resolveInt(@NotNull VariableContext context) {
        return (int) resolve(context);
    }

    /**
     * 解析为 long 值。
     *
     * @param context 变量上下文
     * @return 解析后的长整数值
     */
    default long resolveLong(@NotNull VariableContext context) {
        return (long) resolve(context);
    }

    /**
     * 创建固定值 Quantity。
     *
     * @param value 固定值
     * @return Quantity 实例
     */
    static @NotNull Quantity fixed(double value) {
        return new Fixed(value);
    }

    /**
     * 创建公式 Quantity。
     *
     * @param expression 表达式字符串
     * @return Quantity 实例
     */
    static @NotNull Quantity formula(@NotNull String expression) {
        return new Formula(expression);
    }

    /**
     * 创建查找表 Quantity。
     *
     * @param keyVariable   用于查找的变量名（如 "player_level"）
     * @param table         整数键 → 数值的映射表
     * @param defaultValue  键不存在时的默认值
     * @return Quantity 实例
     */
    static @NotNull Quantity lookupTable(@NotNull String keyVariable, @NotNull Map<Integer, Double> table, double defaultValue) {
        return new LookupTable(keyVariable, table, defaultValue);
    }

    /**
     * 从配置节加载 Quantity。
     * <p>
     * 支持三种格式：
     * <ol>
     *   <li>数值或数值字符串：解析为 Fixed</li>
     *   <li>包含 type 字段的配置节：根据 type 解析</li>
     *   <li>null 或空：返回 Fixed(0.0)</li>
     * </ol>
     *
     * @param config 配置对象
     * @return Quantity 实例
     */
    static @NotNull Quantity fromConfig(@Nullable Object config) {
        if (config == null) {
            return fixed(0.0);
        }
        if (config instanceof Number number) {
            return fixed(number.doubleValue());
        }
        String strValue = String.valueOf(config).trim();
        try {
            return fixed(Double.parseDouble(strValue));
        } catch (NumberFormatException _) {
        }
        if (!(config instanceof YamlSection section)) {
            return fixed(0.0);
        }
        String type = Texts.lower(section.getString("type", "fixed"));
        return switch (type) {
            case "formula" -> formula(section.getString("expression", "0"));
            case "lookup_table", "table" -> {
                String keyVariable = section.getString("key", "player_level");
                double defaultValue = section.getDouble("default", 0.0);
                Map<Integer, Double> table = parseTable(section.getSection("table"));
                yield lookupTable(keyVariable, table, defaultValue);
            }
            default -> fixed(section.getDouble("value", 0.0));
        };
    }

    private static @NotNull Map<Integer, Double> parseTable(@Nullable YamlSection section) {
        Map<Integer, Double> table = new LinkedHashMap<>();
        if (section == null || section.isEmpty()) {
            return table;
        }
        for (String key : section.getKeys(false)) {
            try {
                int intKey = Integer.parseInt(key);
                double value = section.getDouble(key, 0.0);
                table.put(intKey, value);
            } catch (NumberFormatException _) {
            }
        }
        return table;
    }

    /**
     * 固定值类型。
     */
    record Fixed(double value) implements Quantity {
        @Override
        public double resolve(@NotNull VariableContext context) {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    /**
     * 公式计算类型。使用 {@link ExpressionEngine} 计算表达式。
     */
    record Formula(@NotNull String expression) implements Quantity {
        public Formula {
            expression = Texts.toStringSafe(expression);
        }

        @Override
        public double resolve(@NotNull VariableContext context) {
            try {
                return ExpressionEngine.evaluate(expression, context.toMap());
            } catch (Exception _) {
                return 0.0;
            }
        }

        @Override
        public String toString() {
            return "formula(" + expression + ")";
        }
    }

    /**
     * 查找表类型。根据指定变量的整数值从映射表中查找结果。
     */
    record LookupTable(@NotNull String keyVariable, @NotNull Map<Integer, Double> table, double defaultValue) implements Quantity {
        public LookupTable {
            keyVariable = Texts.toStringSafe(keyVariable);
            table = Map.copyOf(table);
        }

        @Override
        public double resolve(@NotNull VariableContext context) {
            int key = context.getInt(keyVariable);
            return table.getOrDefault(key, defaultValue);
        }

        @Override
        public String toString() {
            return "lookup_table(key=" + keyVariable + ", size=" + table.size() + ", default=" + defaultValue + ")";
        }
    }
}
