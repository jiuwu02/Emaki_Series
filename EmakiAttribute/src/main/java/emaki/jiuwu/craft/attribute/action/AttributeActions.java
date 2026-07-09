package emaki.jiuwu.craft.attribute.action;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;

public final class AttributeActions {

    public static final String SOURCE = "emakiattribute";

    private AttributeActions() {
    }

    public static void registerAll(ActionRegistry registry, Plugin owner, AttributeServiceFacade attributeService) {
        if (registry == null || owner == null || attributeService == null) {
            return;
        }
        unregisterAll(registry, owner);
        registry.register(owner, SOURCE, new AttributeDamageAction(AttributeDamageAction.ID, attributeService));
        registry.register(owner, SOURCE, new TemporaryAttributeAction(TemporaryAttributeAction.ADD_ID, TemporaryAttributeAction.ADD_ID, attributeService));
        registry.register(owner, SOURCE, new TemporaryAttributeAction(TemporaryAttributeAction.SET_ID, TemporaryAttributeAction.SET_ID, attributeService));
        registry.register(owner, SOURCE, new TemporaryAttributeAction(TemporaryAttributeAction.REMOVE_ID, TemporaryAttributeAction.REMOVE_ID, attributeService));
        registry.register(owner, SOURCE, new ResourceConsumeAction(ResourceConsumeAction.ID, attributeService));
        registry.register(owner, SOURCE, new ResourceModifyAction(ResourceModifyAction.ADD_ID, attributeService, ResourceModifyAction.Operation.ADD));
        registry.register(owner, SOURCE, new ResourceModifyAction(ResourceModifyAction.SET_ID, attributeService, ResourceModifyAction.Operation.SET));
        registry.register(owner, SOURCE, new ResourceModifyAction(ResourceModifyAction.REMOVE_ID, attributeService, ResourceModifyAction.Operation.REMOVE));
        registry.register(owner, SOURCE, new TemporaryAttributeTagAction(TemporaryAttributeTagAction.ADD_ID, TemporaryAttributeTagAction.ADD_ID, attributeService));
        registry.register(owner, SOURCE, new TemporaryAttributeTagAction(TemporaryAttributeTagAction.REMOVE_ID, TemporaryAttributeTagAction.REMOVE_ID, attributeService));
        registry.register(owner, SOURCE, new TemporaryAttributeTagAction(TemporaryAttributeTagAction.CLEAR_ID, TemporaryAttributeTagAction.CLEAR_ID, attributeService));
        registry.register(owner, SOURCE, new AttributeSyncAction(AttributeSyncAction.SYNC_ID, AttributeSyncAction.SYNC_ID, attributeService));
        registry.register(owner, SOURCE, new AttributeSyncAction(AttributeSyncAction.REFRESH_ID, AttributeSyncAction.REFRESH_ID, attributeService));
    }

    public static void unregisterAll(ActionRegistry registry, Plugin owner) {
        if (registry == null || owner == null) {
            return;
        }
        registry.unregisterAll(owner);
    }
}
