package emaki.jiuwu.craft.attribute.action;

import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;

public final class AttributeActions {

    private AttributeActions() {
    }

    public static void registerAll(ActionRegistry registry, AttributeServiceFacade attributeService) {
        if (registry == null || attributeService == null) {
            return;
        }
        unregisterAll(registry);
        registry.register(new AttributeDamageAction(attributeService));
        registry.register(new TemporaryAttributeAction(TemporaryAttributeAction.ADD_ID, attributeService));
        registry.register(new TemporaryAttributeAction(TemporaryAttributeAction.SET_ID, attributeService));
        registry.register(new TemporaryAttributeAction(TemporaryAttributeAction.REMOVE_ID, attributeService));
    }

    public static void unregisterAll(ActionRegistry registry) {
        if (registry == null) {
            return;
        }
        registry.unregister(AttributeDamageAction.ID);
        registry.unregister(TemporaryAttributeAction.ADD_ID);
        registry.unregister(TemporaryAttributeAction.SET_ID);
        registry.unregister(TemporaryAttributeAction.REMOVE_ID);
    }
}
