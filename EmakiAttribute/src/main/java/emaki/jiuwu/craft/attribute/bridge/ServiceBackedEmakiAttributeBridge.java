package emaki.jiuwu.craft.attribute.bridge;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.corelib.api.integration.EmakiAttributeBridge;
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

    @Override
    public void scheduleEquipmentSync(Player player) {
        if (player == null || attributeService == null) {
            return;
        }
        attributeService.scheduleEquipmentSync(player);
    }

    @Override
    public boolean applyDamage(LivingEntity attacker, LivingEntity target, String damageTypeId, double baseDamage, Map<String, Object> context) {
        if (target == null || attributeService == null) {
            return false;
        }
        String resolvedType = Texts.isBlank(damageTypeId) ? attributeService.defaultDamageTypeId() : damageTypeId;
        return attributeService.applyDamage(attacker, target, resolvedType, baseDamage, context);
    }

    private ResourceState readResourceState(Player player, String resourceId) {
        if (player == null || Texts.isBlank(resourceId) || attributeService == null) {
            return null;
        }
        return attributeService.readResourceState(player, resourceId);
    }
}
