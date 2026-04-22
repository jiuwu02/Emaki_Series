package emaki.jiuwu.craft.skills.trigger;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * A source that listens to Bukkit events and converts them into
 * {@link TriggerInvocation}s dispatched through a {@link TriggerDispatcher}.
 */
public interface SkillTriggerSource {

    /**
     * @return the unique identifier of this trigger source
     */
    String id();

    /**
     * Register the underlying Bukkit event listeners with the given plugin
     * and route resulting invocations through the dispatcher.
     *
     * @param plugin     the owning plugin instance used for listener registration
     * @param dispatcher the dispatcher that will handle invocations
     */
    void register(JavaPlugin plugin, TriggerDispatcher dispatcher);
}
