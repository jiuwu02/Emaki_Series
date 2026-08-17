package emaki.jiuwu.craft.corelib.trigger;

import org.bukkit.plugin.java.JavaPlugin;

public interface TriggerSource {

    String id();

    void register(JavaPlugin plugin, TriggerDispatcher dispatcher);
}
