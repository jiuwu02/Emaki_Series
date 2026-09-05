package emaki.jiuwu.craft.corelib.matcher;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;

@DisplayName("matcher 不再表达物品源条件")
class MatcherItemSourceRejectionTest {

    private static Map<String, Object> node(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    @Test
    @DisplayName("item_source 类型被拒绝")
    void itemSourceTypeIsRejected() {
        assertInstanceOf(Matcher.AnyMatcher.class,
                Matcher.fromConfig(new MapYamlSection(node("type", "item_source", "sources", List.of("minecraft-iron_ingot")))));
    }

    @Test
    @DisplayName("物品源类型别名都被拒绝")
    void itemSourceAliasesAreRejected() {
        for (String type : List.of("item_sources", "source", "sources")) {
            assertInstanceOf(Matcher.AnyMatcher.class, Matcher.fromConfig(node("type", type)));
        }
    }

    @Test
    @DisplayName("缺少 type 不再默认物品源")
    void missingTypeIsRejectedInsteadOfDefaultingToItemSource() {
        assertInstanceOf(Matcher.AnyMatcher.class, Matcher.fromConfig(node("sources", List.of("minecraft-iron_ingot"))));
    }

    @Test
    @DisplayName("未知类型被拒绝")
    void unknownTypeIsRejected() {
        assertInstanceOf(Matcher.AnyMatcher.class, Matcher.fromConfig(node("type", "not_a_matcher")));
    }

    @Test
    @DisplayName("组合 matcher 内的物品源子条件被拒绝")
    void nestedItemSourceChildIsRejected() {
        Map<String, Object> child = node("type", "item_source", "sources", List.of("minecraft-iron_ingot"));
        Map<String, Object> parent = node("type", "all_of", "matchers", List.of(child));
        Matcher matcher = Matcher.fromConfig(parent);
        assertInstanceOf(Matcher.AllMatcher.class, matcher);
        assertTrue(((Matcher.AllMatcher) matcher).matchers().stream().allMatch(Matcher.AnyMatcher.class::isInstance));
    }

    @Test
    @DisplayName("null matcher 表示未声明额外条件")
    void nullConfigStaysUnconstrained() {
        assertInstanceOf(Matcher.AllMatcher.class, Matcher.fromConfig(null));
    }

    @Test
    @DisplayName("非映射输入被拒绝")
    void nonMappingInputIsRejected() {
        assertInstanceOf(Matcher.AnyMatcher.class, Matcher.fromConfig("minecraft-iron_ingot"));
        assertInstanceOf(Matcher.AnyMatcher.class, Matcher.fromConfig(List.of("minecraft-iron_ingot")));
    }

    @Test
    @DisplayName("源码不再声明物品源 matcher 实体")
    void sourceMatcherEntityIsRemoved() throws Exception {
        String source = Files.readString(Path.of("src/main/java/emaki/jiuwu/craft/corelib/matcher/Matcher.java"));
        assertTrue(!source.contains("ItemSourceMatcher"));
    }

    @Test
    @DisplayName("空组合器保持各自的失败方向")
    void emptyCombinatorDirections() {
        assertInstanceOf(Matcher.AllMatcher.class, Matcher.fromConfig(node("type", "all_of")));
        assertInstanceOf(Matcher.AnyMatcher.class, Matcher.fromConfig(node("type", "any_of")));
        assertInstanceOf(Matcher.NoneMatcher.class, Matcher.fromConfig(node("type", "none_of")));
    }
}
