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
        register(registry, LevelOperationType.ADD_EXP, List.of("emakileveladdexp", "elvaddexp", "leveladdexp"));
        register(registry, LevelOperationType.SET_EXP, List.of("emakilevelsetexp", "elvsetexp", "levelsetexp"));
        register(registry, LevelOperationType.REMOVE_EXP, List.of("emakilevelremoveexp", "elvremoveexp", "elvtakeexp", "levelremoveexp"));
        register(registry, LevelOperationType.ADD_LEVEL, List.of("emakileveladdlevel", "elvaddlevel", "leveladdlevel"));
        register(registry, LevelOperationType.SET_LEVEL, List.of("emakilevelsetlevel", "elvsetlevel", "levelsetlevel"));
        register(registry, LevelOperationType.REMOVE_LEVEL, List.of("emakilevelremovelevel", "elvremovelevel", "elvtakelevel", "levelremovelevel"));
        register(registry, LevelOperationType.RESET, List.of("emakilevelreset", "elvreset", "levelreset"));
        register(registry, LevelOperationType.LEVEL_UP, List.of("emakilevellevelup", "elvlevelup", "levellevelup"));
    }

    private void register(ActionRegistry registry, LevelOperationType type, List<String> ids) {
        for (String id : ids) {
            registry.register(plugin, "emakilevel", new LevelOperationAction(plugin, id, type));
        }
    }
}
