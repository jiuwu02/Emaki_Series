package emaki.jiuwu.craft.attribute.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;

final class ResourceManagementService {

    private static final String HEALTH_RESOURCE_ID = "health";

    private final AttributeService service;
    private final Set<UUID> pendingEquipmentSyncs = ConcurrentHashMap.newKeySet();

    ResourceManagementService(AttributeService service) {
        this.service = service;
    }

    public void resyncAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncPlayer(player, ResourceSyncReason.MANUAL, null, false);
        }
    }

    public void regenerateOnlinePlayers() {
        int intervalTicks = Math.max(1, service.config().regenIntervalTicks());
        double intervalSeconds = intervalTicks / 20D;
        Map<String, ResourceDefinition> resources = service.resourceDefinitions();
        for (Player player : Bukkit.getOnlinePlayers()) {
            AttributeSnapshot snapshot = service.collectCombatSnapshot(player);
            for (ResourceDefinition resourceDefinition : resources.values()) {
                ResourceState existing = readResourceState(player, resourceDefinition.id());
                if (existing == null) {
                    continue;
                }
                double regenPerSecond = resourceDefinition.regenPerSecond();
                for (AttributeDefinition definition : service.registryService().resourceRegenDefinitions().getOrDefault(resourceDefinition.id(), List.of())) {
                    Double value = snapshot == null ? null : snapshot.values().get(definition.id());
                    if (value == null) {
                        continue;
                    }
                    regenPerSecond += value;
                }
                if (regenPerSecond == 0D) {
                    continue;
                }
                double nextValue = existing.currentValue() + (regenPerSecond * intervalSeconds);
                syncResource(player, resourceDefinition, snapshot, ResourceSyncReason.REGEN, nextValue);
            }
        }
    }

    public void resyncPlayer(Player player) {
        if (player != null) {
            scheduleHealthSync(player);
        }
    }

    public void scheduleJoinHealthSync(Player player) {
        schedulePlayer(player, online -> {
            ResourceState existingHealth = readResourceState(online, HEALTH_RESOURCE_ID);
            if (existingHealth == null || existingHealth.currentValue() <= 0D) {
                syncPlayer(online, ResourceSyncReason.HEALTH_CHANGE, null, true);
            } else {
                syncPlayer(online, ResourceSyncReason.HEALTH_CHANGE, existingHealth.currentValue(), false);
            }
        });
    }

    public void scheduleRespawnHealthSync(Player player) {
        schedulePlayer(player, online -> syncPlayer(online, ResourceSyncReason.HEALTH_CHANGE, null, true));
    }

    public void scheduleHealthSync(LivingEntity entity) {
        if (entity instanceof Player player) {
            schedulePlayer(player, online -> syncPlayer(online, ResourceSyncReason.HEALTH_CHANGE, online.getHealth(), false));
        }
    }

    public void scheduleEquipmentSync(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        // Equipment is sampled when the task runs, so one pending sync per player is
        // enough to apply the latest inventory state within the current delay window.
        if (!pendingEquipmentSyncs.add(playerId)) {
            return;
        }
        service.plugin().getServer().getScheduler().runTaskLater(
                service.plugin(),
                () -> {
                    try {
                        Player online = Bukkit.getPlayer(playerId);
                        if (online != null && online.isOnline()) {
                            syncPlayer(online, ResourceSyncReason.EQUIPMENT, null, false);
                        }
                    } finally {
                        pendingEquipmentSyncs.remove(playerId);
                    }
                },
                Math.max(1, service.config().syncDelayTicks())
        );
    }

    public void scheduleLivingEntitySync(LivingEntity entity) {
        scheduleLivingEntity(entity, this::syncLivingEntity);
    }

    public void syncLivingEntity(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return;
        }
        if (entity instanceof Player player) {
            syncPlayer(player, ResourceSyncReason.EQUIPMENT, null, false);
            return;
        }
        AttributeSnapshot snapshot = service.collectCombatSnapshot(entity);
        service.vanillaSynchronizer().syncVanillaMappedAttributes(
                entity,
                snapshot,
                service.registryService().vanillaAttributeBindings(),
                service.registryService().vanillaMappedAttributes()
        );
    }

    public void syncPlayer(Player player, ResourceSyncReason reason, Double healthOverride) {
        syncPlayer(player, reason, healthOverride, false);
    }

    public ResourceState syncResource(Player player,
            ResourceDefinition resourceDefinition,
            AttributeSnapshot snapshot,
            ResourceSyncReason reason,
            Double currentValueOverride) {
        if (player == null || resourceDefinition == null) {
            return null;
        }
        ResourceState existing = service.stateRepository().readResourceState(player, resourceDefinition.id());
        boolean existingState = existing != null;
        double defaultMax = resourceDefinition.defaultMax();
        double flatBonus = 0D;
        double percentBonus = 0D;
        for (AttributeDefinition definition : service.registryService().resourceAttributeDefinitions().getOrDefault(resourceDefinition.id(), List.of())) {
            Double value = snapshot == null ? null : snapshot.values().get(definition.id());
            if (value == null) {
                continue;
            }
            if (definition.valueKind() == AttributeValueKind.REGEN
                    || definition.valueKind() == AttributeValueKind.DERIVED
                    || definition.valueKind() == AttributeValueKind.CHANCE) {
                continue;
            }
            if (definition.valueKind() == AttributeValueKind.PERCENT) {
                percentBonus += value;
            } else {
                flatBonus += value;
            }
        }
        double factor = AttributeFusionMath.percentFactor(percentBonus, true);
        double currentMax = AttributeFusionMath.usesFusedCombatValues(snapshot)
                ? resourceDefinition.clampMax((defaultMax * factor) + flatBonus)
                : resourceDefinition.clampMax((defaultMax + flatBonus) * factor);
        double currentValue;
        if (currentValueOverride != null) {
            currentValue = currentValueOverride;
        } else if (reason == ResourceSyncReason.INITIALIZE || !existingState) {
            currentValue = resourceDefinition.fullOnInit() ? currentMax : defaultMax;
        } else {
            currentValue = existing.currentValue();
        }
        currentValue = Math.max(0D, Math.min(currentValue, currentMax));
        String sourceSignature = emaki.jiuwu.craft.corelib.pdc.SignatureUtil.combine(
                service.defaultProfileSignature(),
                snapshot == null ? "" : snapshot.sourceSignature(),
                resourceDefinition.id(),
                Double.toString(defaultMax),
                Double.toString(flatBonus),
                Double.toString(percentBonus)
        );
        ResourceState state = new ResourceState(
                resourceDefinition.id(),
                defaultMax,
                currentMax - defaultMax,
                currentMax,
                currentValue,
                sourceSignature,
                ResourceState.CURRENT_SCHEMA_VERSION
        );
        if (!existingState
                || !Objects.equals(existing.sourceSignature(), state.sourceSignature())
                || existing.currentMax() != state.currentMax()
                || existing.currentValue() != state.currentValue()) {
            service.stateRepository().writeResourceState(player, state);
        }
        if (resourceDefinition.syncToBukkit() && HEALTH_RESOURCE_ID.equals(resourceDefinition.id())) {
            syncHealthToBukkit(player, state);
        }
        return state;
    }

    public ResourceState readResourceState(Player player, String resourceId) {
        return service.stateRepository().readResourceState(player, resourceId);
    }

    public boolean isAttackCoolingDown(Player player) {
        if (player == null) {
            return false;
        }
        Long until = service.stateRepository().readAttackCooldownUntil(player);
        if (until == null || until <= 0L) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now >= until) {
            service.stateRepository().clearAttackCooldown(player);
            return false;
        }
        return true;
    }

    public int startAttackCooldown(Player player, AttributeSnapshot snapshot, ItemStack itemStack) {
        if (player == null) {
            return 0;
        }
        int cooldownTicks = service.vanillaSynchronizer().resolveAttackCooldownTicks(snapshot, service.registryService().genericAttackSpeedDefinitions());
        if (cooldownTicks <= 0) {
            service.stateRepository().clearAttackCooldown(player);
            return 0;
        }
        long until = System.currentTimeMillis() + (cooldownTicks * 50L);
        service.stateRepository().writeAttackCooldownUntil(player, until);
        ItemStack held = itemStack == null ? player.getInventory().getItemInMainHand() : itemStack;
        if (held != null && !held.getType().isAir()) {
            player.setCooldown(held.getType(), cooldownTicks);
        }
        return cooldownTicks;
    }

    private void syncPlayer(Player player, ResourceSyncReason reason, Double healthOverride, boolean forceHealthToFull) {
        if (player == null || reason == null) {
            return;
        }
        AttributeSnapshot snapshot = service.collectCombatSnapshot(player);
        service.stateRepository().writeCombatSnapshot(player, snapshot);
        syncPlayerResources(player, snapshot, reason, healthOverride, forceHealthToFull);
    }

    private void syncPlayerResources(Player player,
            AttributeSnapshot snapshot,
            ResourceSyncReason reason,
            Double healthOverride,
            boolean forceHealthToFull) {
        for (ResourceDefinition resourceDefinition : service.resourceDefinitions().values()) {
            Double override = HEALTH_RESOURCE_ID.equals(resourceDefinition.id()) ? healthOverride : null;
            ResourceSyncReason effectiveReason = forceHealthToFull && HEALTH_RESOURCE_ID.equals(resourceDefinition.id())
                    ? ResourceSyncReason.INITIALIZE
                    : reason;
            syncResource(player, resourceDefinition, snapshot, effectiveReason, override);
        }
        service.vanillaSynchronizer().syncMovementSpeed(player, snapshot, service.registryService().genericSpeedDefinitions());
        service.vanillaSynchronizer().syncVanillaMappedAttributes(
                player,
                snapshot,
                service.registryService().vanillaAttributeBindings(),
                service.registryService().vanillaMappedAttributes()
        );
    }

    private void syncHealthToBukkit(Player player, ResourceState state) {
        if (player == null || state == null) {
            return;
        }
        double rawMaxHealth = state.currentMax();
        double maxHealth = Math.max(1D, rawMaxHealth);
        double bukkitHealth = rawMaxHealth <= 0D
                ? 1D
                : Math.max(0D, Math.min(state.currentValue(), maxHealth));
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(maxHealth);
        }
        player.setHealth(Math.min(maxHealth, bukkitHealth));
    }

    private void schedulePlayer(Player player, Consumer<Player> action) {
        if (player == null || action == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        service.plugin().getServer().getScheduler().runTaskLater(
                service.plugin(),
                () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != null && online.isOnline()) {
                        action.accept(online);
                    }
                },
                Math.max(1, service.config().syncDelayTicks())
        );
    }

    private void scheduleLivingEntity(LivingEntity entity, Consumer<LivingEntity> action) {
        if (entity == null || action == null) {
            return;
        }
        UUID entityId = entity.getUniqueId();
        service.plugin().getServer().getScheduler().runTaskLater(
                service.plugin(),
                () -> {
                    Entity current = Bukkit.getEntity(entityId);
                    if (current instanceof LivingEntity livingEntity && livingEntity.isValid() && !livingEntity.isDead()) {
                        action.accept(livingEntity);
                    }
                },
                Math.max(1, service.config().syncDelayTicks())
        );
    }
}
