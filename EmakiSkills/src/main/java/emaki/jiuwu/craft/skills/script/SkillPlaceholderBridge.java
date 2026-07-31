package emaki.jiuwu.craft.skills.script;

import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.v2.PipelineContext;
import emaki.jiuwu.craft.corelib.action.v2.PlaceholderBridge;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;

/**
 * Renders skill placeholders for a v2 pipeline.
 *
 * <p>Replaces {@code SkillScriptExecutor.resolveText}. The substitution order is unchanged, so the variable
 * names a skill YAML already uses keep working: pipeline variables first, then CoreLib's placeholder registry
 * (which is where PlaceholderAPI is reached). Renaming those variables into the {@code %var.*%} /
 * {@code %skill.*%} namespaces belongs to the phase 6 converter, not here.</p>
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
        Map<String, String> variables = context == null ? Map.of() : context.variables();
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

    private static Player casterOf(PipelineContext context) {
        if (context == null) {
            return null;
        }
        return context.caster().entityOrNull() instanceof Player player ? player : null;
    }
}
