package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeDamageEvent;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshotCodecs;
import emaki.jiuwu.craft.attribute.model.DamageRequest;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.attribute.model.DefaultProfile;
import emaki.jiuwu.craft.attribute.model.ProjectileDamageSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

public final class AttributeService {

    private static final long PROJECTILE_TTL_MS = 5 * 60 * 1000L;

    private final EmakiAttributePlugin plugin;
    private final PdcService pdcService;
    private volatile AttributeConfig config;
    private final AttributeRegistry attributeRegistry;
    private final DamageTypeRegistry damageTypeRegistry;
    private final DefaultProfileRegistry defaultProfileRegistry;
    private final LoreFormatRegistry loreFormatRegistry;
    private final AttributePresetRegistry presetRegistry;
    private final LoreParser loreParser;
    private final DamageEngine damageEngine;
    private final Map<String, AttributeContributionProvider> contributionProviders = new LinkedHashMap<>();

    private final PdcPartition itemPartition;
    private final PdcPartition combatPartition;
    private final PdcPartition projectilePartition;

    public AttributeService(EmakiAttributePlugin plugin,
                            PdcService pdcService,
                            AttributeConfig config,
                            AttributeRegistry attributeRegistry,
                            DamageTypeRegistry damageTypeRegistry,
                            DefaultProfileRegistry defaultProfileRegistry,
                            LoreFormatRegistry loreFormatRegistry,
                            AttributePresetRegistry presetRegistry) {
        this.plugin = plugin;
        this.pdcService = pdcService;
        this.config = config;
        this.attributeRegistry = attributeRegistry;
        this.damageTypeRegistry = damageTypeRegistry;
        this.defaultProfileRegistry = defaultProfileRegistry;
        this.loreFormatRegistry = loreFormatRegistry;
        this.presetRegistry = presetRegistry;
        this.loreParser = new LoreParser(attributeRegistry, loreFormatRegistry);
        this.damageEngine = new DamageEngine();
        this.itemPartition = pdcService.partition("item");
        this.combatPartition = pdcService.partition("combat");
        this.projectilePartition = pdcService.partition("projectile");
    }

    public AttributeConfig config() {
        return config;
    }

    public AttributeRegistry attributeRegistry() {
        return attributeRegistry;
    }

    public DamageTypeRegistry damageTypeRegistry() {
        return damageTypeRegistry;
    }

    public DefaultProfileRegistry defaultProfileRegistry() {
        return defaultProfileRegistry;
    }

    public LoreFormatRegistry loreFormatRegistry() {
        return loreFormatRegistry;
    }

    public AttributePresetRegistry presetRegistry() {
        return presetRegistry;
    }

    public LoreParser loreParser() {
        return loreParser;
    }

    public DamageEngine damageEngine() {
        return damageEngine;
    }

    public EmakiAttributePlugin plugin() {
        return plugin;
    }

    public void reloadConfig(AttributeConfig config) {
        this.config = config == null ? AttributeConfig.defaults() : config;
    }

    public String defaultDamageTypeId() {
        if (config.defaultDamageType() != null && !config.defaultDamageType().isBlank()) {
            DamageTypeDefinition definition = damageTypeRegistry.resolve(config.defaultDamageType());
            if (definition != null) {
                return definition.id();
            }
        }
        if (!damageTypeRegistry.all().isEmpty()) {
            return damageTypeRegistry.all().values().iterator().next().id();
        }
        return "physical";
    }

    public String defaultProjectileDamageTypeId() {
        DamageTypeDefinition projectile = damageTypeRegistry.resolve("projectile");
        if (projectile != null) {
            return projectile.id();
        }
        return defaultDamageTypeId();
    }

    public DamageTypeDefinition resolveDamageType(String damageTypeId) {
        String resolvedId = damageTypeId == null || damageTypeId.isBlank() ? defaultDamageTypeId() : normalizeId(damageTypeId);
        DamageTypeDefinition definition = damageTypeRegistry.resolve(resolvedId);
        if (definition != null) {
            return definition;
        }
        return new DamageTypeDefinition(resolvedId, resolvedId, List.of(), Set.of(), false, List.of(), null);
    }

    public String defaultProfileSignature() {
        return SignatureUtil.stableSignature(defaultProfileRegistry.mergedProfiles());
    }

