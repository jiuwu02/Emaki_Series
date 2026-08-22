package emaki.jiuwu.craft.corelib.quantity;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.variable.VariableContext;

public sealed interface Quantity permits Quantity.Fixed, Quantity.Formula, Quantity.LookupTable {

    double resolve(@NotNull VariableContext context);

    default int resolveInt(@NotNull VariableContext context) {
        return (int) resolve(context);
    }

    default long resolveLong(@NotNull VariableContext context) {
        return (long) resolve(context);
    }

    static @NotNull Quantity fixed(double value) {
        return new Fixed(value);
    }

    static @NotNull Quantity formula(@NotNull String expression) {
        return new Formula(expression);
    }

    static @NotNull Quantity lookupTable(@NotNull String keyVariable, @NotNull Map<Integer, Double> table, double defaultValue) {
        return new LookupTable(keyVariable, table, defaultValue);
    }

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
