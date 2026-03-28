package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.loader.AttributeBalanceRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.attribute.model.DefaultProfile;
import emaki.jiuwu.craft.attribute.model.ProjectileDamageSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.attribute.service.VanillaAttributeSynchronizer.VanillaAttributeBinding;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

public final class AttributeService {

    private static final long PROJECTILE_TTL_MS = 5 * 60 * 1000L;
    private static final String ITEM_LORE_SIGNATURE_VERSION = "lore_parser_v2";

    private final EmakiAttributePlugin plugin;
    private volatile AttributeConfig config;
    private final AttributeRegistry attributeRegistry;
    private final AttributeBalanceRegistry attributeBalanceRegistry;
    private final DamageTypeRegistry damageTypeRegistry;
    private final DefaultProfileRegistry defaultProfileRegistry;
    private final LoreFormatRegistry loreFormatRegistry;
    private final AttributePresetRegistry presetRegistry;
    private final LoreParser loreParser;
    private final DamageEngine damageEngine;
    private final AttributeStateRepository stateRepository;
    private final VanillaAttributeSynchronizer vanillaSynchronizer;
    private final AttributeSnapshotCollector snapshotCollector;
    private final AttributeResourceCoordinator resourceCoordinator;
    private final AttributeDamageCoordinator damageCoordinator;
    private final Map<String, AttributeContributionProvider> contributionProviders = new LinkedHashMap<>();
    private volatile List<AttributeDefinition> attributeDefinitions = List.of();
    private volatile List<DefaultProfile> defaultProfiles = List.of();
    private volatile Map<String, ResourceDefinition> resourceDefinitions = Map.of();
    private volatile Map<String, Double> defaultAttributeValues = Map.of();
    private volatile Map<String, List<AttributeDefinition>> resourceAttributeDefinitions = Map.of();
    private volatile Map<String, List<AttributeDefinition>> resourceRegenDefinitions = Map.of();
    private volatile List<VanillaAttributeBinding> vanillaAttributeBindings = List.of();
    private volatile Set<String> vanillaMappedAttributeIds = Set.of();
    private volatile List<AttributeDefinition> genericSpeedDefinitions = List.of();
    private volatile List<AttributeDefinition> genericAttackSpeedDefinitions = List.of();
    private volatile String defaultProfilesSignature = "";
    private volatile String attributeDefinitionsSignature = "";
    private volatile String combatBaseSignature = "";
    private volatile List<AttributeContributionProvider> orderedContributionProviders = List.of();

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
        this.config = config;
        this.attributeRegistry = attributeRegistry;
        this.attributeBalanceRegistry = attributeBalanceRegistry;
        this.damageTypeRegistry = damageTypeRegistry;
        this.defaultProfileRegistry = defaultProfileRegistry;
        this.loreFormatRegistry = loreFormatRegistry;
        this.presetRegistry = presetRegistry;
        this.loreParser = new LoreParser(attributeRegistry, loreFormatRegistry);
        this.damageEngine = new DamageEngine();
        this.stateRepository = new AttributeStateRepository(pdcService);
        this.vanillaSynchronizer = new VanillaAttributeSynchronizer(plugin);
        this.snapshotCollector = new AttributeSnapshotCollector(this);
        this.resourceCoordinator = new AttributeResourceCoordinator(this);
        this.damageCoordinator = new AttributeDamageCoordinator(this);
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
        List<VanillaAttributeBinding> vanillaBindings = new ArrayList<>();
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
            if (definition.isVanillaMappedValue()) {
                VanillaAttributeBinding binding = vanillaSynchronizer.resolveBinding(definition);
                if (binding != null) {
                    vanillaBindings.add(binding);
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
        defaultAttributeValues = buildDefaultAttributeValues(profiles);
        resourceAttributeDefinitions = freezeDefinitionBuckets(resourceBuckets);
        resourceRegenDefinitions = freezeDefinitionBuckets(resourceRegenBuckets);
        vanillaAttributeBindings = vanillaBindings.isEmpty() ? List.of() : List.copyOf(vanillaBindings);
        vanillaMappedAttributeIds = vanillaSynchronizer.collectManagedTargetIds(vanillaAttributeBindings);
        genericSpeedDefinitions = List.copyOf(speedDefinitions);
        genericAttackSpeedDefinitions = List.copyOf(attackSpeedDefinitions);
        defaultProfilesSignature = SignatureUtil.stableSignature(defaultProfiles);
        attributeDefinitionsSignature = SignatureUtil.stableSignature(attributeDefinitions);
        combatBaseSignature = SignatureUtil.combine(defaultProfilesSignature, attributeDefinitionsSignature);
        refreshContributionProviderCache();
    }

    public String defaultDamageTypeId() {
        return damageCoordinator.defaultDamageTypeId();
    }

    public String defaultProjectileDamageTypeId() {
        return damageCoordinator.defaultProjectileDamageTypeId();
    }

    public DamageTypeDefinition resolveDamageType(String damageTypeId) {
        return damageCoordinator.resolveDamageType(damageTypeId);
    }

    public String defaultProfileSignature() {
        return combatBaseSignature;
    }

    public Map<String, ResourceDefinition> resourceDefinitions() {
        return resourceDefinitions;
    }

    public Map<String, Double> defaultAttributeValues() {
        return defaultAttributeValues;
    }

    public Double resolveAttributeValue(AttributeSnapshot snapshot, String attributeId) {
        if (snapshot == null || Texts.isBlank(attributeId)) {
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
        if (provider == null || Texts.isBlank(provider.id())) {
            return;
        }
        contributionProviders.put(normalizeId(provider.id()), provider);
        refreshContributionProviderCache();
    }

    public void unregisterContributionProvider(String providerId) {
        if (Texts.isBlank(providerId)) {
            return;
        }
        contributionProviders.remove(normalizeId(providerId));
        refreshContributionProviderCache();
    }

    public AttributeSnapshot collectItemSnapshot(ItemStack itemStack) {
        return snapshotCollector.collectItemSnapshot(itemStack);
    }

    public AttributeSnapshot collectCombatSnapshot(LivingEntity entity) {
        return snapshotCollector.collectCombatSnapshot(entity);
    }

    public AttributeSnapshot collectPlayerCombatSnapshot(Player player) {
        return snapshotCollector.collectPlayerCombatSnapshot(player);
    }

    public void resyncAllPlayers() {
        resourceCoordinator.resyncAllPlayers();
    }

    public void regenerateOnlinePlayers() {
        resourceCoordinator.regenerateOnlinePlayers();
    }

    public void resyncPlayer(Player player) {
        resourceCoordinator.resyncPlayer(player);
    }

    public void scheduleJoinHealthSync(Player player) {
        resourceCoordinator.scheduleJoinHealthSync(player);
    }

    public void scheduleRespawnHealthSync(Player player) {
        resourceCoordinator.scheduleRespawnHealthSync(player);
    }

    public void scheduleHealthSync(LivingEntity entity) {
        resourceCoordinator.scheduleHealthSync(entity);
    }

    public void scheduleEquipmentSync(Player player) {
        resourceCoordinator.scheduleEquipmentSync(player);
    }

    public void scheduleLivingEntitySync(LivingEntity entity) {
        resourceCoordinator.scheduleLivingEntitySync(entity);
    }

    public void syncLivingEntity(LivingEntity entity) {
        resourceCoordinator.syncLivingEntity(entity);
    }

    public void syncPlayer(Player player, ResourceSyncReason reason, Double healthOverride) {
        resourceCoordinator.syncPlayer(player, reason, healthOverride);
    }

    public ResourceState syncResource(Player player,
                                      ResourceDefinition resourceDefinition,
                                      AttributeSnapshot snapshot,
                                      ResourceSyncReason reason,
                                      Double currentValueOverride) {
        return resourceCoordinator.syncResource(player, resourceDefinition, snapshot, reason, currentValueOverride);
    }

    public ResourceState readResourceState(Player player, String resourceId) {
        return resourceCoordinator.readResourceState(player, resourceId);
    }

    public void setDamageTypeOverride(LivingEntity entity, String damageTypeId) {
        damageCoordinator.setDamageTypeOverride(entity, damageTypeId);
    }

    public String peekDamageTypeOverride(LivingEntity entity) {
        return damageCoordinator.peekDamageTypeOverride(entity);
    }

    public String consumeDamageTypeOverride(LivingEntity entity) {
        return damageCoordinator.consumeDamageTypeOverride(entity);
    }

    public void markSyntheticDamage(LivingEntity entity, boolean value) {
        damageCoordinator.markSyntheticDamage(entity, value);
    }

    public boolean isSyntheticDamage(LivingEntity entity) {
        return damageCoordinator.isSyntheticDamage(entity);
    }

    public ProjectileDamageSnapshot snapshotProjectile(Projectile projectile, LivingEntity shooter) {
        return damageCoordinator.snapshotProjectile(projectile, shooter);
    }

    public ProjectileDamageSnapshot readProjectileSnapshot(Projectile projectile) {
        return damageCoordinator.readProjectileSnapshot(projectile);
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
        return damageCoordinator.createDamageContext(
            attacker,
            target,
            projectile,
            cause,
            damageTypeId,
            sourceDamage,
            baseDamage,
            attackerSnapshot,
            targetSnapshot,
            context
        );
    }

    public DamageContext createDamageContext(LivingEntity attacker,
                                             LivingEntity target,
                                             Projectile projectile,
                                             EntityDamageEvent.DamageCause cause,
                                             String damageTypeId,
                                             double sourceDamage,
                                             double baseDamage,
                                             Map<String, ?> context) {
        return damageCoordinator.createDamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, context);
    }

    public DamageContext createDamageContext(LivingEntity attacker,
                                             LivingEntity target,
                                             Projectile projectile,
                                             EntityDamageEvent.DamageCause cause,
                                             String damageTypeId,
                                             double sourceDamage,
                                             double baseDamage,
                                             DamageContextVariables context) {
        return damageCoordinator.createDamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, context);
    }

