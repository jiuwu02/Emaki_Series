package emaki.jiuwu.craft.attribute.bridge;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.attribute.api.AttributeCatalog;
import emaki.jiuwu.craft.attribute.api.AttributeOperations;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.PdcAttributeAccess;
import emaki.jiuwu.craft.attribute.api.event.PlayerResourceConsumeEvent;
import emaki.jiuwu.craft.attribute.api.extension.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.api.extension.AttributeExtensions;
import emaki.jiuwu.craft.attribute.api.extension.ContributionProviderRegistration;
import emaki.jiuwu.craft.attribute.api.extension.ItemContributionGate;
import emaki.jiuwu.craft.attribute.api.extension.ItemContributionGateRegistration;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.api.model.DamageResult;
import emaki.jiuwu.craft.attribute.api.model.ResourceDefinitionView;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.attribute.service.ContributionProviderRegistrationRegistry;
import emaki.jiuwu.craft.attribute.service.ItemContributionGateRegistry;
import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.text.Texts;

/** Runtime mapping from the four-layer public facade to existing Attribute services. */
public final class ServiceBackedEmakiAttributeBridge implements EmakiAttributeApi.Bridge,
        AttributeCatalog,
        AttributeOperations,
        AttributeExtensions {

    private final AttributeServiceFacade attributeService;
    private final ThreadOwnership threadOwnership;
    private final ItemContributionGateRegistry gateRegistry;
    private final ContributionProviderRegistrationRegistry contributionRegistry;
    private final PdcAttributeAccess pdcAccess;

    public ServiceBackedEmakiAttributeBridge(AttributeServiceFacade attributeService,
            ThreadOwnership threadOwnership,
            ItemContributionGateRegistry gateRegistry,
            ContributionProviderRegistrationRegistry contributionRegistry,
            PdcAttributeAccess pdcAccess) {
        this.attributeService = attributeService;
        this.threadOwnership = threadOwnership;
        this.gateRegistry = gateRegistry;
        this.contributionRegistry = contributionRegistry;
        this.pdcAccess = pdcAccess;
    }

    @Override
    public @NotNull ApiStatus status() {
        if (attributeService == null || attributeService.plugin() == null || !attributeService.plugin().isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String pluginName = attributeService.plugin().getName();
        String version = attributeService.plugin().getDescription().getVersion();
        return runtimeReady()
                ? ApiStatus.ready(pluginName, version, version)
                : ApiStatus.loading(pluginName, version, version);
    }

    @Override
    public @NotNull AttributeCatalog catalog() {
        return this;
    }

    @Override
    public @NotNull AttributeOperations operations() {
        return this;
    }

    @Override
    public @NotNull AttributeExtensions extensions() {
        return this;
    }

    @Override
    public EmakiResult<Double> attributeValue(Player player, String attributeId) {
        EmakiResult<Unit> playerCheck = validateOwnedPlayer(player);
        if (playerCheck.isFailure()) {
            return playerCheck.retypeFailure();
        }
        if (Texts.isBlank(attributeId)) {
            return EmakiResult.invalidInput("attribute.attribute_id_invalid");
        }
        if (attributeService.attributeRegistry().resolve(attributeId) == null) {
            return EmakiResult.notFound("attribute.attribute_not_found");
        }
        try {
            AttributeSnapshot snapshot = attributeService.collectPlayerCombatSnapshot(player);
            Double value = attributeService.resolveAttributeValue(snapshot, attributeId);
            return value == null
                    ? EmakiResult.notFound("attribute.attribute_value_not_found")
                    : EmakiResult.success(value);
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.attribute_value_failed");
        }
    }

    @Override
    public EmakiResult<Double> resourceCurrent(Player player, String resourceId) {
        EmakiResult<ResourceState> state = resourceState(player, resourceId);
        return state.hasValue()
                ? EmakiResult.success(state.orElse(null).currentValue())
                : state.retypeFailure();
    }

    @Override
    public EmakiResult<Double> resourceMax(Player player, String resourceId) {
        EmakiResult<ResourceState> state = resourceState(player, resourceId);
        return state.hasValue()
                ? EmakiResult.success(state.orElse(null).currentMax())
                : state.retypeFailure();
    }

    @Override
    public EmakiResult<AttributeSnapshot> itemSnapshot(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("attribute.item_invalid");
        }
        try {
            AttributeSnapshot snapshot = attributeService.collectItemSnapshot(itemStack);
            return snapshot == null
                    ? EmakiResult.internalError("attribute.item_snapshot_missing")
                    : EmakiResult.success(snapshot);
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.item_snapshot_failed");
        }
    }

    @Override
    public EmakiResult<AttributeSnapshot> combatSnapshot(LivingEntity entity) {
        EmakiResult<Unit> entityCheck = validateOwnedEntity(entity, "attribute.entity_invalid");
        if (entityCheck.isFailure()) {
            return entityCheck.retypeFailure();
        }
        try {
            AttributeSnapshot snapshot = attributeService.collectCombatSnapshot(entity);
            return snapshot == null
                    ? EmakiResult.internalError("attribute.combat_snapshot_missing")
                    : EmakiResult.success(snapshot);
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.combat_snapshot_failed");
        }
    }

    @Override
    public Map<String, ResourceDefinitionView> resources() {
        if (attributeService == null || attributeService.resourceDefinitions().isEmpty()) {
            return Map.of();
        }
        Map<String, ResourceDefinitionView> views = new LinkedHashMap<>();
        for (Map.Entry<String, ResourceDefinition> entry : attributeService.resourceDefinitions().entrySet()) {
            ResourceDefinition definition = entry.getValue();
            if (definition != null) {
                views.put(entry.getKey(), toView(definition));
            }
        }
        return views.isEmpty() ? Map.of() : Map.copyOf(views);
    }

    @Override
    public Set<String> attributeIds() {
        if (attributeService == null || attributeService.attributeRegistry() == null) {
            return Set.of();
        }
        return immutableIds(attributeService.attributeRegistry().all().keySet());
    }

    @Override
    public Set<String> damageTypeIds() {
        if (attributeService == null || attributeService.damageTypeRegistry() == null) {
            return Set.of();
        }
        return immutableIds(attributeService.damageTypeRegistry().all().keySet());
    }

    @Override
    public boolean isItemContributionActive(Player player, ItemStack itemStack, String slotName) {
        if (gateRegistry == null || itemStack == null || itemStack.getType().isAir()) {
            return true;
        }
        return gateRegistry.rejectingGateId(player, itemStack, slotName).isEmpty();
    }

    @Override
    public EmakiResult<Unit> consumeResource(Player player, String resourceId, double amount) {
        EmakiResult<Unit> playerCheck = validateOwnedPlayer(player);
        if (playerCheck.isFailure()) {
            return playerCheck;
        }
        if (Texts.isBlank(resourceId) || !Double.isFinite(amount) || amount < 0D) {
            return EmakiResult.invalidInput("attribute.resource_consume_invalid");
        }
        String normalizedId = Texts.normalizeId(resourceId);
        ResourceDefinition definition = attributeService.resourceDefinitions().get(normalizedId);
        if (definition == null) {
            return EmakiResult.notFound("attribute.resource_not_found");
        }
        try {
            ResourceState state = attributeService.readResourceState(player, normalizedId);
            if (state == null) {
                return EmakiResult.notFound("attribute.resource_state_not_found");
            }
            if (state.currentValue() < amount) {
                return EmakiResult.rejected("attribute.resource_insufficient");
            }
            PlayerResourceConsumeEvent consumeEvent = new PlayerResourceConsumeEvent(
                    player, normalizedId, amount, state.currentValue(), state.currentMax());
            Bukkit.getPluginManager().callEvent(consumeEvent);
            if (consumeEvent.isCancelled()) {
                return EmakiResult.failure(FailureKind.CANCELLED, "attribute.resource_consume_cancelled");
            }
            double resolvedAmount = consumeEvent.getAmount();
            if (!Double.isFinite(resolvedAmount) || resolvedAmount < 0D) {
                return EmakiResult.invalidInput("attribute.resource_consume_event_amount_invalid");
            }
            if (state.currentValue() < resolvedAmount) {
                return EmakiResult.rejected("attribute.resource_insufficient");
            }
            AttributeSnapshot snapshot = attributeService.collectPlayerCombatSnapshot(player);
            attributeService.syncResource(player,
                    definition,
                    snapshot,
                    ResourceSyncReason.MANUAL,
                    state.currentValue() - resolvedAmount);
            return EmakiResult.ok();
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.resource_consume_failed");
        }
    }

    @Override
    public EmakiResult<Unit> scheduleEquipmentSync(Player player) {
        EmakiResult<Unit> playerCheck = validateOnlinePlayer(player);
        if (playerCheck.isFailure()) {
            return playerCheck;
        }
        try {
            attributeService.scheduleEquipmentSync(player);
            return EmakiResult.ok();
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.equipment_sync_schedule_failed");
        }
    }

    @Override
    public EmakiResult<DamageResult> calculateDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, Object> context) {
        EmakiResult<String> validation = validateDamageCall(attacker, target, damageTypeId, baseDamage);
        if (validation.isFailure()) {
            return validation.retypeFailure();
        }
        try {
            DamageResult result = attributeService.calculateDamage(
                    attacker, target, validation.orElse(""), baseDamage, context);
            return result == null || Texts.isBlank(result.damageTypeId())
                    ? EmakiResult.internalError("attribute.damage_calculation_failed")
                    : EmakiResult.success(result);
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.damage_calculation_failed");
        }
    }

    @Override
    public EmakiResult<Unit> applyDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, Object> context) {
        EmakiResult<String> validation = validateDamageCall(attacker, target, damageTypeId, baseDamage);
        if (validation.isFailure()) {
            return validation.retypeFailure();
        }
        try {
            return attributeService.applyDamage(attacker, target, validation.orElse(""), baseDamage, context)
                    ? EmakiResult.ok()
                    : EmakiResult.rejected("attribute.damage_not_applied");
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.damage_apply_failed");
        }
    }

    @Override
    public EmakiResult<Unit> resyncPlayer(Player player) {
        EmakiResult<Unit> playerCheck = validateOwnedPlayer(player);
        if (playerCheck.isFailure()) {
            return playerCheck;
        }
        try {
            attributeService.resyncPlayer(player);
            return EmakiResult.ok();
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.player_resync_failed");
        }
    }

    @Override
    public ContributionProviderRegistration registerContributionProvider(Plugin owner,
            AttributeContributionProvider provider) {
        return contributionRegistry == null
                ? ContributionProviderRegistration.noop()
                : contributionRegistry.register(owner, provider);
    }

    @Override
    public ItemContributionGateRegistration registerItemContributionGate(Plugin owner, ItemContributionGate gate) {
        return gateRegistry == null
                ? ItemContributionGateRegistration.noop()
                : gateRegistry.register(owner, gate);
    }

    @Override
    public @NotNull PdcAttributeAccess pdc() {
        return pdcAccess;
    }

    private boolean runtimeReady() {
        return pdcAccess != null
                && contributionRegistry != null
                && attributeService.attributeRegistry() != null
                && attributeService.attributeRegistry().loaded()
                && attributeService.damageTypeRegistry() != null
                && attributeService.damageTypeRegistry().loaded()
                && attributeService.defaultProfileRegistry() != null
                && attributeService.defaultProfileRegistry().loaded();
    }

    private EmakiResult<ResourceState> resourceState(Player player, String resourceId) {
        EmakiResult<Unit> playerCheck = validateOwnedPlayer(player);
        if (playerCheck.isFailure()) {
            return playerCheck.retypeFailure();
        }
        if (Texts.isBlank(resourceId)) {
            return EmakiResult.invalidInput("attribute.resource_id_invalid");
        }
        String normalizedId = Texts.normalizeId(resourceId);
        if (!attributeService.resourceDefinitions().containsKey(normalizedId)) {
            return EmakiResult.notFound("attribute.resource_not_found");
        }
        try {
            ResourceState state = attributeService.readResourceState(player, normalizedId);
            return state == null
                    ? EmakiResult.notFound("attribute.resource_state_not_found")
                    : EmakiResult.success(state);
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.resource_read_failed");
        }
    }

    private EmakiResult<String> validateDamageCall(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage) {
        if (target == null || !Double.isFinite(baseDamage) || baseDamage < 0D) {
            return EmakiResult.invalidInput("attribute.damage_input_invalid");
        }
        if (!target.isValid() || target.isDead()) {
            return EmakiResult.rejected("attribute.damage_target_inactive");
        }
        if (target instanceof Player player && !player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        if (!isEntityOwned(target) || (attacker != null && !isEntityOwned(attacker))) {
            return EmakiResult.wrongThread();
        }
        String resolvedType = Texts.isBlank(damageTypeId) ? attributeService.defaultDamageTypeId() : damageTypeId;
        var definition = attributeService.damageTypeRegistry().resolve(resolvedType);
        return definition == null
                ? EmakiResult.notFound("attribute.damage_type_not_found")
                : EmakiResult.success(definition.id());
    }

    private EmakiResult<Unit> validateOwnedPlayer(Player player) {
        EmakiResult<Unit> online = validateOnlinePlayer(player);
        if (online.isFailure()) {
            return online;
        }
        return isEntityOwned(player) ? EmakiResult.ok() : EmakiResult.wrongThread();
    }

    private EmakiResult<Unit> validateOnlinePlayer(Player player) {
        if (player == null) {
            return EmakiResult.invalidInput("attribute.player_invalid");
        }
        return player.isOnline() ? EmakiResult.ok() : EmakiResult.targetOffline();
    }

    private EmakiResult<Unit> validateOwnedEntity(LivingEntity entity, String invalidReason) {
        if (entity == null) {
            return EmakiResult.invalidInput(invalidReason);
        }
        if (entity instanceof Player player && !player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        return isEntityOwned(entity) ? EmakiResult.ok() : EmakiResult.wrongThread();
    }

    private boolean isEntityOwned(LivingEntity entity) {
        return threadOwnership != null && entity != null && threadOwnership.isEntityOwned(entity);
    }

    private static ResourceDefinitionView toView(ResourceDefinition definition) {
        return new ResourceDefinitionView(
                definition.id(),
                definition.displayName(),
                definition.defaultMax(),
                definition.minMax(),
                definition.maxMax(),
                definition.syncToBukkit(),
                definition.fullOnInit(),
                definition.regenPerSecond());
    }

    private static Set<String> immutableIds(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<String> copy = new LinkedHashSet<>();
        for (String id : ids) {
            String normalized = Texts.normalizeId(id);
            if (Texts.isNotBlank(normalized)) {
                copy.add(normalized);
            }
        }
        return copy.isEmpty() ? Set.of() : Set.copyOf(copy);
    }
}
