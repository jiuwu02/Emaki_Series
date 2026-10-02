package emaki.jiuwu.craft.corelib.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;

@DisplayName("物品操作模板渲染器注册表接入")
class OperationTemplateRendererRegistryTest {

    private final ActionContext context = ActionContext.create(null, "test", true);

    @Test
    @DisplayName("registry 为 null 时保持旧路径：已知变量替换、未知占位符保留")
    void nullRegistryKeepsLegacyBehavior() {
        OperationTemplateRenderer renderer = new OperationTemplateRenderer();
        assertEquals("总孔数: 4",
                renderer.renderTemplate("总孔数: %total%", Map.of("total", 4), context, null, "src"));
        assertEquals("缺失: %missing%",
                renderer.renderTemplate("缺失: %missing%", Map.of(), context, null, "src"));
        assertEquals(List.of("行: 4", "无变量"),
                renderer.renderTextLines(List.of("行: %total%", "无变量"), Map.of("total", 4), context, null, "src"));
        // 表达式模板结果经 renderPapi 尾段直连（无 player 时原样返回）
        assertEquals("<tag>",
                renderer.renderTemplate(Map.of("type", "string", "value", "<tag>"), Map.of(), context, null, "src"));
        assertEquals(List.of("<tag>"),
                renderer.renderTextLines(Map.of("type", "string", "value", "<tag>"), Map.of(), context, null, "src"));
    }

    @Test
    @DisplayName("registry 存在时 String 模板走注册表管线，操作变量优先于上下文变量")
    void registryPathResolvesStringTemplates() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register((ctx, text) -> text.replace("%custom_tag%", "<gold>已解析</gold>"));
        OperationTemplateRenderer renderer = new OperationTemplateRenderer(registry);
        assertEquals("<gold>已解析</gold>",
                renderer.renderTemplate("%custom_tag%", Map.of(), context, null, "src"));
        assertEquals("<gold>已解析</gold> 4",
                renderer.renderTemplate("%custom_tag% %total%", Map.of("total", 4), context, null, "src"));
        assertEquals("op", renderer.renderTemplate("%phase%", Map.of("phase", "op"), context, null, "src"));
    }

    @Test
    @DisplayName("registry 存在时表达式模板结果仍进入注册表 resolver 链")
    void registryPathCoversExpressionTemplates() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register((ctx, text) -> text.replace("<expr_tag>", "表达式已解析"));
        OperationTemplateRenderer renderer = new OperationTemplateRenderer(registry);
        assertEquals("表达式已解析",
                renderer.renderTemplate(Map.of("type", "string", "value", "<expr_tag>"), Map.of(), context, null, "src"));
    }

    @Test
    @DisplayName("registry 存在时 renderTextLines 的 String 行与表达式行都走 resolver 链")
    void registryPathCoversTextLines() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register((ctx, text) -> {
            return text.replace("%line_tag%", "行已解析").replace("<line_tag>", "行已解析");
        });
        OperationTemplateRenderer renderer = new OperationTemplateRenderer(registry);
        List<Object> raw = new ArrayList<>();
        raw.add("%line_tag%");
        raw.add(Map.of("type", "string", "value", "<line_tag>"));
        assertEquals(List.of("行已解析", "行已解析"),
                renderer.renderTextLines(raw, Map.of(), context, null, "src"));
    }
}
