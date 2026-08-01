package emaki.jiuwu.craft.skills.script;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.action.pipeline.PlaceholderBridge;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;

/**
 * Renders skill placeholders for a pipeline.
 *
 * <p>Pipeline variables are exposed only through the {@code %var.name%} namespace, then CoreLib's placeholder
 * registry runs for non-pipeline placeholders such as PlaceholderAPI expansions.</p>
 */
public final class SkillPlaceholderBridge implements PlaceholderBridge {

    private final EmakiSkillsPlugin plugin;

    /**
     * Creates the bridge.
     *
     * @param plugin the owning plugin, used to reach CoreLib's placeholder registry
     */
    public SkillPlaceholderBridge(EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String render(PipelineContext context, String template) {
        if (template == null) {
            return "";
        }
        if (Texts.isBlank(template) || template.indexOf('%') < 0) {
            return template;
        }
        Map<String, String> variables = context == null ? Map.of() : variablePlaceholders(context.variables());
        String resolved = Texts.formatTemplate(template, variables);
        PlaceholderRegistry registry = plugin == null || plugin.coreLib() == null
                ? null
                : plugin.coreLib().placeholderRegistry();
        if (registry == null) {
            return resolved;
        }
        Player caster = casterOf(context);
        ActionContext actionContext = ActionContext
                .create(plugin, caster, context == null ? "skill_script" : context.phase(), false)
                .withPlaceholders(variables);
        return Texts.toStringSafe(registry.resolve(actionContext, resolved));
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

    private static Player casterOf(PipelineContext context) {
        if (context == null) {
            return null;
        }
        return context.caster().entityOrNull() instanceof Player player ? player : null;
    }
}
