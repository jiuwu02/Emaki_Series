package emaki.jiuwu.craft.skills.trigger;

import org.bukkit.plugin.java.JavaPlugin;

public interface SkillTriggerSource {

    String id();

    void register(JavaPlugin plugin, TriggerDispatcher dispatcher);
}
