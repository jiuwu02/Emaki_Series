package emaki.jiuwu.craft.corelib.action.pipeline;

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

    private final Supplier<PlaceholderRegistry> registrySupplier;

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

    private static Map<String, String> variablePlaceholders(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            if (Texts.isBlank(entry.getKey())) {
                continue;
            }
            String key = Texts.lower(entry.getKey());
            placeholders.put(key.startsWith("var.") ? key : "var." + key, entry.getValue());
        }
        return Map.copyOf(placeholders);
    }

    private static Player playerOf(PipelineContext context) {
        return context.caster().entityOrNull() instanceof Player player ? player : null;
    }
}
