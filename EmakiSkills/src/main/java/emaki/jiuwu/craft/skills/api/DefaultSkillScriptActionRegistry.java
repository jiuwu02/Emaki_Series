package emaki.jiuwu.craft.skills.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.skills.api.SkillActionErrorType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class DefaultSkillScriptActionRegistry implements SkillScriptActionRegistry {

    private final Map<String, SkillScriptAction> actions = new LinkedHashMap<>();
    private final Map<String, Plugin> owners = new LinkedHashMap<>();

    @Override
    public SkillActionResult register(Plugin owner, SkillScriptAction action) {
        if (owner == null) {
            return SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT, "Skill script action owner cannot be null.");
        }
        if (action == null || Texts.isBlank(action.id())) {
            return SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT, "Skill script action id cannot be blank.");
        }
        String id = Texts.normalizeId(action.id());
        if (actions.containsKey(id)) {
            return SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT, "Skill script action already registered: " + id);
        }
        actions.put(id, action);
        owners.put(id, owner);
        return SkillActionResult.ok();
    }

    @Override
    public void unregister(String actionId) {
        String id = Texts.normalizeId(actionId);
        actions.remove(id);
        owners.remove(id);
    }

    @Override
    public void unregisterAll(Plugin owner) {
        if (owner == null) {
            return;
        }
        List<String> remove = new ArrayList<>();
        for (Map.Entry<String, Plugin> entry : owners.entrySet()) {
            if (owner.equals(entry.getValue())) {
                remove.add(entry.getKey());
            }
        }
        for (String id : remove) {
            unregister(id);
        }
    }

    @Override
    public SkillScriptAction get(String actionId) {
        return actions.get(Texts.normalizeId(actionId));
    }

    @Override
    public Plugin ownerOf(String actionId) {
        return owners.get(Texts.normalizeId(actionId));
    }

    @Override
    public Map<String, SkillScriptAction> all() {
        return Map.copyOf(actions);
    }

    @Override
    public List<SkillScriptAction> byOwner(Plugin owner) {
        if (owner == null) {
            return List.of();
        }
        List<SkillScriptAction> result = new ArrayList<>();
        for (Map.Entry<String, SkillScriptAction> entry : actions.entrySet()) {
            if (owner.equals(owners.get(entry.getKey()))) {
                result.add(entry.getValue());
            }
        }
        return List.copyOf(result);
    }
}
