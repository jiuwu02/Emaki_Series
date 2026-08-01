package emaki.jiuwu.craft.skills.action.v2;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageRegistration;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;

/**
 * Registers this module's pipeline stages into EmakiCoreLib's single stage registry.
 *
 * <p>Registration is replayed after a CoreLib reload, which rebuilds the stage table; without the rebuild
 * callback these stages would disappear the first time a server owner reloaded.</p>
 */
public final class SkillsStageRegistrar {

    private final EmakiSkillsPlugin plugin;
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    public SkillsStageRegistrar(EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Registers every stage and asks to be replayed on reload. Safe to call twice. */
    public void register() {
        closeHandles();
        for (CoreActionStage stage : stages()) {
            CoreStageRegistration registration = EmakiCoreLibApi.registerActionStage(plugin, stage);
            if (registration.successful()) {
                handles.add(registration);
            } else {
                plugin.getLogger().warning("Failed to register pipeline stage '" + stage.id()
                        + "': " + registration.reasonKey());
            }
        }
        EmakiCoreLibApi.onStageRegistryRebuilt(plugin, this::register);
    }

    /** Revokes every stage this registrar installed. */
    public void unregister() {
        closeHandles();
    }

    private List<CoreActionStage> stages() {
        List<CoreActionStage> stages = new ArrayList<>();
        stages.add(new CastSkillStage(plugin));
        for (SkillLevelStage.Operation operation : SkillLevelStage.Operation.values()) {
            stages.add(new SkillLevelStage(plugin, operation));
        }
        for (SkillSlotStage.Operation operation : SkillSlotStage.Operation.values()) {
            stages.add(new SkillSlotStage(plugin, operation));
        }
        for (SkillCooldownStage.Operation operation : SkillCooldownStage.Operation.values()) {
            stages.add(new SkillCooldownStage(plugin, operation));
        }
        for (SkillLearnStage.Operation operation : SkillLearnStage.Operation.values()) {
            stages.add(new SkillLearnStage(plugin, operation));
        }
        return List.copyOf(stages);
    }

    private void closeHandles() {
        for (CoreStageRegistration handle : handles) {
            handle.close();
        }
        handles.clear();
    }
}
