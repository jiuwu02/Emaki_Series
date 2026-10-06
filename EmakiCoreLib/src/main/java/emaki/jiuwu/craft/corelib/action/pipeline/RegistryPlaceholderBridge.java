package emaki.jiuwu.craft.corelib.action.pipeline;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class RegistryPlaceholderBridge implements PlaceholderBridge {

    private static final int MAX_CACHED_TABLES = 256;

    private final Supplier<PlaceholderRegistry> registrySupplier;
    private final Map<Map<String, String>, Map<String, String>> variableTables = new IdentityHashMap<>();

    public RegistryPlaceholderBridge(@NotNull Supplier<PlaceholderRegistry> registrySupplier) {
        this.registrySupplier = registrySupplier;
    }

    @Override
    public @NotNull String render(@NotNull PipelineContext context, @Nullable String template) {
        if (template == null) {
            return "";
        }

        if (template.indexOf('%') < 0) {
            return template;
        }
        Map<String, String> variables = variablePlaceholders(context.variables());
        String resolved = Texts.formatTemplate(template, variables);
        PlaceholderRegistry registry = registrySupplier.get();
        if (registry == null) {
            return resolved;
        }
        ActionContext adapter = ActionContext
                .create(playerOf(context), context.phase(), context.silent())
                .withPlaceholders(variables);
        return Texts.toStringSafe(registry.resolve(adapter, resolved));
    }

    private Map<String, String> variablePlaceholders(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        synchronized (variableTables) {
            Map<String, String> cached = variableTables.get(variables);
            if (cached != null) {
                return cached;
            }
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            if (Texts.isBlank(entry.getKey())) {
                continue;
            }
            String key = Texts.lower(entry.getKey());
            placeholders.put(key.startsWith("var.") ? key : "var." + key, entry.getValue());
        }
        Map<String, String> table = Map.copyOf(placeholders);
        synchronized (variableTables) {
            if (variableTables.size() >= MAX_CACHED_TABLES) {
                variableTables.clear();
            }
            variableTables.put(variables, table);
        }
        return table;
    }

    private static Player playerOf(PipelineContext context) {
        return context.caster().entityOrNull() instanceof Player player ? player : null;
    }
}
