package emaki.jiuwu.craft.skills.api;

import java.util.List;
import java.util.Map;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registry through which plugins contribute and manage {@link SkillScriptAction}
 * implementations.
 *
 * <p>Obtained via {@link EmakiSkillsApi#extensions()}{@code .scriptActions()}. Each action is
 * registered against an owning {@link Plugin} so that it can be cleaned up in
 * bulk when that plugin disables.
 */
@ApiStatus.NonExtendable
public interface SkillScriptActionRegistry {

    /** {@return the shared no-op registry used while the runtime registry is unavailable} */
    static SkillScriptActionRegistry empty() {
        return EmptyHolder.INSTANCE;
    }

    final class EmptyHolder {
        private static final SkillScriptActionRegistry INSTANCE = new EmptySkillScriptActionRegistry();

        private EmptyHolder() {
        }
    }

    /**
     * Registers a skill-script action under the given owner.
     *
     * @param owner  the plugin that owns the action
     * @param action the action to register
     * @return {@link SkillActionResult#ok()} on success, otherwise a failure result
     *         (e.g. id conflict or invalid action)
     */
    SkillActionResult register(Plugin owner, SkillScriptAction action);

    /**
     * Unregisters the action with the given id.
     *
     * @param actionId the id of the action to remove
     */
    void unregister(String actionId);

    /**
     * Unregisters every action owned by the given plugin.
     *
     * @param owner the owning plugin whose actions should be removed
     */
    void unregisterAll(Plugin owner);

    /**
     * {@return the action registered under the id, or {@code null} if none}
     *
     * @param actionId the action id to look up
     */
    SkillScriptAction get(String actionId);

    /**
     * {@return the plugin that owns the action, or {@code null} if unknown}
     *
     * @param actionId the action id to look up
     */
    Plugin ownerOf(String actionId);

    /** {@return an immutable view of all registered actions, keyed by id} */
    Map<String, SkillScriptAction> all();

    /**
     * {@return all actions owned by the given plugin}
     *
     * @param owner the owning plugin
     */
    List<SkillScriptAction> byOwner(Plugin owner);
}
