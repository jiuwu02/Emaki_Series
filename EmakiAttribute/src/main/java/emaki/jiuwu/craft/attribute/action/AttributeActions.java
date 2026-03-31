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
    }

    public static void unregisterAll(ActionRegistry registry) {
        if (registry == null) {
            return;
        }
        registry.unregister(AttributeDamageAction.ID);
    }
}
