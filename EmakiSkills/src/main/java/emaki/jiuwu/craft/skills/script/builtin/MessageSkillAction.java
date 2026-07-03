package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillActionParameterType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class MessageSkillAction extends AbstractSkillScriptAction {

    public MessageSkillAction() {
        super("message", "feedback", "Send skill message.",
                SkillActionParameter.required("text", SkillActionParameterType.STRING, "Message text"),
                SkillActionParameter.optional("target", SkillActionParameterType.STRING, "caster", "Target"));
    }

    @Override
    public CompletableFuture<SkillActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        Entity target = entityTarget(context, arguments);
        if (target instanceof Player player) {
            String text = arg(arguments, "text", "");
            if (text != null && !text.isBlank()) {
                player.sendMessage(MiniMessages.parse(text));
            }
        }
        return completed(SkillActionResult.ok());
    }
}