    public Map<String, ResourceDefinition> resourceDefinitions() {
        Map<String, ResourceDefinition> merged = new LinkedHashMap<>();
        for (DefaultProfile profile : defaultProfileRegistry.mergedProfiles()) {
            for (ResourceDefinition resource : profile.resources().values()) {
                merged.putIfAbsent(resource.id(), resource);
            }
        }
        return merged;
    }

    public Map<String, Double> defaultAttributeValues() {
        Map<String, Double> merged = new LinkedHashMap<>();
        for (DefaultProfile profile : defaultProfileRegistry.mergedProfiles()) {
            for (Map.Entry<String, Double> entry : profile.attributeDefaults().entrySet()) {
                merged.putIfAbsent(normalizeId(entry.getKey()), entry.getValue());
            }
        }
        return merged;
    }

    public void registerContributionProvider(AttributeContributionProvider provider) {
        if (provider == null || provider.id() == null || provider.id().isBlank()) {
            return;
        }
        contributionProviders.put(normalizeId(provider.id()), provider);
    }

    public void unregisterContributionProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return;
        }
        contributionProviders.remove(normalizeId(providerId));
    }

    public AttributeSnapshot collectItemSnapshot(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return AttributeSnapshot.empty("");
        }
        var itemMeta = itemStack.getItemMeta();
        if (itemMeta == null || !itemMeta.hasLore()) {
            clearItemCache(itemStack);
            return AttributeSnapshot.empty("");
        }
        List<String> lore = itemMeta.getLore();
        List<String> normalizedLore = loreParser.normalizeLore(lore);
        if (normalizedLore.isEmpty()) {
            clearItemCache(itemStack);
            return AttributeSnapshot.empty(SignatureUtil.stableSignature(List.of()));
        }
        String sourceSignature = SignatureUtil.stableSignature(normalizedLore);
        String cachedSignature = pdcService.get(itemStack, itemPartition, "source_signature", PersistentDataType.STRING);
        AttributeSnapshot cachedSnapshot = pdcService.readBlob(itemStack, itemPartition, "snapshot", AttributeSnapshotCodecs.ATTRIBUTE_SNAPSHOT);
        if (sourceSignature.equals(cachedSignature) && cachedSnapshot != null) {
            return cachedSnapshot;
        }
        LoreParser.ParsedLore parsedLore = loreParser.parse(lore);
        AttributeSnapshot snapshot = parsedLore.snapshot();
        pdcService.set(itemStack, itemPartition, "schema_version", PersistentDataType.INTEGER, snapshot.schemaVersion());
        pdcService.set(itemStack, itemPartition, "source_signature", PersistentDataType.STRING, snapshot.sourceSignature());
        pdcService.writeBlob(itemStack, itemPartition, "snapshot", AttributeSnapshotCodecs.ATTRIBUTE_SNAPSHOT, snapshot);
        return snapshot;
    }

    public AttributeSnapshot collectCombatSnapshot(LivingEntity entity) {
        if (entity == null) {
            return AttributeSnapshot.empty("");
        }
        if (entity instanceof Player player) {
            return collectPlayerCombatSnapshot(player);
        }
        return collectLivingCombatSnapshot(entity);
    }

    public AttributeSnapshot collectPlayerCombatSnapshot(Player player) {
        if (player == null) {
            return AttributeSnapshot.empty("");
        }
        Map<String, Double> values = new LinkedHashMap<>();
        List<String> signatureParts = new ArrayList<>();
        mergeValues(values, defaultAttributeValues());
        signatureParts.add("defaults:" + SignatureUtil.stableSignature(defaultProfileRegistry.mergedProfiles()));
        PlayerInventory inventory = player.getInventory();
        List<ItemSlot> slots = List.of(
            new ItemSlot("main_hand", inventory.getItemInMainHand()),
            new ItemSlot("off_hand", inventory.getItemInOffHand()),
            new ItemSlot("helmet", inventory.getHelmet()),
            new ItemSlot("chestplate", inventory.getChestplate()),
            new ItemSlot("leggings", inventory.getLeggings()),
            new ItemSlot("boots", inventory.getBoots())
        );
        for (ItemSlot slot : slots) {
            AttributeSnapshot itemSnapshot = collectItemSnapshot(slot.item());
            if (itemSnapshot == null) {
                continue;
            }
            mergeValues(values, itemSnapshot.values());
            signatureParts.add(slot.name() + ":" + itemSnapshot.sourceSignature());
        }
        List<AttributeContributionProvider> providers = new ArrayList<>(contributionProviders.values());
        providers.sort(Comparator.comparingInt(AttributeContributionProvider::priority).reversed());
        for (AttributeContributionProvider provider : providers) {
            Collection<AttributeContribution> contributions = provider.collect(player);
            if (contributions == null || contributions.isEmpty()) {
                continue;
            }
            Map<String, Double> providerValues = new LinkedHashMap<>();
            for (AttributeContribution contribution : contributions) {
                if (contribution == null || contribution.attributeId() == null || contribution.attributeId().isBlank()) {
                    continue;
                }
                String id = normalizeId(contribution.attributeId());
                providerValues.merge(id, contribution.value(), Double::sum);
                values.merge(id, contribution.value(), Double::sum);
            }
            signatureParts.add(normalizeId(provider.id()) + ":" + SignatureUtil.stableSignature(providerValues));
        }
        String sourceSignature = SignatureUtil.stableSignature(signatureParts);
        AttributeSnapshot snapshot = new AttributeSnapshot(AttributeSnapshot.CURRENT_SCHEMA_VERSION, sourceSignature, values, System.currentTimeMillis());
        String cachedSignature = pdcService.get(player, combatPartition, "source_signature", PersistentDataType.STRING);
        AttributeSnapshot cachedSnapshot = pdcService.readBlob(player, combatPartition, "snapshot", AttributeSnapshotCodecs.ATTRIBUTE_SNAPSHOT);
        if (sourceSignature.equals(cachedSignature) && cachedSnapshot != null) {
            return cachedSnapshot;
        }
        syncCombatSnapshot(player, snapshot);
        return snapshot;
    }

    public void resyncAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduleHealthSync(player);
        }
    }

    public void regenerateOnlinePlayers() {
        int intervalTicks = Math.max(1, config.regenIntervalTicks());
        double intervalSeconds = intervalTicks / 20D;
        Map<String, ResourceDefinition> resources = resourceDefinitions();
        for (Player player : Bukkit.getOnlinePlayers()) {
            AttributeSnapshot snapshot = collectCombatSnapshot(player);
            for (ResourceDefinition resourceDefinition : resources.values()) {
                ResourceState existing = readResourceState(player, resourceDefinition.id());
                if (existing == null) {
                    continue;
                }
                double regenPerSecond = resourceDefinition.regenPerSecond();
                for (AttributeDefinition definition : attributeRegistry.all().values()) {
                    if (!resourceDefinition.id().equals(normalizeId(definition.targetId()))
                        || definition.targetType() != emaki.jiuwu.craft.attribute.model.AttributeTargetType.RESOURCE
                        || definition.valueKind() != AttributeValueKind.REGEN) {
                        continue;
                    }
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

    public void scheduleHealthSync(LivingEntity entity) {
        if (entity instanceof Player player) {
            java.util.UUID playerId = player.getUniqueId();
            plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != null && online.isOnline()) {
                        syncPlayer(online, ResourceSyncReason.HEALTH_CHANGE, online.getHealth());
                    }
                },
                Math.max(1, config.syncDelayTicks())
            );
        }
    }

    public void scheduleEquipmentSync(Player player) {
        if (player != null) {
            java.util.UUID playerId = player.getUniqueId();
            plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != null && online.isOnline()) {
                        syncPlayer(online, ResourceSyncReason.EQUIPMENT, null);
                    }
                },
                Math.max(1, config.syncDelayTicks())
            );
        }
    }

    public void syncPlayer(Player player, ResourceSyncReason reason, Double healthOverride) {
        if (player == null || reason == null) {
            return;
        }
        AttributeSnapshot snapshot = collectCombatSnapshot(player);
        syncCombatSnapshot(player, snapshot);
        Map<String, ResourceDefinition> resources = resourceDefinitions();
        for (ResourceDefinition resourceDefinition : resources.values()) {
            Double override = "health".equals(resourceDefinition.id()) ? healthOverride : null;
            syncResource(player, resourceDefinition, snapshot, reason, override);
        }
    }

    public ResourceState syncResource(Player player,
                                     ResourceDefinition resourceDefinition,
                                     AttributeSnapshot snapshot,
                                     ResourceSyncReason reason,
                                     Double currentValueOverride) {
        if (player == null || resourceDefinition == null) {
            return null;
        }
        PdcPartition resourcePartition = resourcePartition(resourceDefinition.id());
        ResourceState existing = readResourceState(player, resourceDefinition.id());
        boolean existingState = existing != null;
        double defaultMax = resourceDefinition.defaultMax();
        double flatBonus = 0D;
        double percentBonus = 0D;
        for (AttributeDefinition definition : attributeRegistry.all().values()) {
            if (!resourceDefinition.id().equals(normalizeId(definition.targetId())) || definition.targetType() != emaki.jiuwu.craft.attribute.model.AttributeTargetType.RESOURCE) {
                continue;
            }
            Double value = snapshot == null ? null : snapshot.values().get(definition.id());
            if (value == null) {
                continue;
            }
            if (definition.valueKind() == AttributeValueKind.REGEN
                || definition.valueKind() == AttributeValueKind.SKILL
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
        double currentMax = resourceDefinition.clampMax((defaultMax + flatBonus) * (1D + (percentBonus / 100D)));
        double currentValue;
        if (currentValueOverride != null) {
            currentValue = currentValueOverride;
        } else if (reason == ResourceSyncReason.INITIALIZE || !existingState) {
            currentValue = resourceDefinition.fullOnInit() ? currentMax : defaultMax;
        } else {
            currentValue = existing.currentValue();
        }
        currentValue = Math.max(0D, Math.min(currentValue, currentMax));
        String sourceSignature = SignatureUtil.combine(
            defaultProfileSignature(),
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
        if (!existingState || !Objects.equals(existing.sourceSignature(), state.sourceSignature()) || existing.currentMax() != state.currentMax() || existing.currentValue() != state.currentValue()) {
            pdcService.set(player, resourcePartition, "schema_version", PersistentDataType.INTEGER, state.schemaVersion());
            pdcService.set(player, resourcePartition, "default_max", PersistentDataType.DOUBLE, state.defaultMax());
            pdcService.set(player, resourcePartition, "bonus_max", PersistentDataType.DOUBLE, state.bonusMax());
            pdcService.set(player, resourcePartition, "current_max", PersistentDataType.DOUBLE, state.currentMax());
            pdcService.set(player, resourcePartition, "current_value", PersistentDataType.DOUBLE, state.currentValue());
            pdcService.set(player, resourcePartition, "source_signature", PersistentDataType.STRING, state.sourceSignature());
        }
        if (resourceDefinition.syncToBukkit() && "health".equals(resourceDefinition.id())) {
            double maxHealth = Math.max(1D, state.currentMax());
            player.setMaxHealth(maxHealth);
            player.setHealth(Math.max(0D, Math.min(state.currentValue(), maxHealth)));
        }
        return state;
    }

    public ResourceState readResourceState(Player player, String resourceId) {
        if (player == null || resourceId == null || resourceId.isBlank()) {
            return null;
        }
        PdcPartition resourcePartition = resourcePartition(resourceId);
        Double defaultMax = pdcService.get(player, resourcePartition, "default_max", PersistentDataType.DOUBLE);
        Double bonusMax = pdcService.get(player, resourcePartition, "bonus_max", PersistentDataType.DOUBLE);
        Double currentMax = pdcService.get(player, resourcePartition, "current_max", PersistentDataType.DOUBLE);
        Double currentValue = pdcService.get(player, resourcePartition, "current_value", PersistentDataType.DOUBLE);
        String sourceSignature = pdcService.get(player, resourcePartition, "source_signature", PersistentDataType.STRING);
        Integer schemaVersion = pdcService.get(player, resourcePartition, "schema_version", PersistentDataType.INTEGER);
        if (defaultMax == null && bonusMax == null && currentMax == null && currentValue == null && (sourceSignature == null || sourceSignature.isBlank()) && schemaVersion == null) {
            return null;
        }
        return new ResourceState(
            normalizeId(resourceId),
            defaultMax == null ? 0D : defaultMax,
            bonusMax == null ? 0D : bonusMax,
            currentMax == null ? 0D : currentMax,
            currentValue == null ? 0D : currentValue,
            sourceSignature,
            schemaVersion == null ? ResourceState.CURRENT_SCHEMA_VERSION : schemaVersion
        );
    }

    public void setDamageTypeOverride(LivingEntity entity, String damageTypeId) {
        if (entity == null) {
            return;
        }
        if (damageTypeId == null || damageTypeId.isBlank()) {
            pdcService.remove(entity, combatPartition, "damage_type_override");
            return;
        }
        pdcService.set(entity, combatPartition, "damage_type_override", PersistentDataType.STRING, normalizeId(damageTypeId));
    }

    public String peekDamageTypeOverride(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        return pdcService.get(entity, combatPartition, "damage_type_override", PersistentDataType.STRING);
    }

    public String consumeDamageTypeOverride(LivingEntity entity) {
        String override = peekDamageTypeOverride(entity);
        if (override != null && !override.isBlank()) {
            pdcService.remove(entity, combatPartition, "damage_type_override");
        }
        return override;
    }

    public void markSyntheticDamage(LivingEntity entity, boolean value) {
        if (entity == null) {
            return;
        }
        if (value) {
            pdcService.set(entity, combatPartition, "synthetic_damage", PersistentDataType.BYTE, (byte) 1);
        } else {
            pdcService.remove(entity, combatPartition, "synthetic_damage");
        }
    }

    public boolean isSyntheticDamage(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        return pdcService.has(entity, combatPartition, "synthetic_damage", PersistentDataType.BYTE);
    }

    public ProjectileDamageSnapshot snapshotProjectile(Projectile projectile, LivingEntity shooter) {
        if (projectile == null || shooter == null) {
            return null;
        }
        AttributeSnapshot attackSnapshot = collectCombatSnapshot(shooter);
        String damageTypeId = consumeDamageTypeOverride(shooter);
        if (damageTypeId == null || damageTypeId.isBlank()) {
            damageTypeId = defaultProjectileDamageTypeId();
        }
        long now = System.currentTimeMillis();
        String sourceSignature = SignatureUtil.combine(attackSnapshot.sourceSignature(), damageTypeId, projectile.getUniqueId().toString(), shooter.getUniqueId().toString());
        ProjectileDamageSnapshot snapshot = new ProjectileDamageSnapshot(
            ProjectileDamageSnapshot.CURRENT_SCHEMA_VERSION,
            normalizeId(damageTypeId),
            shooter.getUniqueId(),
            sourceSignature,
            now,
            now + PROJECTILE_TTL_MS,
            attackSnapshot
        );
        pdcService.set(projectile, projectilePartition, "schema_version", PersistentDataType.INTEGER, snapshot.schemaVersion());
        pdcService.set(projectile, projectilePartition, "source_signature", PersistentDataType.STRING, snapshot.sourceSignature());
        pdcService.set(projectile, projectilePartition, "damage_type_id", PersistentDataType.STRING, snapshot.damageTypeId());
        pdcService.set(projectile, projectilePartition, "shooter_uuid", PersistentDataType.STRING, snapshot.shooterUuid().toString());
        pdcService.set(projectile, projectilePartition, "launched_at", PersistentDataType.LONG, snapshot.launchedAt());
        pdcService.set(projectile, projectilePartition, "expires_at", PersistentDataType.LONG, snapshot.expiresAt());
        pdcService.writeBlob(projectile, projectilePartition, "attack_snapshot", AttributeSnapshotCodecs.ATTRIBUTE_SNAPSHOT, attackSnapshot);
        pdcService.writeBlob(projectile, projectilePartition, "snapshot", AttributeSnapshotCodecs.PROJECTILE_DAMAGE_SNAPSHOT, snapshot);
        return snapshot;
    }

    public ProjectileDamageSnapshot readProjectileSnapshot(Projectile projectile) {
        if (projectile == null) {
            return null;
        }
        ProjectileDamageSnapshot snapshot = pdcService.readBlob(projectile, projectilePartition, "snapshot", AttributeSnapshotCodecs.PROJECTILE_DAMAGE_SNAPSHOT);
        if (snapshot == null) {
            return null;
        }
        if (snapshot.expiresAt() > 0L && System.currentTimeMillis() > snapshot.expiresAt()) {
            pdcService.remove(projectile, projectilePartition, "schema_version");
            pdcService.remove(projectile, projectilePartition, "source_signature");
            pdcService.remove(projectile, projectilePartition, "damage_type_id");
            pdcService.remove(projectile, projectilePartition, "shooter_uuid");
            pdcService.remove(projectile, projectilePartition, "launched_at");
            pdcService.remove(projectile, projectilePartition, "expires_at");
            pdcService.remove(projectile, projectilePartition, "attack_snapshot");
            pdcService.remove(projectile, projectilePartition, "snapshot");
            return null;
        }
        return snapshot;
    }

    public DamageResult calculateDamage(LivingEntity attacker,
                                        LivingEntity target,
                                        String damageTypeId,
                                        double baseDamage,
                                        Map<String, Object> context) {
        String resolvedTypeId = damageTypeId == null || damageTypeId.isBlank() ? null : normalizeId(damageTypeId);
        if ((resolvedTypeId == null || resolvedTypeId.isBlank()) && attacker != null) {
            resolvedTypeId = consumeDamageTypeOverride(attacker);
        }
        if (resolvedTypeId == null || resolvedTypeId.isBlank()) {
            resolvedTypeId = defaultDamageTypeId();
        }
        DamageTypeDefinition damageType = resolveDamageType(resolvedTypeId);
        AttributeSnapshot attackerSnapshot = attacker == null ? AttributeSnapshot.empty("") : collectCombatSnapshot(attacker);
        AttributeSnapshot targetSnapshot = target == null ? AttributeSnapshot.empty("") : collectCombatSnapshot(target);
        Map<String, Object> normalizedContext = normalizeContext(context);
        DamageRequest request = new DamageRequest(damageType.id(), baseDamage, attackerSnapshot, targetSnapshot, normalizedContext);
        return damageEngine.resolve(request, damageType, ThreadLocalRandom.current().nextDouble(0D, 100D));
    }

    public boolean applyDamage(LivingEntity attacker,
                               LivingEntity target,
                               String damageTypeId,
                               double baseDamage,
                               Map<String, Object> context) {
        if (target == null || !target.isValid() || target.isDead()) {
            return false;
        }
        DamageResult result = calculateDamage(attacker, target, damageTypeId, baseDamage, context);
        EmakiAttributeDamageEvent event = new EmakiAttributeDamageEvent(attacker, target, null, result.damageTypeId(), baseDamage, result);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getFinalDamage() <= 0D) {
            return false;
        }
        applyDirectDamage(target, event.getFinalDamage(), attacker);
        scheduleHealthSync(target);
        return true;
    }

    public boolean applyProjectileDamage(Projectile projectile,
                                         LivingEntity target,
                                         double baseDamage,
                                         Map<String, Object> context) {
        if (projectile == null || target == null) {
            return false;
        }
        ProjectileDamageSnapshot snapshot = readProjectileSnapshot(projectile);
        if (snapshot == null) {
            LivingEntity shooter = projectile.getShooter() instanceof LivingEntity livingEntity ? livingEntity : null;
            return applyDamage(shooter, target, defaultProjectileDamageTypeId(), baseDamage, context);
        }
        AttributeSnapshot attackSnapshot = snapshot.attackSnapshot() == null ? AttributeSnapshot.empty("") : snapshot.attackSnapshot();
        AttributeSnapshot targetSnapshot = collectCombatSnapshot(target);
        DamageTypeDefinition damageType = resolveDamageType(snapshot.damageTypeId());
        Map<String, Object> normalizedContext = normalizeContext(context);
        DamageRequest request = new DamageRequest(damageType.id(), baseDamage, attackSnapshot, targetSnapshot, normalizedContext);
        DamageResult result = damageEngine.resolve(request, damageType, ThreadLocalRandom.current().nextDouble(0D, 100D));
        LivingEntity shooter = projectile.getShooter() instanceof LivingEntity livingEntity ? livingEntity : null;
        EmakiAttributeDamageEvent event = new EmakiAttributeDamageEvent(shooter, target, projectile, result.damageTypeId(), baseDamage, result);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getFinalDamage() <= 0D) {
            return false;
        }
        applyDirectDamage(target, event.getFinalDamage(), projectile);
        scheduleHealthSync(target);
        return true;
    }

    public void clearPlayerDamageTypeOverride(Player player) {
        if (player != null) {
            pdcService.remove(player, combatPartition, "damage_type_override");
        }
    }

    private Map<String, Object> normalizeContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(normalizeId(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private void clearItemCache(ItemStack itemStack) {
        pdcService.remove(itemStack, itemPartition, "schema_version");
        pdcService.remove(itemStack, itemPartition, "source_signature");
        pdcService.remove(itemStack, itemPartition, "snapshot");
    }

    private void mergeValues(Map<String, Double> target, Map<String, Double> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            target.merge(normalizeId(entry.getKey()), entry.getValue(), Double::sum);
        }
    }

    private void syncCombatSnapshot(Player player, AttributeSnapshot snapshot) {
        if (player == null || snapshot == null) {
            return;
        }
        pdcService.set(player, combatPartition, "schema_version", PersistentDataType.INTEGER, snapshot.schemaVersion());
        pdcService.set(player, combatPartition, "source_signature", PersistentDataType.STRING, snapshot.sourceSignature());
        pdcService.writeBlob(player, combatPartition, "snapshot", AttributeSnapshotCodecs.ATTRIBUTE_SNAPSHOT, snapshot);
    }

    private AttributeSnapshot collectLivingCombatSnapshot(LivingEntity entity) {
        Map<String, Double> values = new LinkedHashMap<>();
        List<String> signatureParts = new ArrayList<>();
        mergeValues(values, defaultAttributeValues());
        signatureParts.add("defaults:" + SignatureUtil.stableSignature(defaultProfileRegistry.mergedProfiles()));
        AttributeSnapshot cached = pdcService.readBlob(entity, combatPartition, "snapshot", AttributeSnapshotCodecs.ATTRIBUTE_SNAPSHOT);
        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            List<ItemSlot> slots = List.of(
                new ItemSlot("main_hand", equipment.getItemInMainHand()),
                new ItemSlot("off_hand", equipment.getItemInOffHand()),
                new ItemSlot("helmet", equipment.getHelmet()),
                new ItemSlot("chestplate", equipment.getChestplate()),
                new ItemSlot("leggings", equipment.getLeggings()),
                new ItemSlot("boots", equipment.getBoots())
            );
            for (ItemSlot slot : slots) {
                AttributeSnapshot itemSnapshot = collectItemSnapshot(slot.item());
                if (itemSnapshot == null) {
                    continue;
                }
                mergeValues(values, itemSnapshot.values());
                signatureParts.add(slot.name() + ":" + itemSnapshot.sourceSignature());
            }
        }
        String sourceSignature = SignatureUtil.stableSignature(signatureParts);
        AttributeSnapshot snapshot = new AttributeSnapshot(AttributeSnapshot.CURRENT_SCHEMA_VERSION, sourceSignature, values, System.currentTimeMillis());
        String cachedSignature = pdcService.get(entity, combatPartition, "source_signature", PersistentDataType.STRING);
        if (sourceSignature.equals(cachedSignature) && cached != null) {
            return cached;
        }
        syncCombatSnapshot(entity, snapshot);
        return snapshot;
    }

    private void syncCombatSnapshot(LivingEntity entity, AttributeSnapshot snapshot) {
        if (entity == null || snapshot == null) {
            return;
        }
        pdcService.set(entity, combatPartition, "schema_version", PersistentDataType.INTEGER, snapshot.schemaVersion());
        pdcService.set(entity, combatPartition, "source_signature", PersistentDataType.STRING, snapshot.sourceSignature());
        pdcService.writeBlob(entity, combatPartition, "snapshot", AttributeSnapshotCodecs.ATTRIBUTE_SNAPSHOT, snapshot);
    }

    private void applyDirectDamage(LivingEntity target, double damage, Entity visualSource) {
        if (target == null || damage <= 0D || !target.isValid() || target.isDead()) {
            return;
        }
        // Direct health writes keep the final result independent from vanilla armor and damage causes.
        double currentHealth = Math.max(0D, target.getHealth());
        double nextHealth = Math.max(0D, currentHealth - damage);
        target.setHealth(nextHealth);
        if (nextHealth > 0D) {
            float yaw = 0F;
            if (visualSource != null) {
                yaw = visualSource.getLocation().getYaw();
            }
            target.playHurtAnimation(yaw);
        }
    }

    private PdcPartition resourcePartition(String resourceId) {
        return pdcService.partition("resource." + normalizeId(resourceId));
    }

    private record ItemSlot(String name, ItemStack item) {
    }
}
