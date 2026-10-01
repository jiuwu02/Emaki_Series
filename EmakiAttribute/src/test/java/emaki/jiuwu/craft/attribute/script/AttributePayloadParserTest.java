package emaki.jiuwu.craft.attribute.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeTargetType;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.attribute.model.TemporaryStackMode;

@DisplayName("脚本属性 payload 解析映射")
class AttributePayloadParserTest {

    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    private Value payload(String literal) {
        return context.eval("js", "(" + literal + ")");
    }

    @Test
    @DisplayName("全字段 payload 映射为 AttributeDefinition")
    void fullFieldPayloadMapsToDefinition() {
        AttributePayloadParser.ParseResult result = AttributePayloadParser.parse(payload(
                "{id: 'Script_Example', display_name: '示例', value_kind: 'PERCENT', target_type: 'RESOURCE',"
                        + " target_id: 'Mana', mmoitems_stat: 'MANA_REGEN', default_value: 2.5, min_value: 0.5,"
                        + " max_value: 99.5, allow_negative: false, priority: 42, lore_format_id: 'Default_Percent',"
                        + " lore_patterns: ['a.*', 'b.*'], description: ' 说明 ', attribute_power: 1.5,"
                        + " tags: ['Alpha', 'alpha', ' '], temporary_stack_mode: 'STACK'}"));
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        AttributeDefinition definition = result.definition();
        assertEquals("script_example", definition.id());
        assertEquals("示例", definition.displayName());
        assertEquals(AttributeValueKind.PERCENT, definition.valueKind());
        assertEquals(AttributeTargetType.RESOURCE, definition.targetType());
        assertEquals("mana", definition.targetId());
        assertEquals("MANA_REGEN", definition.mmoItemsStatId());
        assertEquals(2.5D, definition.defaultValue());
        assertEquals(0.5D, definition.minValue());
        assertEquals(99.5D, definition.maxValue());
        assertFalse(definition.allowNegative());
        assertEquals(42, definition.priority());
        assertEquals("default_percent", definition.loreFormatId());
        assertEquals(List.of("a.*", "b.*"), definition.lorePatterns());
        assertEquals("说明", definition.description());
        assertEquals(1.5D, definition.attributePower());
        assertEquals(List.of("alpha"), definition.tags());
        assertEquals(TemporaryStackMode.STACK, definition.temporaryStackMode());
        assertFalse(definition.parentAttribute());
        assertTrue(definition.childBonuses().isEmpty());
    }

    @Test
    @DisplayName("缺省字段回退到 YAML schema 默认值")
    void defaultsAppliedWhenFieldsAbsent() {
        AttributePayloadParser.ParseResult result = AttributePayloadParser.parse(payload("{id: 'Demo Attr'}"));
        assertTrue(result.valid());
        AttributeDefinition definition = result.definition();
        assertEquals("demo_attr", definition.id());
        assertEquals(AttributeValueKind.FLAT, definition.valueKind());
        assertEquals(AttributeTargetType.GENERIC, definition.targetType());
        assertEquals(TemporaryStackMode.REPLACE, definition.temporaryStackMode());
        assertEquals(0D, definition.defaultValue());
        assertNull(definition.minValue());
        assertNull(definition.maxValue());
        assertTrue(definition.allowNegative());
        assertEquals(0, definition.priority());
        assertEquals(1D, definition.attributePower());
        assertTrue(definition.lorePatterns().isEmpty());
        assertTrue(definition.tags().isEmpty());
    }

    @Test
    @DisplayName("非法枚举逐条拒绝")
    void invalidEnumRejected() {
        AttributePayloadParser.ParseResult result = AttributePayloadParser.parse(
                payload("{id: 'demo', value_kind: 'NOT_A_KIND'}"));
        assertFalse(result.valid());
        assertNull(result.definition());
        assertEquals(1, result.errors().size());
        AttributePayloadParser.PayloadError error = result.errors().get(0);
        assertEquals(AttributePayloadParser.ErrorKind.INVALID_ENUM, error.kind());
        assertEquals("value_kind", error.field());
        assertEquals("NOT_A_KIND", error.value());
    }

