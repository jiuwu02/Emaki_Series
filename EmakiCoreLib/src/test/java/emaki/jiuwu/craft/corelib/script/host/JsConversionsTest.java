package emaki.jiuwu.craft.corelib.script.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsConversions 深转换")
class JsConversionsTest {

    @Test
    @DisplayName("Map 与 List 递归转换为可属性/索引访问的 JS 对象")
    void mapAndListConvertRecursively() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("name", "sword");
            input.put("nested", Map.of("id", 7));
            input.put("tags", List.of("a", "b"));

            Value value = context.asValue(JsConversions.deepToJs(input));

            assertTrue(value.hasMembers());
            assertEquals("sword", value.getMember("name").asString());
            assertTrue(value.getMember("nested").hasMembers());
            assertEquals(7, value.getMember("nested").getMember("id").asInt());
            assertTrue(value.getMember("tags").hasArrayElements());
            assertEquals(2, value.getMember("tags").getArraySize());
            assertEquals("b", value.getMember("tags").getArrayElement(1).asString());
        }
    }

    @Test
    @DisplayName("列表内嵌套 Map 与映射内嵌套列表均被递归转换")
    void nestedStructuresConvertDeeply() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("k", "v");
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("rows", List.of(entry));
            input.put("matrix", List.of(List.of(1, 2)));

            Value value = context.asValue(JsConversions.deepToJs(input));

            assertEquals("v", value.getMember("rows").getArrayElement(0).getMember("k").asString());
            Value matrix = value.getMember("matrix");
            assertTrue(matrix.hasArrayElements());
            assertEquals(2, matrix.getArrayElement(0).getArraySize());
            assertEquals(2, matrix.getArrayElement(0).getArrayElement(1).asInt());
        }
    }

    @Test
    @DisplayName("标量与 null 原样通过，宿主对象原样返回")
    void scalarsAndHostObjectsPassThrough() {
        assertEquals("text", JsConversions.deepToJs("text"));
        assertEquals(42, JsConversions.deepToJs(42));
        assertEquals(Boolean.TRUE, JsConversions.deepToJs(Boolean.TRUE));
        assertNull(JsConversions.deepToJs(null));
        Object hostObject = new Object();
        assertSame(hostObject, JsConversions.deepToJs(hostObject));
    }
}
