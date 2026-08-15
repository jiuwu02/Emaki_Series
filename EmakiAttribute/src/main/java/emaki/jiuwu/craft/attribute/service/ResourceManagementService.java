package emaki.jiuwu.craft.attribute.service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;

final class ResourceManagementService {

    private static final String HEALTH_RESOURCE_ID = "health";

    private final AttributeService service;
    private final Set<UUID> pendingEquipmentSyncs = ConcurrentHashMap.newKeySet();
    private volatile boolean healthDisplayScalingWarningLogged;

    ResourceManagementService(AttributeService service) {
        this.service = service;
    }

    public void resyncAllPlayers() {
        EmakiScheduling sched = scheduling();
        if (sched == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            sched.runForEntity(service.plugin(), player, () -> {
                if (isPlayerUsable(player)) {
                    syncPlayer(player, ResourceSyncReason.MANUAL, null, false);
                }
            }, null);
        }
    }

    public void regenerateOnlinePlayers() {
        EmakiScheduling sched = scheduling();
        if (sched == null) {
            return;
        }
        int intervalTicks = Math.max(1, service.config().regenIntervalTicks());
        double intervalSeconds = intervalTicks / 20D;
        Map<String, ResourceDefinition> resources = service.resourceDefinitions();
        for (Player player : Bukkit.getOnlinePlayers()) {
            sched.runForEntity(service.plugin(), player, () -> {
                if (isPlayerUsable(player)) {
                    regeneratePlayer(player, intervalSeconds, resources);
                }
            }, null);
        }
    }

