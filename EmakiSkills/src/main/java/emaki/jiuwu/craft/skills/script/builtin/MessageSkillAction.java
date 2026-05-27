package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class MessageSkillAction extends AbstractSkillScriptAction {

    public MessageSkillAction() {
        super("message", "feedback", "Send skill message.",
                ActionParameter.required("text", ActionParameterType.STRING, "Message text"),
                ActionParameter.optional("target", ActionParameterType.STRING, "caster", "Target"));
    }

    @Override
    public CompletableFuture<ActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        Entity target = entityTarget(context, arguments);
        if (target instanceof Player player) {
            AdventureSupport.sendMiniMessage(context.plugin(), player, arg(arguments, "text", ""));
        }
        return completed(ActionResult.ok());
    }
}
