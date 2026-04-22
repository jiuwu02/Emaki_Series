package emaki.jiuwu.craft.attribute.bridge;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.corelib.integration.EmakiAttributeBridge;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ServiceBackedEmakiAttributeBridge implements EmakiAttributeBridge {

    private final AttributeServiceFacade attributeService;

    public ServiceBackedEmakiAttributeBridge(AttributeServiceFacade attributeService) {
        this.attributeService = attributeService;
    }

    @Override
    public boolean available() {
        return attributeService != null;
    }

    @Override
    public double readResourceCurrent(Player player, String resourceId) {
        ResourceState state = readResourceState(player, resourceId);
        return state == null ? -1D : state.currentValue();
    }

    @Override
    public double readResourceMax(Player player, String resourceId) {
        ResourceState state = readResourceState(player, resourceId);
        return state == null ? -1D : state.currentMax();
    }

    @Override
    public boolean consumeResource(Player player, String resourceId, double amount) {
        if (player == null || Texts.isBlank(resourceId) || amount < 0D || attributeService == null) {
            return false;
        }
        ResourceState state = attributeService.readResourceState(player, resourceId);
        if (state == null || state.currentValue() < amount) {
            return false;
        }
        ResourceDefinition definition = attributeService.resourceDefinitions().get(Texts.normalizeId(resourceId));
        if (definition == null) {
            return false;
        }
        AttributeSnapshot snapshot = attributeService.collectPlayerCombatSnapshot(player);
        attributeService.syncResource(
                player,
                definition,
                snapshot,
                ResourceSyncReason.MANUAL,
                state.currentValue() - amount
        );
        return true;
    }

    @Override
    public double readAttributeValue(Player player, String attributeId) {
        if (player == null || Texts.isBlank(attributeId) || attributeService == null) {
            return 0D;
        }
        AttributeSnapshot snapshot = attributeService.collectPlayerCombatSnapshot(player);
        Double value = attributeService.resolveAttributeValue(snapshot, attributeId);
        return value == null ? 0D : value;
    }

    private ResourceState readResourceState(Player player, String resourceId) {
        if (player == null || Texts.isBlank(resourceId) || attributeService == null) {
            return null;
        }
        return attributeService.readResourceState(player, resourceId);
    }
}
