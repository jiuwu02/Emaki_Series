package emaki.jiuwu.craft.corelib.script.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderResolver;
import emaki.jiuwu.craft.corelib.script.host.ScriptHost;
import emaki.jiuwu.craft.corelib.script.host.ScriptHostSettings;

@DisplayName("占位符绑定桥")
class PlaceholderBindingTest {

    @TempDir
    Path dataDirectory;

    private ScriptHost host;
    private PlaceholderRegistry placeholderRegistry;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warns = new ArrayList<>();
    private final List<PlaceholderResolver> resolvers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (host != null) {
            host.close();
            host = null;
        }
        for (PlaceholderResolver resolver : resolvers) {
            if (placeholderRegistry != null) {
                placeholderRegistry.unregister(resolver);
            }
        }
        resolvers.clear();
        errors.clear();
        warns.clear();
    }

    private void loadScripts(Map<String, String> files) throws IOException {
        Path scripts = dataDirectory.resolve("scripts");
        Files.createDirectories(scripts);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Files.writeString(scripts.resolve(entry.getKey()), entry.getValue());
        }
        placeholderRegistry = new PlaceholderRegistry();
        PlaceholderScriptBinding binding = new PlaceholderScriptBinding(
                () -> placeholderRegistry,
                () -> host == null ? null : host.callbackRunner(),
                errors::add,
                warns::add,
                resolvers);
        host = new ScriptHost(null, scripts, new ScriptHostSettings(true, 500, false),
                Map.of("placeholders", binding));
        host.load();
    }

    @Test
    @DisplayName("JS resolve 参与占位符解析链并返回替换文本")
    void resolveTransformsText() throws IOException {
        loadScripts(Map.of("mark.js", """
                placeholders.register({
                    id: 'sc_ph_mark',
                    resolve: function (text, ctx) {
                        return text.split('%sc_mark%').join('[demo]');
                    }
                });
                """));
        assertTrue(errors.isEmpty(), () -> "unexpected errors: " + errors);
        assertEquals(1, resolvers.size());
        assertEquals("前缀[demo]后缀",
                placeholderRegistry.resolve(null, "前缀%sc_mark%后缀"));
    }

    @Test
    @DisplayName("JS resolve 抛异常时返回原文本且不中断链")
    void throwingResolverKeepsChainIntact() throws IOException {
        loadScripts(Map.of(
                "boom.js", "placeholders.register({id: 'sc_ph_boom', "
                        + "resolve: function (text, ctx) { throw new Error('boom'); }});",
                "after.js", "placeholders.register({id: 'sc_ph_after', "
                        + "resolve: function (text, ctx) { return text + '!'; }});"));
        assertEquals(2, resolvers.size());
        assertEquals("原文%keep%!",
                placeholderRegistry.resolve(null, "原文%keep%"));
        assertEquals(1, warns.size());
        assertTrue(warns.getFirst().contains("sc_ph_boom"), () -> String.valueOf(warns));
    }

    @Test
    @DisplayName("null 返回值按原文本处理，数值返回值转为文本")
    void nullAndNonStringResultsAreHandled() throws IOException {
        loadScripts(Map.of("noop.js", """
                placeholders.register({
                    id: 'sc_ph_noop',
                    resolve: function (text, ctx) { return text === 'EMPTY' ? null : 12345; }
                });
                """));
        assertEquals(1, resolvers.size());
        assertEquals("EMPTY", resolvers.getFirst().resolve(null, "EMPTY"));
        assertEquals("12345", resolvers.getFirst().resolve(null, "X"));
    }

    @Test
    @DisplayName("缺失 id 或 resolve 被显式拒绝")
    void invalidPayloadsAreRejectedExplicitly() throws IOException {
        loadScripts(Map.of(
                "a_no_id.js", "placeholders.register({resolve: function (text, ctx) { return text; }});",
                "b_no_resolve.js", "placeholders.register({id: 'sc_ph_no_resolve'});"));
        assertEquals(0, resolvers.size());
        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("'id'"), () -> String.valueOf(errors));
        assertTrue(errors.get(1).contains("'resolve'"), () -> String.valueOf(errors));
    }
}
