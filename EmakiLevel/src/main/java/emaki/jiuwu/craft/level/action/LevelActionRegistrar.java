package emaki.jiuwu.craft.level.action;

import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.api.LevelOperationType;

public final class LevelActionRegistrar {

    private final EmakiLevelPlugin plugin;

    public LevelActionRegistrar(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(ActionRegistry registry) {
        if (registry == null) {
            return;
        }
        register(registry, LevelOperationType.ADD_EXP, "emakileveladdexp");
        register(registry, LevelOperationType.SET_EXP, "emakilevelsetexp");
        register(registry, LevelOperationType.REMOVE_EXP, "emakilevelremoveexp");
        register(registry, LevelOperationType.ADD_LEVEL, "emakileveladdlevel");
        register(registry, LevelOperationType.SET_LEVEL, "emakilevelsetlevel");
        register(registry, LevelOperationType.REMOVE_LEVEL, "emakilevelremovelevel");
        register(registry, LevelOperationType.RESET, "emakilevelreset");
        register(registry, LevelOperationType.LEVEL_UP, "emakilevellevelup");
    }

    private void register(ActionRegistry registry, LevelOperationType type, String id) {
        registry.register(plugin, "emakilevel", new LevelOperationAction(plugin, id, type));
    }
}
