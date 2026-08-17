package emaki.jiuwu.craft.skills.trigger;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.trigger.TriggerDispatcher;

public interface SkillTriggerSource {

    String id();

    void register(JavaPlugin plugin, TriggerDispatcher dispatcher);
}
