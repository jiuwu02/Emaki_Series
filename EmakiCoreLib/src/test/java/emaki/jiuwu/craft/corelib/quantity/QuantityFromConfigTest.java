package emaki.jiuwu.craft.corelib.quantity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.variable.VariableContext;

@DisplayName("数量解析的输入形态约束")
class QuantityFromConfigTest {

    private static final VariableContext EMPTY_CONTEXT = VariableContext.builder(null).build();

    @Test
    @DisplayName("整数标量按字面值解析")
    void parsesIntegerScalar() {
        assertEquals(5D, Quantity.fromConfig(5).resolve(EMPTY_CONTEXT));
    }

    @Test
    @DisplayName("小数标量按字面值解析")
    void parsesDoubleScalar() {
        assertEquals(2.5D, Quantity.fromConfig(2.5D).resolve(EMPTY_CONTEXT));
    }

    @Test
    @DisplayName("数字字符串按字面值解析")
    void parsesNumericString() {
        assertEquals(7D, Quantity.fromConfig("7").resolve(EMPTY_CONTEXT));
        assertEquals(1.5D, Quantity.fromConfig(" 1.5 ").resolve(EMPTY_CONTEXT));
    }

    @Test
    @DisplayName("null 解析为 0")
    void nullBecomesZero() {
        assertEquals(0D, Quantity.fromConfig(null).resolve(EMPTY_CONTEXT));
    }

    @Test
    @DisplayName("YamlSection 的 fixed 形态读 value 键")
    void parsesFixedSection() {
        MapYamlSection section = new MapYamlSection(Map.of("type", "fixed", "value", 9));
        assertEquals(9D, Quantity.fromConfig(section).resolve(EMPTY_CONTEXT));
    }

    @Test
    @DisplayName("未写 type 的 YamlSection 默认按 fixed 读 value")
    void sectionWithoutTypeDefaultsToFixed() {
        MapYamlSection section = new MapYamlSection(Map.of("value", 4));
        assertEquals(4D, Quantity.fromConfig(section).resolve(EMPTY_CONTEXT));
    }

    @Test
    @DisplayName("fixed 形态缺 value 键时为 0")
    void fixedWithoutValueIsZero() {
        assertEquals(0D, Quantity.fromConfig(new MapYamlSection(Map.of("type", "fixed"))).resolve(EMPTY_CONTEXT));
    }

    @Test
    @DisplayName("formula 形态解析为公式量而非固定量")
    void parsesFormulaSection() {
        MapYamlSection section = new MapYamlSection(Map.of("type", "formula", "expression", "2 + 3"));
        assertInstanceOf(Quantity.Formula.class, Quantity.fromConfig(section));
    }

    @Test
    @DisplayName("lookup_table 形态解析为查表量")
    void parsesLookupTableSection() {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("1", 10);
        table.put("2", 20);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("type", "lookup_table");
        raw.put("key", "player_level");
        raw.put("default", 1);
        raw.put("table", table);

        assertInstanceOf(Quantity.LookupTable.class, Quantity.fromConfig(new MapYamlSection(raw)));
    }

    @Test
    @DisplayName("裸 Map 不被 Quantity 接受，只解析为 0 —— 调用方必须先归一化为 YamlSection")
    void bareMapIsNotAccepted() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("type", "fixed");
        raw.put("value", 99);

        assertEquals(0D, Quantity.fromConfig(raw).resolve(EMPTY_CONTEXT));
        assertEquals(99D, Quantity.fromConfig(new MapYamlSection(raw)).resolve(EMPTY_CONTEXT));
    }

    @Test
    @DisplayName("非数字字符串解析为 0 而非抛异常")
    void nonNumericStringBecomesZero() {
        assertEquals(0D, Quantity.fromConfig("not-a-number").resolve(EMPTY_CONTEXT));
    }

    @Test
    @DisplayName("resolveInt 与 resolveLong 向下截断")
    void integerConversionTruncates() {
        Quantity quantity = Quantity.fromConfig(3.9D);
        assertEquals(3, quantity.resolveInt(EMPTY_CONTEXT));
        assertEquals(3L, quantity.resolveLong(EMPTY_CONTEXT));
    }
}