    public DamageResult calculateDamage(DamageContext damageContext) {
        return damageCoordinator.calculateDamage(damageContext);
    }

    public DamageResult calculateDamage(LivingEntity attacker,
                                        LivingEntity target,
                                        String damageTypeId,
                                        double baseDamage,
                                        DamageContextVariables context) {
        return damageCoordinator.calculateDamage(attacker, target, damageTypeId, baseDamage, context);
    }

    public DamageResult calculateDamage(LivingEntity attacker,
                                        LivingEntity target,
                                        String damageTypeId,
                                        double baseDamage,
                                        Map<String, ?> context) {
        return damageCoordinator.calculateDamage(attacker, target, damageTypeId, baseDamage, context);
    }

    public boolean applyDamage(DamageContext damageContext) {
        return damageCoordinator.applyDamage(damageContext);
    }

    public boolean applyDamage(LivingEntity attacker,
                               LivingEntity target,
                               String damageTypeId,
                               double baseDamage,
                               DamageContextVariables context) {
        return damageCoordinator.applyDamage(attacker, target, damageTypeId, baseDamage, context);
    }

    public boolean applyDamage(LivingEntity attacker,
                               LivingEntity target,
                               String damageTypeId,
                               double baseDamage,
                               Map<String, ?> context) {
        return damageCoordinator.applyDamage(attacker, target, damageTypeId, baseDamage, context);
    }

