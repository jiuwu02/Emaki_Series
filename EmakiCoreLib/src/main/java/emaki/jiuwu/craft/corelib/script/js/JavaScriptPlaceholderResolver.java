package emaki.jiuwu.craft.corelib.script.js;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderResolver;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptPlaceholderResolver implements PlaceholderResolver {

    private final Plugin plugin;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final String id;
    private final Pattern pattern;
    private final String scriptPath;
    private final String functionName;
    private final long timeoutMillis;

    public JavaScriptPlaceholderResolver(Plugin plugin,
            JavaScriptService javaScriptService,
            ScriptConfig scriptConfig,
            String id,
            String scriptPath,
            String functionName,
            long timeoutMillis) {
        this.plugin = plugin;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
        this.id = Texts.normalizeId(id);
        this.pattern = Pattern.compile("%" + Pattern.quote(this.id) + "%", Pattern.CASE_INSENSITIVE);
        this.scriptPath = scriptPath;
        this.functionName = Texts.isBlank(functionName) ? "resolve" : functionName;
        this.timeoutMillis = timeoutMillis <= 0L ? this.scriptConfig.engine().defaultTimeoutMillis() : timeoutMillis;
    }

    public String id() {
        return id;
    }

    @Override
    public String resolve(ActionContext context, String text) {
        if (Texts.isBlank(text) || javaScriptService == null || !javaScriptService.enabled()) {
            return text;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        String replacement = resolveValue(context);
        matcher.reset();
        return matcher.replaceAll(Matcher.quoteReplacement(replacement));
    }

    private String resolveValue(ActionContext context) {
        ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                context == null ? plugin : context.sourcePlugin(),
                context,
                scriptPath,
                functionName,
                List.of(context == null ? Map.of() : context, Map.of("id", id)),
                Map.of("placeholder", id, "script", scriptPath),
                scriptConfig.clampTimeoutMillis(timeoutMillis),
                true
        ));
        if (result == null || result.skipped() || !result.success()) {
            return "";
        }
        Object value = result.returnValue();
        if (value == null && result.output().containsKey("value")) {
            value = result.output().get("value");
        }
        return Texts.toStringSafe(value);
    }
}
