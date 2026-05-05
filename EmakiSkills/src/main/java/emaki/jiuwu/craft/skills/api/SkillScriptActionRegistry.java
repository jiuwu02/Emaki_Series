package emaki.jiuwu.craft.skills.api;

import java.util.List;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.ActionResult;

public interface SkillScriptActionRegistry {

    ActionResult register(Plugin owner, SkillScriptAction action);

    void unregister(String actionId);

    void unregisterAll(Plugin owner);

    SkillScriptAction get(String actionId);

    Plugin ownerOf(String actionId);

    Map<String, SkillScriptAction> all();

    List<SkillScriptAction> byOwner(Plugin owner);
}
