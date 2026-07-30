package emaki.jiuwu.craft.skills.api;

import java.util.List;
import java.util.Map;

import org.bukkit.plugin.Plugin;

/**
 * No-op script action registry used when EmakiSkills is unavailable.
 *
 * <p>All reads return empty / null, all writes return failure results. This allows callers to
 * obtain a registry reference without null-checking and defer the availability check to their first
 * actual use.
 */
final class EmptySkillScriptActionRegistry implements SkillScriptActionRegistry {

    EmptySkillScriptActionRegistry() {
    }

    @Override
    public SkillActionResult register(Plugin owner, SkillScriptAction action) {
        return SkillActionResult.failure(SkillActionErrorType.PROVIDER_UNAVAILABLE, "EmakiSkills is unavailable.");
    }

    @Override
    public void unregister(String actionId) {
        // Nothing to unregister.
    }

    @Override
    public void unregisterAll(Plugin owner) {
        // Nothing to unregister.
    }

    @Override
    public SkillScriptAction get(String actionId) {
        return null;
    }

    @Override
    public Plugin ownerOf(String actionId) {
        return null;
    }

    @Override
    public Map<String, SkillScriptAction> all() {
        return Map.of();
    }

    @Override
    public List<SkillScriptAction> byOwner(Plugin owner) {
        return List.of();
    }
}