    @Test
    @DisplayName("非数值字段逐条拒绝")
    void invalidNumberRejected() {
        AttributePayloadParser.ParseResult result = AttributePayloadParser.parse(
                payload("{id: 'demo', default_value: 'abc'}"));
        assertFalse(result.valid());
        assertNull(result.definition());
        assertEquals(1, result.errors().size());
        AttributePayloadParser.PayloadError error = result.errors().get(0);
        assertEquals(AttributePayloadParser.ErrorKind.INVALID_NUMBER, error.kind());
        assertEquals("default_value", error.field());
        assertEquals("abc", error.value());
    }

    @Test
    @DisplayName("id 空白或缺失拒绝")
    void blankOrMissingIdRejected() {
        AttributePayloadParser.ParseResult blank = AttributePayloadParser.parse(payload("{id: '   '}"));
        assertFalse(blank.valid());
        assertEquals(1, blank.errors().size());
        assertEquals(AttributePayloadParser.ErrorKind.MISSING_ID, blank.errors().get(0).kind());

        AttributePayloadParser.ParseResult missing = AttributePayloadParser.parse(payload("{}"));
        assertFalse(missing.valid());
        assertEquals(1, missing.errors().size());
        assertEquals(AttributePayloadParser.ErrorKind.MISSING_ID, missing.errors().get(0).kind());
    }

    @Test
    @DisplayName("tags 与 lore_patterns 列表转换且非法列表拒绝")
    void listFieldsConvertAndReject() {
        AttributePayloadParser.ParseResult result = AttributePayloadParser.parse(payload(
                "{id: 'demo', lore_patterns: ['a.*', '', 'b.*'], tags: ['x', 'y']}"));
        assertTrue(result.valid());
        assertEquals(List.of("a.*", "b.*"), result.definition().lorePatterns());
        assertEquals(List.of("x", "y"), result.definition().tags());

        AttributePayloadParser.ParseResult invalid = AttributePayloadParser.parse(
                payload("{id: 'demo', tags: 'not-an-array'}"));
        assertFalse(invalid.valid());
        assertEquals(1, invalid.errors().size());
        assertEquals(AttributePayloadParser.ErrorKind.INVALID_LIST, invalid.errors().get(0).kind());
        assertEquals("tags", invalid.errors().get(0).field());
    }

    @Test
    @DisplayName("多字段非法时逐条收集错误")
    void multipleErrorsCollected() {
        AttributePayloadParser.ParseResult result = AttributePayloadParser.parse(payload(
                "{id: 'demo', value_kind: 'WRONG', priority: 'abc', target_type: 'NOPE'}"));
        assertFalse(result.valid());
        assertEquals(3, result.errors().size());
        assertEquals(AttributePayloadParser.ErrorKind.INVALID_ENUM, result.errors().get(0).kind());
        assertEquals("value_kind", result.errors().get(0).field());
        assertEquals(AttributePayloadParser.ErrorKind.INVALID_ENUM, result.errors().get(1).kind());
        assertEquals("target_type", result.errors().get(1).field());
        assertEquals(AttributePayloadParser.ErrorKind.INVALID_NUMBER, result.errors().get(2).kind());
        assertEquals("priority", result.errors().get(2).field());
    }

    @Test
    @DisplayName("数值字符串字段按 YAML 语义接受")
    void numericStringsAccepted() {
        AttributePayloadParser.ParseResult result = AttributePayloadParser.parse(payload(
                "{id: 'demo', priority: '15', default_value: '3.5'}"));
        assertTrue(result.valid());
        assertEquals(15, result.definition().priority());
        assertEquals(3.5D, result.definition().defaultValue());
    }

    @Test
    @DisplayName("宿主无文件上下文时校验错误退化为不带文件名")
    void payloadErrorDegradesWithoutFileContext() throws Exception {
        Source source = Source.newBuilder("js", "({id: 'demo', value_kind: 'WRONG'})", "attributes/example_attribute.js")
                .buildLiteral();
        AttributePayloadParser.ParseResult result = AttributePayloadParser.parse(context.eval(source));
        assertFalse(result.valid());
        assertEquals(1, result.errors().size());
        assertEquals("unknown", result.errors().get(0).file());
    }
}
