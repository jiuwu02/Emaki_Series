package emaki.jiuwu.craft.corelib.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.text.Texts;

@DisplayName("配置 lore 占位符供值与整行展开约束")
class LorePlaceholderExpansionTest {

    private static Map<String, Object> summaryReplacements(List<String> lines, int total, int opened) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("summary_lines", lines);
        replacements.put("total", total);
        replacements.put("opened", opened);
        return replacements;
    }

    @Test
    @DisplayName("整行占位符按集合元素数量展开为多行")
    void expandsSoleplaceholderIntoOneLinePerElement() {
        List<String> computed = List.of("总孔数: 4", "已开孔: 2", "已镶嵌: 1");
        List<String> expanded = Texts.expandTemplateLines("%summary_lines%",
                summaryReplacements(computed, 4, 2));
        assertEquals(3, expanded.size());
        assertEquals(computed, expanded);
    }

    @Test
    @DisplayName("同一占位符在空状态与有值状态展开出不同行数")
    void expandedLineCountFollowsBranchState() {
        List<String> emptyBranch = List.of("放入装备后可查看统计");
        List<String> filledBranch = List.of("总孔数: 4", "已开孔: 2", "已镶嵌: 1", "空余: 1", "未开孔: 2");
        assertEquals(1, Texts.expandTemplateLines("%summary_lines%",
                summaryReplacements(emptyBranch, 0, 0)).size());
        assertEquals(5, Texts.expandTemplateLines("%summary_lines%",
                summaryReplacements(filledBranch, 4, 2)).size());
    }

    @Test
    @DisplayName("裸值占位符逐行引用时替换为标量")
    void substitutesScalarPlaceholders() {
        Map<String, Object> replacements = summaryReplacements(List.of(), 4, 2);
        assertEquals(List.of("总孔数: 4 / 已开孔: 2"),
                Texts.expandTemplateLines("总孔数: %total% / 已开孔: %opened%", replacements));
    }

    @Test
    @DisplayName("未提供的占位符原样保留，因此每条分支必须供全键")
    void unmatchedPlaceholderSurvivesLiterally() {
        List<String> rendered = Texts.expandTemplateLines("未开孔: %locked%",
                summaryReplacements(List.of(), 4, 2));
        assertEquals(List.of("未开孔: %locked%"), rendered);
        assertTrue(rendered.getFirst().contains("%locked%"));
    }

    @Test
    @DisplayName("混合行不展开为多行，前缀保留")
    void mixedLineKeepsPrefixAndStaysSingleLine() {
        List<String> rendered = Texts.expandTemplateLines("统计: %summary_lines%",
                summaryReplacements(List.of("a", "b"), 0, 0));
        assertEquals(1, rendered.size());
        assertTrue(rendered.getFirst().startsWith("统计: "));
    }

    @Test
    @DisplayName("空集合整行占位符展开为零行")
    void emptyCollectionExpandsToNoLines() {
        assertEquals(List.of(), Texts.expandTemplateLines("%summary_lines%",
                summaryReplacements(List.of(), 0, 0)));
    }

    @Test
    @DisplayName("标量值不触发整行展开")
    void scalarSolePlaceholderStaysSingleLine() {
        List<String> rendered = Texts.expandTemplateLines("%total%",
                summaryReplacements(List.of(), 4, 2));
        assertEquals(List.of("4"), rendered);
    }

    @Test
    @DisplayName("custom_name 走标量替换，不做整行展开")
    void titlePlaceholderResolvesAsSingleValue() {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("summary_title", "<gold>宝石槽统计</gold>");
        assertEquals("<gold>宝石槽统计</gold>",
                Texts.formatTemplate("%summary_title%", replacements));
    }
}
