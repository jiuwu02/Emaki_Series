package emaki.jiuwu.craft.corelib.action.select;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("选择器参数合并")
class SelectorArgumentsTest {

    @Test
    @DisplayName("行内参数覆盖配置参数，其余沿用配置")
    void inlineOverridesConfigured() {
        SelectorArguments.Result.Merged merged = merged(
                Map.of("radius", "16", "limit", "5"),
                Map.of("radius", "24"),
                List.of("radius", "limit"));

        assertEquals("24", merged.values().get("radius"));
        assertEquals("5", merged.values().get("limit"));
    }

    @Test
    @DisplayName("选择器 id 本身不进入源参数")
    void selectorNameIsSkipped() {
        SelectorArguments.Result.Merged merged = merged(
                Map.of(), Map.of(SelectorArguments.SELECTOR_NAME_KEY, "elite_zombies"), List.of());

        assertEquals(Map.of(), merged.values());
    }

    @Test
    @DisplayName("源段未声明的参数被拒绝而不是静默丢弃")
    void unknownArgumentIsReported() {
        SelectorArguments.Result result = SelectorArguments.merge(
                Map.of("radius", "16"), Map.of("typo", "3"), List.of("radius"));

        assertEquals("typo", assertInstanceOf(SelectorArguments.Result.UnknownArgument.class, result).key());
    }

    @Test
    @DisplayName("配置为空时只使用行内参数")
    void configuredMayBeEmpty() {
        SelectorArguments.Result.Merged merged = merged(
                null, Map.of("radius", "8"), List.of("radius"));

        assertEquals(Map.of("radius", "8"), merged.values());
    }

    private static SelectorArguments.Result.Merged merged(Map<String, String> configured,
            Map<String, String> inline,
            List<String> declared) {
        return assertInstanceOf(SelectorArguments.Result.Merged.class,
                SelectorArguments.merge(configured, inline, declared));
    }
}