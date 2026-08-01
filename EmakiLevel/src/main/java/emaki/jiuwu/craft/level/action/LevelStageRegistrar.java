package emaki.jiuwu.craft.level.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.api.LevelOperationType;

/**
 * Registers this module's pipeline stages into EmakiCoreLib's single stage registry.
 *
 * <p>Registration is replayed after a CoreLib reload, which rebuilds the stage table; without the rebuild
 * callback these stages would disappear the first time a server owner reloaded.</p>
 */
public final class LevelStageRegistrar {

    /**
     * Stage id per operation.
     *
     * <p>Spelled out rather than derived from the enum name so the ids stay stable if the enum is ever
     * renamed, and so the snake_case form is visible in one place.</p>
     */
    private static final Map<LevelOperationType, String> STAGE_IDS = stageIds();

    private final EmakiLevelPlugin plugin;
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    public LevelStageRegistrar(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    private static Map<LevelOperationType, String> stageIds() {
        Map<LevelOperationType, String> ids = new LinkedHashMap<>();
        ids.put(LevelOperationType.ADD_EXP, "level_add_exp");
        ids.put(LevelOperationType.SET_EXP, "level_set_exp");
        ids.put(LevelOperationType.REMOVE_EXP, "level_remove_exp");
        ids.put(LevelOperationType.ADD_LEVEL, "level_add_level");
        ids.put(LevelOperationType.SET_LEVEL, "level_set_level");
        ids.put(LevelOperationType.REMOVE_LEVEL, "level_remove_level");
        ids.put(LevelOperationType.RESET, "level_reset");
        ids.put(LevelOperationType.LEVEL_UP, "level_up");
        return Map.copyOf(ids);
    }

    /** Registers every stage and asks to be replayed on reload. Safe to call twice. */
    public void register() {
        closeHandles();
        STAGE_IDS.forEach((operation, id) -> {
            CoreStageRegistration registration = EmakiCoreLibApi.registerActionStage(
                    plugin, new LevelOperationStage(plugin, operation, id));
            if (registration.successful()) {
                handles.add(registration);
            } else {
                plugin.getLogger().warning("Failed to register pipeline stage '" + id
                        + "': " + registration.reasonKey());
            }
        });
        EmakiCoreLibApi.onStageRegistryRebuilt(plugin, this::register);
    }

    /** Revokes every stage this registrar installed. */
    public void unregister() {
        closeHandles();
    }

    private void closeHandles() {
        for (CoreStageRegistration handle : handles) {
            handle.close();
        }
        handles.clear();
    }
}