    public boolean applyProjectileDamage(DamageContext damageContext) {
        return damageCoordinator.applyProjectileDamage(damageContext);
    }

    public boolean applyProjectileDamage(Projectile projectile,
                                         LivingEntity target,
                                         double baseDamage,
                                         DamageContextVariables context) {
        return damageCoordinator.applyProjectileDamage(projectile, target, baseDamage, context);
    }

    public boolean applyProjectileDamage(Projectile projectile,
                                         LivingEntity target,
                                         double baseDamage,
                                         Map<String, ?> context) {
        return damageCoordinator.applyProjectileDamage(projectile, target, baseDamage, context);
    }

    public void clearPlayerDamageTypeOverride(Player player) {
        damageCoordinator.clearPlayerDamageTypeOverride(player);
    }

    public boolean isAttackCoolingDown(Player player) {
        return resourceCoordinator.isAttackCoolingDown(player);
    }

    public int startAttackCooldown(Player player, AttributeSnapshot snapshot, ItemStack itemStack) {
        return resourceCoordinator.startAttackCooldown(player, snapshot, itemStack);
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

    private Map<String, Double> buildDefaultAttributeValues(List<DefaultProfile> profiles) {
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

    private void refreshContributionProviderCache() {
        List<AttributeContributionProvider> providers = new ArrayList<>(contributionProviders.values());
        providers.sort(Comparator.comparingInt(AttributeContributionProvider::priority).reversed()
            .thenComparing(provider -> normalizeId(provider.id())));
        orderedContributionProviders = providers.isEmpty() ? List.of() : List.copyOf(providers);
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    AttributeStateRepository stateRepositoryInternal() {
        return stateRepository;
    }

    VanillaAttributeSynchronizer vanillaSynchronizerInternal() {
        return vanillaSynchronizer;
    }

    List<AttributeDefinition> attributeDefinitionsInternal() {
        return attributeDefinitions;
    }

    Map<String, Double> defaultAttributeValuesInternal() {
        return defaultAttributeValues;
    }

    Map<String, ResourceDefinition> resourceDefinitionsInternal() {
        return resourceDefinitions;
    }

    Map<String, List<AttributeDefinition>> resourceAttributeDefinitionsInternal() {
        return resourceAttributeDefinitions;
    }

    Map<String, List<AttributeDefinition>> resourceRegenDefinitionsInternal() {
        return resourceRegenDefinitions;
    }

    List<VanillaAttributeBinding> vanillaAttributeBindingsInternal() {
        return vanillaAttributeBindings;
    }

    Set<String> vanillaMappedAttributeIdsInternal() {
        return vanillaMappedAttributeIds;
    }

    List<AttributeDefinition> genericSpeedDefinitionsInternal() {
        return genericSpeedDefinitions;
    }

    List<AttributeDefinition> genericAttackSpeedDefinitionsInternal() {
        return genericAttackSpeedDefinitions;
    }

    String defaultProfilesSignatureInternal() {
        return defaultProfilesSignature;
    }

    String attributeDefinitionsSignatureInternal() {
        return attributeDefinitionsSignature;
    }

    List<AttributeContributionProvider> orderedContributionProvidersInternal() {
        return orderedContributionProviders;
    }

    long projectileTtlMs() {
        return PROJECTILE_TTL_MS;
    }

    String itemLoreSignatureVersion() {
        return ITEM_LORE_SIGNATURE_VERSION;
    }
}
