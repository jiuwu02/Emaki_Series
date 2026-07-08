package emaki.jiuwu.craft.attribute.action;

import java.util.List;

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
        registerDamage(registry, owner, attributeService, List.of(AttributeDamageAction.ID, "attribute_damage"));
        registerTemporaryAttribute(registry, owner, attributeService, TemporaryAttributeAction.ADD_ID, List.of(TemporaryAttributeAction.ADD_ID, "temporary_attribute_add", "temp_attribute_add"));
        registerTemporaryAttribute(registry, owner, attributeService, TemporaryAttributeAction.SET_ID, List.of(TemporaryAttributeAction.SET_ID, "temporary_attribute_set", "temp_attribute_set"));
        registerTemporaryAttribute(registry, owner, attributeService, TemporaryAttributeAction.REMOVE_ID, List.of(TemporaryAttributeAction.REMOVE_ID, "temporary_attribute_remove", "temp_attribute_remove"));
        registerResourceConsume(registry, owner, attributeService, List.of(ResourceConsumeAction.ID, "resource_consume"));
        registerResourceModify(registry, owner, attributeService, ResourceModifyAction.Operation.ADD, List.of(ResourceModifyAction.ADD_ID, "attribute_resource_give", "resource_add"));
        registerResourceModify(registry, owner, attributeService, ResourceModifyAction.Operation.SET, List.of(ResourceModifyAction.SET_ID, "resource_set"));
        registerResourceModify(registry, owner, attributeService, ResourceModifyAction.Operation.REMOVE, List.of(ResourceModifyAction.REMOVE_ID, "attribute_resource_take", "resource_remove"));
        registerTemporaryAttributeTag(registry, owner, attributeService, TemporaryAttributeTagAction.ADD_ID, List.of(TemporaryAttributeTagAction.ADD_ID, "temporary_attribute_tag_add", "temp_attribute_tag_add"));
        registerTemporaryAttributeTag(registry, owner, attributeService, TemporaryAttributeTagAction.REMOVE_ID, List.of(TemporaryAttributeTagAction.REMOVE_ID, "temporary_attribute_tag_remove", "temp_attribute_tag_remove"));
        registerTemporaryAttributeTag(registry, owner, attributeService, TemporaryAttributeTagAction.CLEAR_ID, List.of(TemporaryAttributeTagAction.CLEAR_ID, "temporary_attribute_tag_clear", "temp_attribute_tag_clear"));
        registerSync(registry, owner, attributeService, AttributeSyncAction.SYNC_ID, List.of(AttributeSyncAction.SYNC_ID, "attribute_resync"));
        registerSync(registry, owner, attributeService, AttributeSyncAction.REFRESH_ID, List.of(AttributeSyncAction.REFRESH_ID, "attribute_reload"));
    }

    private static void registerDamage(ActionRegistry registry, Plugin owner, AttributeServiceFacade attributeService, List<String> ids) {
        for (String id : ids) {
            registry.register(owner, SOURCE, new AttributeDamageAction(id, attributeService));
        }
    }

    private static void registerTemporaryAttribute(ActionRegistry registry, Plugin owner, AttributeServiceFacade attributeService, String operationId, List<String> ids) {
        for (String id : ids) {
            registry.register(owner, SOURCE, new TemporaryAttributeAction(id, operationId, attributeService));
        }
    }

    private static void registerResourceConsume(ActionRegistry registry, Plugin owner, AttributeServiceFacade attributeService, List<String> ids) {
        for (String id : ids) {
            registry.register(owner, SOURCE, new ResourceConsumeAction(id, attributeService));
        }
    }

    private static void registerResourceModify(ActionRegistry registry, Plugin owner, AttributeServiceFacade attributeService, ResourceModifyAction.Operation operation, List<String> ids) {
        for (String id : ids) {
            registry.register(owner, SOURCE, new ResourceModifyAction(id, attributeService, operation));
        }
    }

    private static void registerTemporaryAttributeTag(ActionRegistry registry, Plugin owner, AttributeServiceFacade attributeService, String operationId, List<String> ids) {
        for (String id : ids) {
            registry.register(owner, SOURCE, new TemporaryAttributeTagAction(id, operationId, attributeService));
        }
    }

    private static void registerSync(ActionRegistry registry, Plugin owner, AttributeServiceFacade attributeService, String operationId, List<String> ids) {
        for (String id : ids) {
            registry.register(owner, SOURCE, new AttributeSyncAction(id, operationId, attributeService));
        }
    }

    public static void unregisterAll(ActionRegistry registry, Plugin owner) {
        if (registry == null || owner == null) {
            return;
        }
        registry.unregisterAll(owner);
    }
}
