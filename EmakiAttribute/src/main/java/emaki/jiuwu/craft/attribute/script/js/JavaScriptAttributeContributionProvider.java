package emaki.jiuwu.craft.attribute.script.js;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.LivingEntity;

import emaki.jiuwu.craft.attribute.api.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.corelib.api.script.ScriptServerApi.ScriptEntityApi;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.text.Texts;
import org.jetbrains.annotations.NotNull;

public final class JavaScriptAttributeContributionProvider implements AttributeContributionProvider {

    private final org.bukkit.plugin.Plugin plugin;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final String id;
    private final int priority;
    private final String scriptPath;
    private final String functionName;

    public JavaScriptAttributeContributionProvider(org.bukkit.plugin.Plugin plugin,
            JavaScriptService javaScriptService,
            ScriptConfig scriptConfig,
            String id,
            int priority,
            String scriptPath,
            String functionName) {
        this.plugin = plugin;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
        this.id = Texts.normalizeId(id);
        this.priority = priority;
        this.scriptPath = scriptPath;
        this.functionName = Texts.isBlank(functionName) ? "collect" : functionName;
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public @NotNull Collection<AttributeContribution> collect(@NotNull LivingEntity entity) {
        if (javaScriptService == null || !javaScriptService.enabled() || entity == null) {
            return List.of();
        }
        ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                plugin,
                null,
                scriptPath,
                functionName,
                List.of(new ScriptEntityApi(entity)),
                Map.of("provider", id),
                scriptConfig.clampTimeoutMillis(scriptConfig.engine().defaultTimeoutMillis()),
                true
        ));
        if (result == null || !result.success() || result.returnValue() == null) {
            return List.of();
        }
        try {
            return parseContributions(result.returnValue());
        } catch (IllegalStateException exception) {
            if (isClosedContextException(exception)) {
                return List.of();
            }
            throw exception;
        }
    }

    private boolean isClosedContextException(IllegalStateException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("Context is already closed")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Collection<AttributeContribution> parseContributions(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("attributes")) {
                return contributionsFromPayload(map);
            }
            return contributionsFromAttributes("", map);
        }
        if (value instanceof Iterable<?> iterable) {
            List<AttributeContribution> result = new ArrayList<>();
            for (Object entry : iterable) {
                result.addAll(parseContributions(entry));
            }
            return List.copyOf(result);
        }
        return List.of();
    }

    private Collection<AttributeContribution> contributionsFromPayload(Map<?, ?> payload) {
        Object sourceValue = payload.containsKey("sourceId") ? payload.get("sourceId") : payload.get("source_id");
        String sourceId = Texts.toStringSafe(sourceValue == null ? id : sourceValue);
        Object attributes = payload.get("attributes");
        return attributes instanceof Map<?, ?> map ? contributionsFromAttributes(sourceId, map) : List.of();
    }

    private Collection<AttributeContribution> contributionsFromAttributes(String sourceId, Map<?, ?> attributes) {
        List<AttributeContribution> result = new ArrayList<>();
        for (Map.Entry<?, ?> entry : attributes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            Double value = parseDouble(entry.getValue());
            if (value != null) {
                result.add(new AttributeContribution(Texts.normalizeId(entry.getKey().toString()), value, Texts.isBlank(sourceId) ? id : sourceId));
            }
        }
        return List.copyOf(result);
    }

    private Double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Texts.toStringSafe(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
