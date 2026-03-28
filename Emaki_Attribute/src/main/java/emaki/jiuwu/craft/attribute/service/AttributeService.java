package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeDamageEvent;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.loader.AttributeBalanceRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshotCodecs;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageStageSource;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageRequest;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.attribute.model.DefaultProfile;
import emaki.jiuwu.craft.attribute.model.ProjectileDamageSnapshot;
import emaki.jiuwu.craft.attribute.model.RecoveryDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
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
import org.bukkit.event.entity.EntityDamageEvent;
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
    private static final double DEFAULT_WALK_SPEED_BLOCKS_PER_SECOND = 4.317D;
    private static final int DEFAULT_ATTACK_COOLDOWN_TICKS = 20;

    private final EmakiAttributePlugin plugin;
    private final PdcService pdcService;
    private volatile AttributeConfig config;
    private final AttributeRegistry attributeRegistry;
    private final AttributeBalanceRegistry attributeBalanceRegistry;
    private final DamageTypeRegistry damageTypeRegistry;
    private final DefaultProfileRegistry defaultProfileRegistry;
    private final LoreFormatRegistry loreFormatRegistry;
    private final AttributePresetRegistry presetRegistry;
    private final LoreParser loreParser;
    private final DamageEngine damageEngine;
    private final Map<String, AttributeContributionProvider> contributionProviders = new LinkedHashMap<>();
    private volatile List<AttributeDefinition> attributeDefinitions = List.of();
    private volatile List<DefaultProfile> defaultProfiles = List.of();
    private volatile Map<String, ResourceDefinition> resourceDefinitions = Map.of();
    private volatile Map<String, Double> defaultAttributeValues = Map.of();
    private volatile Map<String, List<AttributeDefinition>> resourceAttributeDefinitions = Map.of();
    private volatile Map<String, List<AttributeDefinition>> resourceRegenDefinitions = Map.of();
    private volatile List<AttributeDefinition> genericSpeedDefinitions = List.of();
    private volatile List<AttributeDefinition> genericAttackSpeedDefinitions = List.of();

    private final PdcPartition itemPartition;
    private final PdcPartition combatPartition;
    private final PdcPartition projectilePartition;

    public AttributeService(EmakiAttributePlugin plugin,
                            PdcService pdcService,
                            AttributeConfig config,
                            AttributeRegistry attributeRegistry,
                            AttributeBalanceRegistry attributeBalanceRegistry,
                            DamageTypeRegistry damageTypeRegistry,
                            DefaultProfileRegistry defaultProfileRegistry,
                            LoreFormatRegistry loreFormatRegistry,
                            AttributePresetRegistry presetRegistry) {
        this.plugin = plugin;
        this.pdcService = pdcService;
        this.config = config;
        this.attributeRegistry = attributeRegistry;
        this.attributeBalanceRegistry = attributeBalanceRegistry;
        this.damageTypeRegistry = damageTypeRegistry;
        this.defaultProfileRegistry = defaultProfileRegistry;
        this.loreFormatRegistry = loreFormatRegistry;
        this.presetRegistry = presetRegistry;
        this.loreParser = new LoreParser(attributeRegistry, loreFormatRegistry);
        this.damageEngine = new DamageEngine();
        this.itemPartition = pdcService.partition("item");
        this.combatPartition = pdcService.partition("combat");
        this.projectilePartition = pdcService.partition("projectile");
        refreshCaches();
    }

    public AttributeConfig config() {
        return config;
    }

    public AttributeRegistry attributeRegistry() {
        return attributeRegistry;
    }

    public AttributeBalanceRegistry attributeBalanceRegistry() {
        return attributeBalanceRegistry;
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

    public void refreshCaches() {
        List<AttributeDefinition> definitions = attributeRegistry == null
            ? List.of()
            : List.copyOf(attributeRegistry.all().values());
        List<DefaultProfile> profiles = defaultProfileRegistry == null
            ? List.of()
            : defaultProfileRegistry.mergedProfiles();
        Map<String, List<AttributeDefinition>> resourceBuckets = new LinkedHashMap<>();
        Map<String, List<AttributeDefinition>> resourceRegenBuckets = new LinkedHashMap<>();
        List<AttributeDefinition> speedDefinitions = new ArrayList<>();
        List<AttributeDefinition> attackSpeedDefinitions = new ArrayList<>();
        for (AttributeDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            String targetId = normalizeId(definition.targetId());
            if (definition.targetType() == emaki.jiuwu.craft.attribute.model.AttributeTargetType.RESOURCE && !targetId.isBlank()) {
                resourceBuckets.computeIfAbsent(targetId, key -> new ArrayList<>()).add(definition);
                if (definition.valueKind() == AttributeValueKind.REGEN) {
                    resourceRegenBuckets.computeIfAbsent(targetId, key -> new ArrayList<>()).add(definition);
                }
            }
            if (definition.targetType() == emaki.jiuwu.craft.attribute.model.AttributeTargetType.GENERIC) {
                if ("speed".equals(targetId) || "movement_speed".equals(targetId)) {
                    speedDefinitions.add(definition);
                }
                if ("attack_speed".equals(targetId)) {
                    attackSpeedDefinitions.add(definition);
                }
            }
        }
        attributeDefinitions = definitions;
        defaultProfiles = profiles;
        resourceDefinitions = buildResourceDefinitions(profiles);
        defaultAttributeValues = buildDefaultAttributeValues(profiles, definitions);
        resourceAttributeDefinitions = freezeDefinitionBuckets(resourceBuckets);
        resourceRegenDefinitions = freezeDefinitionBuckets(resourceRegenBuckets);
        genericSpeedDefinitions = List.copyOf(speedDefinitions);
        genericAttackSpeedDefinitions = List.copyOf(attackSpeedDefinitions);
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
        return SignatureUtil.combine(
            SignatureUtil.stableSignature(defaultProfiles),
            SignatureUtil.stableSignature(attributeDefinitions)
        );
    }

    public Map<String, ResourceDefinition> resourceDefinitions() {
        return resourceDefinitions;
    }

    public Map<String, Double> defaultAttributeValues() {
        return defaultAttributeValues;
    }

    private Map<String, ResourceDefinition> buildResourceDefinitions(List<DefaultProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return Map.of();
        }
        Map<String, ResourceDefinition> result = new LinkedHashMap<>();
        for (DefaultProfile profile : profiles) {
            if (profile == null || profile.resources() == null || profile.resources().isEmpty()) {
                continue;
            }
            for (ResourceDefinition definition : profile.resources().values()) {
                if (definition == null || Texts.isBlank(definition.id())) {
                    continue;
                }
                result.putIfAbsent(definition.id(), definition);
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private Map<String, Double> buildDefaultAttributeValues(List<DefaultProfile> profiles, List<AttributeDefinition> definitions) {
        if (profiles == null || profiles.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (DefaultProfile profile : profiles) {
            if (profile == null || profile.attributeDefaults() == null || profile.attributeDefaults().isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Double> entry : profile.attributeDefaults().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                result.merge(normalizeId(entry.getKey()), entry.getValue(), Double::sum);
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private Map<String, List<AttributeDefinition>> freezeDefinitionBuckets(Map<String, List<AttributeDefinition>> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return Map.of();
        }
        Map<String, List<AttributeDefinition>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, List<AttributeDefinition>> entry : buckets.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return frozen.isEmpty() ? Map.of() : Map.copyOf(frozen);
    }

    public Double resolveAttributeValue(AttributeSnapshot snapshot, String attributeId) {
        if (snapshot == null || attributeId == null || attributeId.isBlank()) {
            return null;
        }
        String normalized = normalizeId(attributeId);
        AttributeDefinition definition = attributeRegistry.resolve(attributeId);
        if (definition != null) {
            Double value = snapshot.values().get(definition.id());
            if (value != null) {
                return value;
            }
        }
        Double value = snapshot.values().get(normalized);
        if (value != null) {
            return value;
        }
        return definition == null ? null : snapshot.values().get(definition.id());
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
        String sourceSignature = SignatureUtil.combine(
            SignatureUtil.stableSignature(normalizedLore),
            SignatureUtil.stableSignature(attributeDefinitions)
        );
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
        signatureParts.add("defaults:" + SignatureUtil.stableSignature(defaultProfiles));
        signatureParts.add("attributes:" + SignatureUtil.stableSignature(attributeDefinitions));
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
        mergeContributionProviders(player, values, signatureParts);
        applyDerivedValues(values);
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
                for (AttributeDefinition definition : resourceRegenDefinitions.getOrDefault(resourceDefinition.id(), List.of())) {
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

    public void scheduleLivingEntitySync(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        java.util.UUID entityId = entity.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(
            plugin,
            () -> {
                Entity current = Bukkit.getEntity(entityId);
                if (current instanceof LivingEntity livingEntity && livingEntity.isValid() && !livingEntity.isDead()) {
                    syncLivingEntity(livingEntity);
                }
            },
            Math.max(1, config.syncDelayTicks())
        );
    }

    public void syncLivingEntity(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return;
        }
        if (entity instanceof Player player) {
            syncPlayer(player, ResourceSyncReason.EQUIPMENT, null);
            return;
        }
        collectCombatSnapshot(entity);
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
        syncMovementSpeed(player, snapshot);
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
        for (AttributeDefinition definition : resourceAttributeDefinitions.getOrDefault(resourceDefinition.id(), List.of())) {
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
            syncHealthToBukkit(player, state);
        }
        return state;
    }

    private void syncHealthToBukkit(Player player, ResourceState state) {
        if (player == null || state == null) {
            return;
        }
        double rawMaxHealth = state.currentMax();
        double maxHealth = Math.max(1D, rawMaxHealth);
        // Health can still be tracked as 0 in the attribute layer, but Bukkit must never
        // receive a zero-or-negative max health or the player can get stuck in a dead state.
        double bukkitHealth = rawMaxHealth <= 0D
            ? 1D
            : Math.max(0D, Math.min(state.currentValue(), maxHealth));
        player.setMaxHealth(maxHealth);
        player.setHealth(Math.min(maxHealth, bukkitHealth));
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

    public DamageContext createDamageContext(LivingEntity attacker,
                                             LivingEntity target,
                                             Projectile projectile,
                                             EntityDamageEvent.DamageCause cause,
                                             String damageTypeId,
                                             double sourceDamage,
                                             double baseDamage,
                                             AttributeSnapshot attackerSnapshot,
                                             AttributeSnapshot targetSnapshot,
                                             DamageContextVariables context) {
        DamageTypeDefinition damageType = resolveDamageType(damageTypeId);
        AttributeSnapshot resolvedAttacker = attackerSnapshot;
        if (resolvedAttacker == null) {
            resolvedAttacker = attacker == null ? AttributeSnapshot.empty("") : collectCombatSnapshot(attacker);
        }
        AttributeSnapshot resolvedTarget = targetSnapshot;
        if (resolvedTarget == null) {
            resolvedTarget = target == null ? AttributeSnapshot.empty("") : collectCombatSnapshot(target);
        }
        DamageContextVariables.Builder merged = DamageContextVariables.builder();
        merged.putAll(context);
        String attackerLabel = entityLabel(attacker, cause, messageOrFallback("damage.environment", "环境"));
        String targetLabel = entityLabel(target, null, messageOrFallback("damage.target", "目标"));
        merged.put("attacker", attackerLabel);
        merged.put("attacker_name", attackerLabel);
        merged.put("attacker_type", attacker == null ? "" : attacker.getType().name());
        merged.put("attacker_uuid", attacker == null ? "" : attacker.getUniqueId().toString());
        merged.put("source", attackerLabel);
        merged.put("source_name", attackerLabel);
        merged.put("target", targetLabel);
        merged.put("target_name", targetLabel);
        merged.put("target_type", target == null ? "" : target.getType().name());
        merged.put("target_uuid", target == null ? "" : target.getUniqueId().toString());
        merged.put("damage_type", damageType.displayName());
        merged.put("damage_type_name", damageType.displayName());
        merged.put("damage_type_id", damageType.id());
        merged.put("source_damage", sourceDamage);
        merged.put("input_damage", sourceDamage);
        merged.put("base_damage", baseDamage);
        merged.put("damage", baseDamage);
        merged.put("cause", cause == null ? "" : cause.name());
        merged.put("cause_name", causeDisplayName(cause));
        merged.put("cause_id", cause == null ? "" : normalizeId(cause.name()));
        merged.put("damage_cause", cause == null ? "" : cause.name());
        merged.put("damage_cause_name", causeDisplayName(cause));
        merged.put("damage_cause_id", cause == null ? "" : normalizeId(cause.name()));
        if (projectile != null) {
            merged.put("projectile_type", projectile.getType().name());
            merged.put("projectile_uuid", projectile.getUniqueId().toString());
        }
        return DamageContext.of(attacker, target, projectile, cause, damageType.id(), sourceDamage, baseDamage, resolvedAttacker, resolvedTarget, merged.build());
    }

    public DamageContext createDamageContext(LivingEntity attacker,
                                             LivingEntity target,
                                             Projectile projectile,
                                             EntityDamageEvent.DamageCause cause,
                                             String damageTypeId,
                                             double sourceDamage,
                                             double baseDamage,
                                             Map<String, ?> context) {
        return createDamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, null, null, DamageContextVariables.from(context));
    }

    public DamageContext createDamageContext(LivingEntity attacker,
                                             LivingEntity target,
                                             Projectile projectile,
                                             EntityDamageEvent.DamageCause cause,
                                             String damageTypeId,
                                             double sourceDamage,
                                             double baseDamage,
                                             DamageContextVariables context) {
        return createDamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, null, null, context);
    }

    public DamageResult calculateDamage(DamageContext damageContext) {
        if (damageContext == null) {
            return new DamageResult("", 0D, false, 0D, Map.of(), DamageContext.legacy("", 0D, null, null, DamageContextVariables.empty()));
        }
        String resolvedTypeId = damageContext.damageTypeId();
        if (Texts.isBlank(resolvedTypeId)) {
            resolvedTypeId = defaultDamageTypeId();
        }
        if (damageContext.attacker() instanceof Player player) {
            String override = consumeDamageTypeOverride(player);
            if (!Texts.isBlank(override) && Texts.isBlank(damageContext.damageTypeId())) {
                resolvedTypeId = override;
            }
        }
        DamageTypeDefinition damageType = resolveDamageType(resolvedTypeId);
        DamageContext resolvedContext = damageContext.withDamageTypeId(damageType.id());
        DamageRequest request = new DamageRequest(resolvedContext);
        return damageEngine.resolve(request, damageType, ThreadLocalRandom.current().nextDouble(0D, 100D));
    }

    public DamageResult calculateDamage(LivingEntity attacker,
                                        LivingEntity target,
                                        String damageTypeId,
                                        double baseDamage,
                                        DamageContextVariables context) {
        double sourceDamage = contextDouble(context, "source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = extractDamageCause(context);
        DamageContext damageContext = createDamageContext(attacker, target, null, cause, damageTypeId, sourceDamage, baseDamage, context);
        return calculateDamage(damageContext);
    }

    public DamageResult calculateDamage(LivingEntity attacker,
                                        LivingEntity target,
                                        String damageTypeId,
                                        double baseDamage,
                                  Map<String, ?> context) {
        return calculateDamage(attacker, target, damageTypeId, baseDamage, DamageContextVariables.from(context));
    }

    public boolean applyDamage(DamageContext damageContext) {
        if (damageContext == null || damageContext.target() == null || !damageContext.target().isValid() || damageContext.target().isDead()) {
            return false;
        }
        if (damageContext.projectile() != null) {
            return applyProjectileDamage(damageContext);
        }
        DamageResult result = calculateDamage(damageContext);
        EmakiAttributeDamageEvent event = new EmakiAttributeDamageEvent(damageContext, result);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getFinalDamage() <= 0D) {
            return false;
        }
        double finalDamage = event.getFinalDamage();
        applyDirectDamage(damageContext.target(), finalDamage, damageContext.attacker());
        if (damageContext.attacker() instanceof Player player) {
            startAttackCooldown(player, damageContext.attackerSnapshot(), player.getInventory().getItemInMainHand());
        }
        DamageTypeDefinition damageType = resolveDamageType(result.damageTypeId());
        applyRecovery(damageContext, damageType, result, finalDamage);
        notifyDamageMessages(damageContext, damageType, result, finalDamage);
        scheduleHealthSync(damageContext.target());
        return true;
    }

    public boolean applyDamage(LivingEntity attacker,
                               LivingEntity target,
                               String damageTypeId,
                               double baseDamage,
                               DamageContextVariables context) {
        double sourceDamage = contextDouble(context, "source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = extractDamageCause(context);
        DamageContext damageContext = createDamageContext(attacker, target, null, cause, damageTypeId, sourceDamage, baseDamage, context);
        return applyDamage(damageContext);
    }

    public boolean applyDamage(LivingEntity attacker,
                               LivingEntity target,
                               String damageTypeId,
                               double baseDamage,
                               Map<String, ?> context) {
        return applyDamage(attacker, target, damageTypeId, baseDamage, DamageContextVariables.from(context));
    }

    public boolean applyProjectileDamage(DamageContext damageContext) {
        if (damageContext == null || damageContext.projectile() == null || damageContext.target() == null) {
            return false;
        }
        Projectile projectile = damageContext.projectile();
        ProjectileDamageSnapshot snapshot = readProjectileSnapshot(projectile);
        if (snapshot == null) {
            LivingEntity shooter = projectile.getShooter() instanceof LivingEntity livingEntity ? livingEntity : damageContext.attacker();
            return applyDamage(shooter, damageContext.target(), defaultProjectileDamageTypeId(), damageContext.baseDamage(), damageContext.variables());
        }
        AttributeSnapshot attackSnapshot = snapshot.attackSnapshot() == null ? AttributeSnapshot.empty("") : snapshot.attackSnapshot();
        AttributeSnapshot targetSnapshot = collectCombatSnapshot(damageContext.target());
        LivingEntity shooter = projectile.getShooter() instanceof LivingEntity livingEntity ? livingEntity : damageContext.attacker();
        DamageContext resolvedContext = createDamageContext(
            shooter,
            damageContext.target(),
            projectile,
            damageContext.cause(),
            snapshot.damageTypeId(),
            damageContext.sourceDamage(),
            damageContext.baseDamage(),
            attackSnapshot,
            targetSnapshot,
            damageContext.variables()
        );
        DamageResult result = calculateDamage(resolvedContext);
        DamageTypeDefinition damageType = resolveDamageType(result.damageTypeId());
        EmakiAttributeDamageEvent event = new EmakiAttributeDamageEvent(resolvedContext, result);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getFinalDamage() <= 0D) {
            return false;
        }
        double finalDamage = event.getFinalDamage();
        applyDirectDamage(damageContext.target(), finalDamage, projectile);
        applyRecovery(resolvedContext, damageType, result, finalDamage);
        notifyDamageMessages(resolvedContext, damageType, result, finalDamage);
        scheduleHealthSync(damageContext.target());
        return true;
    }

    public boolean applyProjectileDamage(Projectile projectile,
                                         LivingEntity target,
                                         double baseDamage,
                                         DamageContextVariables context) {
        if (projectile == null || target == null) {
            return false;
        }
        LivingEntity shooter = projectile.getShooter() instanceof LivingEntity livingEntity ? livingEntity : null;
        double sourceDamage = contextDouble(context, "source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = extractDamageCause(context);
        DamageContext damageContext = createDamageContext(shooter, target, projectile, cause, defaultProjectileDamageTypeId(), sourceDamage, baseDamage, context);
        return applyProjectileDamage(damageContext);
    }

    public boolean applyProjectileDamage(Projectile projectile,
                                         LivingEntity target,
                                         double baseDamage,
                                         Map<String, ?> context) {
        return applyProjectileDamage(projectile, target, baseDamage, DamageContextVariables.from(context));
    }

    public void clearPlayerDamageTypeOverride(Player player) {
        if (player != null) {
            pdcService.remove(player, combatPartition, "damage_type_override");
        }
    }

    public boolean isAttackCoolingDown(Player player) {
        if (player == null) {
            return false;
        }
        Long until = pdcService.get(player, combatPartition, "attack_cooldown_until", PersistentDataType.LONG);
        if (until == null || until <= 0L) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now >= until) {
            pdcService.remove(player, combatPartition, "attack_cooldown_until");
            return false;
        }
        return true;
    }

    public int startAttackCooldown(Player player, AttributeSnapshot snapshot, ItemStack itemStack) {
        if (player == null) {
            return 0;
        }
        int cooldownTicks = resolveAttackCooldownTicks(snapshot);
        if (cooldownTicks <= 0) {
            pdcService.remove(player, combatPartition, "attack_cooldown_until");
            return 0;
        }
        long until = System.currentTimeMillis() + (cooldownTicks * 50L);
        pdcService.set(player, combatPartition, "attack_cooldown_until", PersistentDataType.LONG, until);
        ItemStack held = itemStack == null ? player.getInventory().getItemInMainHand() : itemStack;
        if (held != null && !held.getType().isAir()) {
            player.setCooldown(held.getType(), cooldownTicks);
        }
        return cooldownTicks;
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

    private void mergeContributionProviders(LivingEntity entity,
                                            Map<String, Double> target,
                                            List<String> signatureParts) {
        if (entity == null) {
            return;
        }
        List<AttributeContributionProvider> providers = new ArrayList<>(contributionProviders.values());
        providers.sort(Comparator.comparingInt(AttributeContributionProvider::priority).reversed());
        for (AttributeContributionProvider provider : providers) {
            Collection<AttributeContribution> contributions = provider.collect(entity);
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
                target.merge(id, contribution.value(), Double::sum);
            }
            if (!providerValues.isEmpty()) {
                signatureParts.add(normalizeId(provider.id()) + ":" + SignatureUtil.stableSignature(providerValues));
            }
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
        signatureParts.add("defaults:" + SignatureUtil.stableSignature(defaultProfiles));
        signatureParts.add("attributes:" + SignatureUtil.stableSignature(attributeDefinitions));
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
        mergeContributionProviders(entity, values, signatureParts);
        applyDerivedValues(values);
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

    private void applyRecovery(DamageContext damageContext,
                               DamageTypeDefinition damageType,
                               DamageResult result,
                               double finalDamage) {
        if (damageContext == null || damageType == null || !damageType.hasRecovery() || result == null) {
            return;
        }
        LivingEntity attacker = damageContext.attacker();
        if (attacker == null || !attacker.isValid() || attacker.isDead()) {
            return;
        }
        double recoveryAmount = resolveRecoveryAmount(damageContext, damageType.recovery(), finalDamage);
        if (recoveryAmount <= 0D) {
            return;
        }
        double currentHealth = Math.max(0D, attacker.getHealth());
        double maxHealth = Math.max(1D, attacker.getMaxHealth());
        attacker.setHealth(Math.min(maxHealth, currentHealth + recoveryAmount));
        scheduleHealthSync(attacker);
    }

    private void notifyDamageMessages(DamageContext damageContext,
                                      DamageTypeDefinition damageType,
                                      DamageResult result,
                                      double finalDamage) {
        if (damageContext == null || damageType == null || result == null) {
            return;
        }
        Map<String, Object> replacements = buildDamageMessageReplacements(damageContext, damageType, result, finalDamage);
        Player attackerPlayer = damageContext.attacker() instanceof Player player ? player : null;
        Player targetPlayer = damageContext.target() instanceof Player player ? player : null;
        if (attackerPlayer != null && targetPlayer != null && attackerPlayer.getUniqueId().equals(targetPlayer.getUniqueId())) {
            sendDamageMessage(attackerPlayer, firstNonBlank(damageType.attackerMessage(), damageType.targetMessage()), replacements);
            return;
        }
        sendDamageMessage(attackerPlayer, damageType.attackerMessage(), replacements);
        sendDamageMessage(targetPlayer, damageType.targetMessage(), replacements);
    }

    private void sendDamageMessage(Player player, String template, Map<String, Object> replacements) {
        if (player == null || Texts.isBlank(template)) {
            return;
        }
        String rendered = Texts.formatTemplate(template, replacements);
        if (Texts.isBlank(rendered)) {
            return;
        }
        player.sendMessage(MiniMessages.parse(rendered));
    }

    private Map<String, Object> buildDamageMessageReplacements(DamageContext damageContext,
                                                               DamageTypeDefinition damageType,
                                                               DamageResult result,
                                                               double finalDamage) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        String attackerLabel = entityLabel(damageContext.attacker(), damageContext.cause(), messageOrFallback("damage.environment", "环境"));
        String targetLabel = entityLabel(damageContext.target(), null, messageOrFallback("damage.target", "目标"));
        String damageTypeLabel = Texts.isBlank(damageType.displayName()) ? damageType.id() : damageType.displayName();
        String sourceDamageText = Numbers.formatNumber(damageContext.sourceDamage(), "0.##");
        String baseDamageText = Numbers.formatNumber(damageContext.baseDamage(), "0.##");
        String finalDamageText = Numbers.formatNumber(finalDamage, "0.##");
        String causeName = causeDisplayName(damageContext.cause());
        replacements.put("attacker", attackerLabel);
        replacements.put("attacker_name", attackerLabel);
        replacements.put("attacker_type", damageContext.attacker() == null ? causeName : damageContext.attacker().getType().name());
        replacements.put("attacker_uuid", damageContext.attacker() == null ? "" : damageContext.attacker().getUniqueId().toString());
        replacements.put("source", attackerLabel);
        replacements.put("source_name", attackerLabel);
        replacements.put("source_type", damageContext.attacker() == null ? causeName : damageContext.attacker().getType().name());
        replacements.put("source_uuid", damageContext.attacker() == null ? "" : damageContext.attacker().getUniqueId().toString());
        replacements.put("target", targetLabel);
        replacements.put("target_name", targetLabel);
        replacements.put("target_type", damageContext.target() == null ? "" : damageContext.target().getType().name());
        replacements.put("target_uuid", damageContext.target() == null ? "" : damageContext.target().getUniqueId().toString());
        replacements.put("damage_type", damageTypeLabel);
        replacements.put("damage_type_name", damageTypeLabel);
        replacements.put("damage_type_id", damageType.id());
        replacements.put("source_damage", sourceDamageText);
        replacements.put("input_damage", sourceDamageText);
        replacements.put("base_damage", baseDamageText);
        replacements.put("final_damage", finalDamageText);
        replacements.put("damage", finalDamageText);
        replacements.put("cause", damageContext.causeName());
        replacements.put("cause_name", causeName);
        replacements.put("cause_id", damageContext.causeId());
        replacements.put("damage_cause", damageContext.causeName());
        replacements.put("damage_cause_name", causeName);
        replacements.put("damage_cause_id", damageContext.causeId());
        replacements.put("critical", result.critical());
        replacements.put("critical_text", result.critical() ? messageOrFallback("damage.critical_text", "暴击") : "");
        replacements.put("critical_suffix", result.critical() ? messageOrFallback("damage.critical_suffix", " <red>暴击</red>") : "");
        replacements.put("roll", Numbers.formatNumber(result.roll(), "0.##"));
        return replacements;
    }

    private String entityLabel(LivingEntity entity, EntityDamageEvent.DamageCause cause, String fallback) {
        if (entity == null) {
            if (cause != null) {
                return causeDisplayName(cause);
            }
            return fallback;
        }
        String name = Texts.toStringSafe(entity.getName()).trim();
        if (Texts.isBlank(name)) {
            name = entity.getType().name();
        }
        return Texts.isBlank(name) ? fallback : name;
    }

    private String causeDisplayName(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return messageOrFallback("damage.cause.environment", "环境");
        }
        return switch (cause) {
            case CONTACT -> messageOrFallback("damage.cause.contact", "接触");
            case ENTITY_ATTACK -> messageOrFallback("damage.cause.entity_attack", "攻击");
            case PROJECTILE -> messageOrFallback("damage.cause.projectile", "弹射物");
            case SUFFOCATION -> messageOrFallback("damage.cause.suffocation", "窒息");
            case FALL -> messageOrFallback("damage.cause.fall", "摔落");
            case FIRE -> messageOrFallback("damage.cause.fire", "火焰");
            case FIRE_TICK -> messageOrFallback("damage.cause.fire_tick", "燃烧");
            case LAVA -> messageOrFallback("damage.cause.lava", "岩浆");
            case DROWNING -> messageOrFallback("damage.cause.drowning", "溺水");
            case BLOCK_EXPLOSION -> messageOrFallback("damage.cause.block_explosion", "方块爆炸");
            case ENTITY_EXPLOSION -> messageOrFallback("damage.cause.entity_explosion", "爆炸");
            case VOID -> messageOrFallback("damage.cause.void", "虚空");
            case LIGHTNING -> messageOrFallback("damage.cause.lightning", "雷击");
            case STARVATION -> messageOrFallback("damage.cause.starvation", "饥饿");
            case POISON -> messageOrFallback("damage.cause.poison", "中毒");
            case MAGIC -> messageOrFallback("damage.cause.magic", "魔法");
            case WITHER -> messageOrFallback("damage.cause.wither", "凋零");
            case FALLING_BLOCK -> messageOrFallback("damage.cause.falling_block", "落块");
            case DRAGON_BREATH -> messageOrFallback("damage.cause.dragon_breath", "龙息");
            case FLY_INTO_WALL -> messageOrFallback("damage.cause.fly_into_wall", "碰撞");
            case HOT_FLOOR -> messageOrFallback("damage.cause.hot_floor", "高温");
            case CAMPFIRE -> messageOrFallback("damage.cause.campfire", "营火");
            case CRAMMING -> messageOrFallback("damage.cause.cramming", "挤压");
            case FREEZE -> messageOrFallback("damage.cause.freeze", "冻结");
            case SONIC_BOOM -> messageOrFallback("damage.cause.sonic_boom", "音爆");
            default -> messageOrFallback("damage.cause.unknown", cause.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        };
    }

    private EntityDamageEvent.DamageCause extractDamageCause(DamageContextVariables context) {
        if (context == null || context.isEmpty()) {
            return null;
        }
        Object raw = context.get("cause");
        if (raw == null) {
            raw = context.get("damage_cause");
        }
        if (raw == null) {
            raw = context.get("damage_cause_id");
        }
        if (raw == null) {
            return null;
        }
        if (raw instanceof EntityDamageEvent.DamageCause cause) {
            return cause;
        }
        String normalized = normalizeId(String.valueOf(raw));
        if (Texts.isBlank(normalized)) {
            return null;
        }
        try {
            return EntityDamageEvent.DamageCause.valueOf(normalized.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return  null;
        }
    }

    private EntityDamageEvent.DamageCause extractDamageCause(Map<String, ?> context) {
        return extractDamageCause(DamageContextVariables.from(context));
    }

    private double contextDouble(DamageContextVariables context, String key, double fallback) {
        if (context == null || context.isEmpty() || Texts.isBlank(key)) {
            return fallback;
        }
        Double value = Numbers.tryParseDouble(context.get(normalizeId(key)), null);
        return value == null ? fallback : value;
    }

    private double contextDouble(Map<String, ?> context, String key, double fallback) {
        return contextDouble(DamageContextVariables.from(context), key, fallback);
    }

    private String firstNonBlank(String left, String right) {
        return Texts.isBlank(left) ? right : left;
    }

    private void applyDerivedValues(Map<String, Double> values) {
        if (values == null) {
            return;
        }
        values.put("attribute_power", computeAttributePower(values));
    }

    private double computeAttributePower(Map<String, Double> values) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (AttributeDefinition definition : attributeDefinitions) {
            if (definition == null || "attribute_power".equals(definition.id())) {
                continue;
            }
            Double value = values.get(definition.id());
            if (value == null) {
                continue;
            }
            double weight = attributeBalanceRegistry == null
                ? definition.attributePower()
                : attributeBalanceRegistry.weightOf(definition.id(), definition.attributePower());
            total += value * weight;
        }
        return Math.max(0D, total);
    }

    private void syncMovementSpeed(Player player, AttributeSnapshot snapshot) {
        if (player == null) {
            return;
        }
        double flatSpeed = 0D;
        double percentSpeed = 0D;
        for (AttributeDefinition definition : genericSpeedDefinitions) {
            Double value = snapshot == null ? null : snapshot.values().get(definition.id());
            if (value == null) {
                continue;
            }
            if (definition.valueKind() == AttributeValueKind.PERCENT) {
                percentSpeed += value;
            } else if (definition.valueKind() != AttributeValueKind.CHANCE
                && definition.valueKind() != AttributeValueKind.REGEN
                && definition.valueKind() != AttributeValueKind.RESOURCE
                && definition.valueKind() != AttributeValueKind.SKILL
                && definition.valueKind() != AttributeValueKind.DERIVED) {
                flatSpeed += value;
            }
        }
        double blocksPerSecond = Math.max(0D, DEFAULT_WALK_SPEED_BLOCKS_PER_SECOND + flatSpeed);
        blocksPerSecond *= Math.max(0D, 1D + (percentSpeed / 100D));
        float walkSpeed = (float) Math.max(0D, Math.min(1D, (blocksPerSecond / DEFAULT_WALK_SPEED_BLOCKS_PER_SECOND) * 0.2D));
        player.setWalkSpeed(walkSpeed);
    }

    private int resolveAttackCooldownTicks(AttributeSnapshot snapshot) {
        double flatReduction = 0D;
        double percentReduction = 0D;
        for (AttributeDefinition definition : genericAttackSpeedDefinitions) {
            Double value = snapshot == null ? null : snapshot.values().get(definition.id());
            if (value == null) {
                continue;
            }
            if (definition.valueKind() == AttributeValueKind.PERCENT) {
                percentReduction += value;
            } else if (definition.valueKind() != AttributeValueKind.CHANCE
                && definition.valueKind() != AttributeValueKind.REGEN
                && definition.valueKind() != AttributeValueKind.RESOURCE
                && definition.valueKind() != AttributeValueKind.SKILL
                && definition.valueKind() != AttributeValueKind.DERIVED) {
                flatReduction += value;
            }
        }
        double cooldown = Math.max(0D, DEFAULT_ATTACK_COOLDOWN_TICKS - flatReduction);
        cooldown *= Math.max(0D, 1D - (percentReduction / 100D));
        return Math.max(1, (int) Math.round(cooldown));
    }

    private double resolveRecoveryAmount(DamageContext damageContext, RecoveryDefinition recovery, double finalDamage) {
        if (damageContext == null || recovery == null) {
            return 0D;
        }
        DamageContextVariables.Builder context = damageContext.variables().toBuilder();
        AttributeSnapshot sourceSnapshot = snapshotForRecovery(damageContext, recovery.source());
        AttributeSnapshot resistanceSnapshot = snapshotForRecovery(damageContext, recovery.resistanceSource());
        Map<String, Object> evaluationContext = context.build().asMap();
        double flat = sumAttributes(sourceSnapshot, evaluationContext, recovery.flatAttributes());
        double percent = sumAttributes(sourceSnapshot, evaluationContext, recovery.percentAttributes());
        double resistance = sumAttributes(resistanceSnapshot, evaluationContext, recovery.resistanceAttributes());
        double percentAmount = finalDamage * (percent / 100D);
        double grossRecovery = flat + percentAmount;
        context.put("input", finalDamage);
        context.put("base", finalDamage);
        context.put("damage", finalDamage);
        context.put("final_damage", finalDamage);
        context.put("flat", flat);
        context.put("percent", percent);
        context.put("percent_amount", percentAmount);
        context.put("gross", grossRecovery);
        context.put("resistance", resistance);
        context.put("healing_flat", flat);
        context.put("healing_percent", percent);
        context.put("healing_percent_amount", percentAmount);
        context.put("healing_gross", grossRecovery);
        context.put("healing_resistance", resistance);
        evaluationContext = context.build().asMap();
        double value;
        if (Texts.isBlank(recovery.expression())) {
            value = grossRecovery * (1D - (resistance / 100D));
        } else {
            value = ExpressionEngine.evaluate(recovery.expression(), evaluationContext);
        }
        if (recovery.minResult() != null) {
            value = Math.max(value, recovery.minResult());
        }
        if (recovery.maxResult() != null) {
            value = Math.min(value, recovery.maxResult());
        }
        return Math.max(0D, value);
    }

    private AttributeSnapshot snapshotForRecovery(DamageContext damageContext, DamageStageSource source) {
        if (damageContext == null || source == null) {
            return null;
        }
        return switch (source) {
            case ATTACKER -> damageContext.attackerSnapshot();
            case TARGET -> damageContext.targetSnapshot();
            case CONTEXT -> null;
        };
    }

    private double sumAttributes(AttributeSnapshot snapshot, Map<String, ?> context, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (String id : ids) {
            if (Texts.isBlank(id)) {
                continue;
            }
            String normalized = normalizeId(id);
            Double value = snapshot == null ? null : snapshot.values().get(normalized);
            if (value == null && context != null) {
                Object raw = context.get(normalized);
                value = Numbers.tryParseDouble(raw, null);
            }
            if (value != null) {
                total += value;
            }
        }
        return total;
    }

    private String messageOrFallback(String key, String fallback) {
        if (plugin == null || plugin.messageService() == null || Texts.isBlank(key)) {
            return fallback;
        }
        String value = plugin.messageService().message(key);
        return Texts.isBlank(value) || key.equals(value) ? fallback : value;
    }

    private PdcPartition resourcePartition(String resourceId) {
        return pdcService.partition("resource." + normalizeId(resourceId));
    }

    private record ItemSlot(String name, ItemStack item) {
    }
}