    private void regeneratePlayer(
            Player player,
            double intervalSeconds,
            Map<String, ResourceDefinition> resources) {
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

            double baselineValue = resolveCurrentValueBaseline(player, resourceDefinition, existing);
            double nextValue = baselineValue + (regenPerSecond * intervalSeconds);
            boolean traceHealthRegen = HEALTH_RESOURCE_ID.equals(resourceDefinition.id()) && shouldDebugResource(player);
            ResourceState refreshed = syncResource(player, resourceDefinition, snapshot, ResourceSyncReason.REGEN, nextValue);
            if (traceHealthRegen) {
                Map<String, Object> replacements = debugReplacements(
                        "player", player.getName(),
                        "resource", resourceDefinition.id(),
                        "old_value", describeNumber(existing.currentValue()),
                        "baseline_value", describeNumber(baselineValue),
                        "regen_per_second", describeNumber(regenPerSecond),
                        "interval_seconds", describeNumber(intervalSeconds),
                        "candidate_value", describeNumber(nextValue)
                );
                putResourceState(replacements, "synced", refreshed);
                debugResource(player, "resource.regen", replacements);
            }
        }
    }

    public void resyncPlayer(Player player) {
        if (player != null) {
            scheduleHealthSync(player);
        }
    }

    public void scheduleJoinHealthSync(Player player) {
        schedulePlayer(player, "player_join", ResourceSyncReason.HEALTH_CHANGE, online -> {
            ResourceState existingHealth = readResourceState(online, HEALTH_RESOURCE_ID);
            if (existingHealth == null || existingHealth.currentValue() <= 0D) {
                syncPlayer(online, ResourceSyncReason.HEALTH_CHANGE, null, true);
            } else {

                syncPlayer(online, ResourceSyncReason.HEALTH_CHANGE, online.getHealth(), false);
            }
        });
    }

    public void scheduleRespawnHealthSync(Player player) {
        schedulePlayer(player, "player_respawn", ResourceSyncReason.HEALTH_CHANGE,
                online -> syncPlayer(online, ResourceSyncReason.HEALTH_CHANGE, null, true));
    }

    public void scheduleHealthSync(LivingEntity entity) {
        if (entity instanceof Player player) {
            schedulePlayer(player, "living_entity_health_change", ResourceSyncReason.HEALTH_CHANGE,
                    online -> syncPlayer(online, ResourceSyncReason.HEALTH_CHANGE, online.getHealth(), false));
        }
    }

    public void scheduleEquipmentSync(Player player) {
        scheduleEquipmentSync(player, "unspecified");
    }

    public void scheduleEquipmentSync(Player player, String trigger) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        String triggerName = trigger == null || trigger.isBlank() ? "unspecified" : trigger;
        debugEquipmentSync(player, "resync.equipment_request", Map.of("trigger", triggerName));
        if (!pendingEquipmentSyncs.add(playerId)) {
            debugEquipmentSync(player, "resync.equipment_coalesced", Map.of("trigger", triggerName));
            return;
        }
        debugEquipmentSync(player, "resync.equipment_queued", Map.of("trigger", triggerName));
        Runnable cleanupPending = () -> pendingEquipmentSyncs.remove(playerId);
        try {
            EmakiScheduling sched = scheduling();
            if (sched == null) {
                cleanupPending.run();
                debugEquipmentSync(player, "resync.equipment_dispatcher_unavailable", Map.of("trigger", triggerName));
                return;
            }
            TaskToken task = sched.runEntityLater(
                    service.plugin(),
                    player,
                    () -> {
                        cleanupPending.run();
                        debugEquipmentSync(player, "resync.equipment_execute", Map.of("trigger", triggerName));
                        if (isPlayerUsable(player)) {
                            syncPlayer(player, ResourceSyncReason.EQUIPMENT, null, false);
                            debugEquipmentSync(player, "resync.equipment_complete", Map.of("trigger", triggerName));
                        } else {
                            debugEquipmentSync(player, "resync.equipment_player_unavailable", Map.of("trigger", triggerName));
                        }
                    },
                    () -> {
                        cleanupPending.run();
                        debugEquipmentSync(player, "resync.equipment_retired", Map.of("trigger", triggerName));
                    },
                    Math.max(1, service.config().syncDelayTicks())
            );
            if (task == null) {
                cleanupPending.run();
                debugEquipmentSync(player, "resync.equipment_rejected", Map.of("trigger", triggerName));
            }
        } catch (RuntimeException | LinkageError exception) {
            cleanupPending.run();
            debugEquipmentSync(player, "resync.equipment_failed", Map.of(
                    "trigger", triggerName,
                    "error", exception.getClass().getSimpleName()
            ));
            throw exception;
        }
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
        double ownMax = AttributeFusionMath.usesFusedCombatValues(snapshot)
                ? resourceDefinition.clampMax((defaultMax * factor) + flatBonus)
                : resourceDefinition.clampMax((defaultMax + flatBonus) * factor);
        double currentMax = resolveHealthCeiling(player, resourceDefinition, ownMax, reason);
        double currentValue;
        if (currentValueOverride != null) {
            currentValue = currentValueOverride;
        } else if (reason == ResourceSyncReason.INITIALIZE || !existingState) {
            currentValue = resourceDefinition.fullOnInit() ? currentMax : defaultMax;
        } else {
            currentValue = resolveCurrentValueBaseline(player, resourceDefinition, existing);
        }
        currentValue = Math.max(0D, Math.min(currentValue, currentMax));
        String sourceSignature = SignatureUtil.combine(
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
        boolean writeState = !existingState
                || !Objects.equals(existing.sourceSignature(), state.sourceSignature())
                || existing.currentMax() != state.currentMax()
                || existing.currentValue() != state.currentValue();
        Map<String, Object> calculationReplacements = debugReplacements(
                "player", player.getName(),
                "resource", resourceDefinition.id(),
                "reason", describeReason(reason),
                "current_value_override", describeNumber(currentValueOverride),
                "default_max", describeNumber(defaultMax),
                "flat_bonus", describeNumber(flatBonus),
                "percent_bonus", describeNumber(percentBonus),
                "percent_factor", describeNumber(factor),
                "current_max", describeNumber(currentMax),
                "current_value", describeNumber(currentValue),
                "write_state", writeState
        );
        putResourceState(calculationReplacements, "existing", existing);
        debugResource(player, "resource.calculate", calculationReplacements);
        if (writeState) {
            service.stateRepository().writeResourceState(player, state);
        }
        Map<String, Object> stateReplacements = debugReplacements(
                "player", player.getName(),
                "resource", resourceDefinition.id(),
                "reason", describeReason(reason)
        );
        putResourceState(stateReplacements, "state", state);
        debugResource(player, writeState ? "resource.state_written" : "resource.state_unchanged", stateReplacements);
        if (resourceDefinition.syncToBukkit() && HEALTH_RESOURCE_ID.equals(resourceDefinition.id())) {
            syncHealthToBukkit(player, state, reason);
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
        if (service.config().attackSpeedAttributeOnly()
                && !service.vanillaSynchronizer().hasAttackSpeedValue(
                        snapshot,
                        service.registryService().genericAttackSpeedDefinitions())) {
            service.stateRepository().clearAttackCooldown(player);
            return 0;
        }
        int cooldownTicks = service.vanillaSynchronizer().resolveAttackCooldownTicks(
                player,
                snapshot,
                service.registryService().genericAttackSpeedDefinitions());
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

        service.vanillaSynchronizer().syncVanillaMappedAttributes(
                player,
                snapshot,
                service.registryService().vanillaAttributeBindings(),
                service.registryService().vanillaMappedAttributes()
        );
        for (ResourceDefinition resourceDefinition : service.resourceDefinitions().values()) {
            Double override = HEALTH_RESOURCE_ID.equals(resourceDefinition.id()) ? healthOverride : null;
            ResourceSyncReason effectiveReason = forceHealthToFull && HEALTH_RESOURCE_ID.equals(resourceDefinition.id())
                    ? ResourceSyncReason.INITIALIZE
                    : reason;
            syncResource(player, resourceDefinition, snapshot, effectiveReason, override);
        }
        service.vanillaSynchronizer().syncMovementSpeed(player, snapshot, service.registryService().genericSpeedDefinitions());
    }

    private double resolveHealthCeiling(Player player,
            ResourceDefinition resourceDefinition,
            double ownMax,
            ResourceSyncReason reason) {
        if (!resourceDefinition.syncToBukkit() || !HEALTH_RESOURCE_ID.equals(resourceDefinition.id())) {
            return ownMax;
        }
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute == null) {
            return ownMax;
        }
        double base = Math.max(1D, ownMax);
        maxHealthAttribute.setBaseValue(base);
        double effective = maxHealthAttribute.getValue();
        double ceiling = Double.isFinite(effective) && effective > 0D ? effective : base;
        debugResource(player, "resource.health_ceiling", debugReplacements(
                "player", player.getName(),
                "resource", resourceDefinition.id(),
                "reason", describeReason(reason),
                "own_max", describeNumber(ownMax),
                "bukkit_max_health_base", describeNumber(base),
                "bukkit_max_health_value", describeNumber(effective),
                "ceiling", describeNumber(ceiling)
        ));
        debugMaxHealthModifiers(player, reason, maxHealthAttribute);
        return ceiling;
    }

    private double resolveCurrentValueBaseline(Player player,
            ResourceDefinition resourceDefinition,
            ResourceState existing) {
        if (!resourceDefinition.syncToBukkit() || !HEALTH_RESOURCE_ID.equals(resourceDefinition.id())) {
            return existing == null ? 0D : existing.currentValue();
        }
        return player.getHealth();
    }

    private void syncHealthToBukkit(Player player, ResourceState state, ResourceSyncReason reason) {
        if (player == null || state == null) {
            return;
        }

        if (player.isDead()) {
            debugResource(player, "resource.bukkit_skipped_dead", debugReplacements(
                    "player", player.getName(),
                    "reason", describeReason(reason),
                    "resource_value", describeNumber(state.currentValue()),
                    "resource_max", describeNumber(state.currentMax()),
                    "bukkit_health", describeNumber(player.getHealth())
            ));
            return;
        }
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        debugBukkitHealth(player, state, reason, "before", maxHealthAttribute);
        double ceiling = maxHealthAttribute == null
                ? Math.max(1D, state.currentMax())
                : maxHealthAttribute.getValue();
        player.setHealth(Math.max(0D, Math.min(state.currentValue(), ceiling)));
        debugBukkitHealth(player, state, reason, "after", player.getAttribute(Attribute.MAX_HEALTH));
        syncHealthDisplayScaling(player);
    }

    void resetHealthDisplayScaling() {
        healthDisplayScalingWarningLogged = false;
        EmakiScheduling sched = scheduling();
        if (sched == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            sched.runForEntity(
                    service.plugin(),
                    player,
                    () -> resetHealthDisplayScaling(player),
                    null
            );
        }
    }

    private void syncHealthDisplayScaling(Player player) {
        if (player == null || !service.config().healthDisplayScalingEnabled()) {
            return;
        }
        double target = service.config().healthDisplayScalingTarget();
        if (!Double.isFinite(target) || target <= 0D) {
            resetHealthDisplayScaling(player);
            return;
        }
        try {
            player.setHealthScale(target);
            healthDisplayScalingWarningLogged = false;
        } catch (IllegalArgumentException exception) {
            resetHealthDisplayScaling(player);
            if (!healthDisplayScalingWarningLogged) {
                healthDisplayScalingWarningLogged = true;
                service.plugin().getLogger().warning("Invalid health_display_scaling.target '" + target + "': " + exception.getMessage());
            }
        }
    }

    private void resetHealthDisplayScaling(Player player) {
        if (player == null) {
            return;
        }
        try {
            player.setHealthScaled(false);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void debugEquipmentSync(Player player, String langKey, Map<String, ?> replacements) {
        if (service.plugin() == null || service.plugin().debugLogger() == null) {
            return;
        }
        service.plugin().debugLogger().log("resync", player, langKey, replacements);
    }

    private boolean shouldDebugResource(Player player) {
        return service.plugin() != null
                && service.plugin().debugLogger() != null
                && service.plugin().debugLogger().shouldLog("resource", player);
    }

    private void debugResource(Player player, String langKey, Map<String, ?> replacements) {
        if (service.plugin() == null || service.plugin().debugLogger() == null) {
            return;
        }
        service.plugin().debugLogger().log("resource", player, langKey, replacements);
    }

    private void debugBukkitHealth(Player player,
            ResourceState state,
            ResourceSyncReason reason,
            String phase,
            AttributeInstance maxHealthAttribute) {
        debugResource(player, "resource.bukkit_" + phase, Map.ofEntries(
                Map.entry("player", player.getName()),
                Map.entry("reason", describeReason(reason)),
                Map.entry("resource_value", describeNumber(state.currentValue())),
                Map.entry("resource_max", describeNumber(state.currentMax())),
                Map.entry("bukkit_health", describeNumber(player.getHealth())),
                Map.entry("bukkit_max_health_base", describeAttributeBase(maxHealthAttribute)),
                Map.entry("bukkit_max_health_value", describeAttributeValue(maxHealthAttribute))
        ));
    }

    private void debugMaxHealthModifiers(Player player, ResourceSyncReason reason, AttributeInstance maxHealthAttribute) {
        if (maxHealthAttribute == null || !shouldDebugResource(player)) {
            return;
        }
        List<AttributeModifier> modifiers = List.copyOf(maxHealthAttribute.getModifiers());
        int total = modifiers.size();
        for (int index = 0; index < total; index++) {
            AttributeModifier modifier = modifiers.get(index);
            debugResource(player, "resource.max_health_modifier", debugReplacements(
                    "player", player.getName(),
                    "reason", describeReason(reason),
                    "index", index + 1,
                    "total", total,
                    "modifier_key", modifier == null || modifier.getKey() == null ? "" : modifier.getKey().toString(),
                    "modifier_amount", modifier == null ? "" : describeNumber(modifier.getAmount()),
                    "modifier_operation", modifier == null || modifier.getOperation() == null ? "" : modifier.getOperation().name(),
                    "modifier_slot_group", modifier == null || modifier.getSlotGroup() == null ? "" : modifier.getSlotGroup().toString()
            ));
        }
    }

    private static String describeAttributeBase(AttributeInstance attribute) {
        return attribute == null ? "" : describeNumber(attribute.getBaseValue());
    }

    private static String describeAttributeValue(AttributeInstance attribute) {
        return attribute == null ? "" : describeNumber(attribute.getValue());
    }

    private static String describeNumber(Double value) {
        return value == null ? "" : describeNumber(value.doubleValue());
    }

    private static String describeNumber(double value) {
        return Double.toString(value);
    }

    private static String describeReason(ResourceSyncReason reason) {
        return reason == null ? "" : reason.name();
    }

    private static Map<String, Object> debugReplacements(Object... entries) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            replacements.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return replacements;
    }

    private static void putResourceState(Map<String, Object> replacements, String prefix, ResourceState state) {
        replacements.put(prefix + "_present", state != null);
        replacements.put(prefix + "_resource_id", state == null ? "" : state.resourceId());
        replacements.put(prefix + "_default_max", state == null ? "" : describeNumber(state.defaultMax()));
        replacements.put(prefix + "_bonus_max", state == null ? "" : describeNumber(state.bonusMax()));
        replacements.put(prefix + "_current_max", state == null ? "" : describeNumber(state.currentMax()));
        replacements.put(prefix + "_current_value", state == null ? "" : describeNumber(state.currentValue()));
        replacements.put(prefix + "_source_signature", state == null ? "" : state.sourceSignature());
        replacements.put(prefix + "_schema_version", state == null ? "" : state.schemaVersion());
    }

    private static boolean isPlayerUsable(Player player) {
        return player != null && player.isOnline() && player.isValid();
    }

    private void schedulePlayer(Player player,
            String source,
            ResourceSyncReason reason,
            Consumer<Player> action) {
        if (player == null || action == null) {
            return;
        }
        int delayTicks = Math.max(1, service.config().syncDelayTicks());
        debugResource(player, "resource.request", Map.ofEntries(
                Map.entry("player", player.getName()),
                Map.entry("source", source),
                Map.entry("reason", describeReason(reason)),
                Map.entry("delay_ticks", delayTicks)
        ));
        EmakiScheduling sched = scheduling();
        if (sched == null) {
            return;
        }
        sched.runEntityLater(
                service.plugin(),
                player,
                () -> {
                    if (isPlayerUsable(player)) {
                        ResourceState existingHealth = readResourceState(player, HEALTH_RESOURCE_ID);
                        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
                        Map<String, Object> replacements = debugReplacements(
                                "player", player.getName(),
                                "source", source,
                                "reason", describeReason(reason),
                                "delay_ticks", delayTicks,
                                "bukkit_health", describeNumber(player.getHealth()),
                                "bukkit_max_health_base", describeAttributeBase(maxHealthAttribute),
                                "bukkit_max_health_value", describeAttributeValue(maxHealthAttribute)
                        );
                        putResourceState(replacements, "stored", existingHealth);
                        debugResource(player, "resource.execute", replacements);
                        action.accept(player);
                    }
                },
                null,
                delayTicks
        );
    }

    private void scheduleLivingEntity(LivingEntity entity, Consumer<LivingEntity> action) {
        if (entity == null || action == null) {
            return;
        }
        EmakiScheduling sched = scheduling();
        if (sched == null) {
            return;
        }
        sched.runEntityLater(
                service.plugin(),
                entity,
                () -> {
                    if (entity.isValid() && !entity.isDead()) {
                        action.accept(entity);
                    }
                },
                null,
                Math.max(1, service.config().syncDelayTicks())
        );
    }

    private EmakiScheduling scheduling() {
        EmakiScheduling sched = service.scheduling();
        return sched != null ? sched : service.plugin().scheduling();
    }
}
