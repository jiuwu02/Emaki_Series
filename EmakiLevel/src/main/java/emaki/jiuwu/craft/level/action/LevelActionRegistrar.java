package emaki.jiuwu.craft.level.action;

import java.util.List;

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
        register(registry, LevelOperationType.ADD_EXP, List.of("emakileveladdexp", "elvaddexp", "leveladdexp", "emakilevel_add_exp", "level_add_exp"));
        register(registry, LevelOperationType.SET_EXP, List.of("emakilevelsetexp", "elvsetexp", "levelsetexp", "emakilevel_set_exp", "level_set_exp"));
        register(registry, LevelOperationType.REMOVE_EXP, List.of("emakilevelremoveexp", "elvremoveexp", "elvtakeexp", "levelremoveexp", "emakilevel_remove_exp", "level_remove_exp", "leveltakeexp", "level_take_exp"));
        register(registry, LevelOperationType.ADD_LEVEL, List.of("emakileveladdlevel", "elvaddlevel", "leveladdlevel", "emakilevel_add_level", "level_add_level"));
        register(registry, LevelOperationType.SET_LEVEL, List.of("emakilevelsetlevel", "elvsetlevel", "levelsetlevel", "emakilevel_set_level", "level_set_level"));
        register(registry, LevelOperationType.REMOVE_LEVEL, List.of("emakilevelremovelevel", "elvremovelevel", "elvtakelevel", "levelremovelevel", "emakilevel_remove_level", "level_remove_level", "leveltakelevel", "level_take_level"));
        register(registry, LevelOperationType.RESET, List.of("emakilevelreset", "elvreset", "levelreset", "emakilevel_reset", "level_reset"));
        register(registry, LevelOperationType.LEVEL_UP, List.of("emakilevellevelup", "elvlevelup", "levellevelup", "emakilevel_level_up", "level_level_up", "levelup", "level_up"));
    }

    private void register(ActionRegistry registry, LevelOperationType type, List<String> ids) {
        for (String id : ids) {
            registry.register(plugin, "emakilevel", new LevelOperationAction(plugin, id, type));
        }
    }
}
