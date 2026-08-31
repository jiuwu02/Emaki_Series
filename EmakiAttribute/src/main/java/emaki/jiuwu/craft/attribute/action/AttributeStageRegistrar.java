package emaki.jiuwu.craft.attribute.action;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;

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
                owner.getLogger().warning("Failed to register pipeline stage '" + stage.id()
                        + "': " + registration.reasonKey());
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
