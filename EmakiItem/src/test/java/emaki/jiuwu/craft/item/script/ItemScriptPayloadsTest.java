package emaki.jiuwu.craft.item.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("脚本效果类型 payload 契约校验")
class ItemScriptPayloadsTest {

    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    private Value eval(String source) {
        return context.eval("js", source);
    }

    @Test
    @DisplayName("合法 payload 通过且 id 按 typeId 规则规范化")
    void acceptsValidPayloadAndNormalizesId() {
        ItemScriptPayload payload = ItemScriptPayloads.from(
                eval("({ id: '  My Effect ', parse: function () { return null; }, apply: function () {} })"));
        assertEquals("my_effect", payload.id());
        assertNotNull(payload.parseFn());
        assertTrue(payload.parseFn().canExecute());
        assertNotNull(payload.applyFn());
        assertTrue(payload.applyFn().canExecute());
        assertNull(payload.clearFn());
    }

    @Test
    @DisplayName("clear 可选：提供函数时被保留")
    void keepsOptionalClearFunction() {
        ItemScriptPayload payload = ItemScriptPayloads.from(
                eval("({ id: 'x', parse: function () {}, apply: function () {}, clear: function () {} })"));
        assertNotNull(payload.clearFn());
        assertTrue(payload.clearFn().canExecute());
    }

    @Test
    @DisplayName("空白 id 被拒绝")
    void rejectsBlankId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ItemScriptPayloads.from(eval("({ id: '   ', parse: function () {}, apply: function () {} })")));
        assertTrue(exception.getMessage().contains("blank"));
    }

    @Test
    @DisplayName("缺失 id 被拒绝")
    void rejectsMissingId() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemScriptPayloads.from(eval("({ parse: function () {}, apply: function () {} })")));
    }

    @Test
    @DisplayName("非字符串 id 被拒绝")
    void rejectsNonStringId() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemScriptPayloads.from(eval("({ id: 42, parse: function () {}, apply: function () {} })")));
    }

    @Test
    @DisplayName("内置效果类型 id 被逐个拒绝")
    void rejectsBuiltInIds() {
        for (String builtIn : new String[]{"variables", "ea_attribute", "es_skill",
                "accessory_slot", "name_action", "lore_action"}) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> ItemScriptPayloads.from(eval("({ id: '" + builtIn
                            + "', parse: function () {}, apply: function () {} })")),
                    "expected rejection for built-in id " + builtIn);
            assertTrue(exception.getMessage().contains(builtIn));
        }
    }

    @Test
    @DisplayName("isReservedId 与内置表一致")
    void reservedIdCheckMatchesBuiltInSet() {
        assertTrue(ItemScriptPayloads.isReservedId("ea_attribute"));
        assertFalse(ItemScriptPayloads.isReservedId("script_example_glow"));
    }

    @Test
    @DisplayName("缺失 apply 被拒绝")
    void rejectsMissingApply() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ItemScriptPayloads.from(eval("({ id: 'x', parse: function () {} })")));
        assertTrue(exception.getMessage().contains("apply"));
    }

    @Test
    @DisplayName("缺失 parse 被拒绝")
    void rejectsMissingParse() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ItemScriptPayloads.from(eval("({ id: 'x', apply: function () {} })")));
        assertTrue(exception.getMessage().contains("parse"));
    }

    @Test
    @DisplayName("parse/apply 为非函数时被拒绝")
    void rejectsNonFunctionMembers() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemScriptPayloads.from(eval("({ id: 'x', parse: 'no', apply: function () {} })")));
        assertThrows(IllegalArgumentException.class,
                () -> ItemScriptPayloads.from(eval("({ id: 'x', parse: function () {}, apply: 123 })")));
    }

    @Test
    @DisplayName("clear 提供但非函数时被拒绝")
    void rejectsNonFunctionClear() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ItemScriptPayloads.from(
                        eval("({ id: 'x', parse: function () {}, apply: function () {}, clear: 'no' })")));
        assertTrue(exception.getMessage().contains("clear"));
    }

    @Test
    @DisplayName("payload 非对象被拒绝")
    void rejectsNonObjectPayload() {
        assertThrows(IllegalArgumentException.class, () -> ItemScriptPayloads.from(eval("42")));
        assertThrows(IllegalArgumentException.class, () -> ItemScriptPayloads.from(eval("null")));
        assertThrows(IllegalArgumentException.class, () -> ItemScriptPayloads.from(null));
    }

    @Test
    @DisplayName("isAbsent 判定 null/JS null/undefined，不误伤合法返回值")
    void absentDetection() {
        assertTrue(ItemScriptPayloads.isAbsent(null));
        assertTrue(ItemScriptPayloads.isAbsent(eval("null")));
        assertTrue(ItemScriptPayloads.isAbsent(eval("undefined")));
        assertFalse(ItemScriptPayloads.isAbsent(eval("0")));
        assertFalse(ItemScriptPayloads.isAbsent(eval("false")));
        assertFalse(ItemScriptPayloads.isAbsent(eval("({})")));
    }

    @Test
    @DisplayName("tryExtractId 尽力提取原始 id 用于错误报告")
    void tryExtractIdReportsRawValue() {
        assertEquals("  My Effect ", ItemScriptPayloads.tryExtractId(
                eval("({ id: '  My Effect ', parse: function () {}, apply: function () {} })")));
        assertNull(ItemScriptPayloads.tryExtractId(eval("({ parse: function () {} })")));
        assertNull(ItemScriptPayloads.tryExtractId(eval("42")));
        assertNull(ItemScriptPayloads.tryExtractId(null));
    }
}
