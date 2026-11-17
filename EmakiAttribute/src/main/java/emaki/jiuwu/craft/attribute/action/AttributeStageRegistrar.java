package emaki.jiuwu.craft.attribute.action;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.Map;
import java.util.logging.Level;

public final class AttributeStageRegistrar {

    private final Plugin owner;
    private final AttributeServiceFacade attributeService;
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    public AttributeStageRegistrar(Plugin owner, AttributeServiceFacade attributeService) {
        this.owner = owner;
        this.attributeService = attributeService;
    }

    public void register() {
        closeHandles();
        for (CoreActionStage stage : stages()) {
            CoreStageRegistration registration = EmakiCoreLibApi.registerActionStage(owner, stage);
            if (registration.successful()) {
                handles.add(registration);
            } else {
                LogTags.send(owner, LogTag.STARTUP, Level.WARNING, "startup.pipeline_stage_register_failed", Map.of("stage", stage.id(), "reason", registration.reasonKey()));
            }
        }
        EmakiCoreLibApi.onStageRegistryRebuilt(owner, this::register);
    }

    public void unregister() {
        closeHandles();
    }

    private List<CoreActionStage> stages() {
        List<CoreActionStage> stages = new ArrayList<>();
        stages.add(new AttributeDamageStage(attributeService));
        for (TemporaryAttributeStage.Operation operation : TemporaryAttributeStage.Operation.values()) {
            stages.add(new TemporaryAttributeStage(attributeService, operation));
        }
        for (ResourceModifyStage.Operation operation : ResourceModifyStage.Operation.values()) {
            stages.add(new ResourceModifyStage(attributeService, operation));
        }
        for (TemporaryAttributeTagStage.Operation operation : TemporaryAttributeTagStage.Operation.values()) {
            stages.add(new TemporaryAttributeTagStage(attributeService, operation));
        }
        for (AttributeSyncStage.Operation operation : AttributeSyncStage.Operation.values()) {
            stages.add(new AttributeSyncStage(attributeService, operation));
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
