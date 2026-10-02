package emaki.jiuwu.craft.corelib.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.action.ActionContext;

@DisplayName("占位符注册表解析管线")
class PlaceholderRegistryTest {

    private final ActionContext context = ActionContext.create(null, "test", true);

    @Test
    @DisplayName("extraVariables 覆盖 contextVariables 中的同名键")
    void extraVariablesOverrideContextVariables() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        assertEquals("op", registry.resolve(context, Map.of("phase", "op"), "%phase%"));
        assertEquals("test", registry.resolve(context, Map.of(), "%phase%"));
        ActionContext withPlaceholder = context.withPlaceholders(Map.of("ctx_val", "来自上下文"));
        assertEquals("来自附加变量",
                registry.resolve(withPlaceholder, Map.of("ctx_val", "来自附加变量"), "%ctx_val%"));
        assertEquals("来自上下文",
                registry.resolve(withPlaceholder, Map.of("other", "x"), "%ctx_val%"));
    }

    @Test
    @DisplayName("注册 PAPI 类型 resolver 不阻断自定义 resolver 段")
    void customResolversRunAlongsidePapiResolvers() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        List<String> order = new ArrayList<>();
        registry.register(new PlaceholderApiResolver());
        registry.register((ctx, text) -> {
            order.add("custom");
            return text.replace("%custom%", "已解析");
        });
        registry.register(new PlaceholderApiResolver());
        assertEquals("已解析 %remaining%", registry.resolve(context, "%custom% %remaining%"));
        assertEquals(List.of("custom"), order);
    }

    @Test
    @DisplayName("自定义 resolver 按注册顺序链式传递结果")
    void customResolversChainInRegistrationOrder() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register((ctx, text) -> text.replace("%first%", "一步"));
        registry.register((ctx, text) -> text + "|二步");
        assertEquals("一步|二步", registry.resolve(context, "%first%"));
    }

    @Test
    @DisplayName("未匹配占位符保留原文")
    void unmatchedPlaceholderSurvivesLiterally() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register((ctx, text) -> text.replace("%known%", "值"));
        assertEquals("值 %unknown%", registry.resolve(context, "%known% %unknown%"));
    }

    @Test
    @DisplayName("空文本与无标记文本短路，不进入 resolver 管线")
    void shortCircuitsEmptyOrMarkerFreeText() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        List<String> invoked = new ArrayList<>();
        registry.register((ctx, text) -> {
            invoked.add(text);
            return text;
        });
        assertNull(registry.resolve(context, null));
        assertEquals("", registry.resolve(context, ""));
        assertEquals("纯文本", registry.resolve(context, "纯文本"));
        assertEquals(List.of(), invoked);
        registry.resolve(context, "<gold>标签</gold>");
        assertEquals(List.of("<gold>标签</gold>"), invoked);
    }

    @Test
    @DisplayName("单参 resolve 与三参重载空附加变量结果一致")
    void legacyOverloadMatchesNewOverloadWithEmptyExtras() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register((ctx, text) -> text.replace("%k%", "v"));
        String text = "%k% %phase%";
        assertEquals(registry.resolve(context, text), registry.resolve(context, Map.of(), text));
        assertEquals("v test", registry.resolve(context, text));
    }
}
