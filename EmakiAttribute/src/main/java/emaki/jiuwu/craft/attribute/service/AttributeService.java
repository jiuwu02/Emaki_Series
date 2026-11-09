package emaki.jiuwu.craft.attribute.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.config.ScalingCurveConfig;
import emaki.jiuwu.craft.attribute.loader.AttributeBalanceRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.api.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.attribute.api.extension.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.api.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.api.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.attribute.model.ProjectileDamageSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class AttributeService extends AbstractAttributeServiceFacade {

    private static final long PROJECTILE_TTL_MS = 5 * 60 * 1000L;
    private static final String ITEM_LORE_SIGNATURE_VERSION = "lore_parser_v3";

    private final EmakiAttributePlugin plugin;
    private final AsyncTaskScheduler asyncTaskScheduler;
    private final EmakiScheduling scheduling;
    private volatile AttributeConfig config;
    private final AttributeRegistry attributeRegistry;
    private final AttributeBalanceRegistry attributeBalanceRegistry;
    private final DamageTypeRegistry damageTypeRegistry;
    private final DefaultProfileRegistry defaultProfileRegistry;
    private final LoreFormatRegistry loreFormatRegistry;
    private final AttributePresetRegistry presetRegistry;
    private final LoreParser loreParser;
    private final DamageEngine damageEngine;
    private final AsyncDamageEngine asyncDamageEngine;
    private final AttributeStateRepository stateRepository;
    private final VanillaAttributeSynchronizer vanillaSynchronizer;
    private final AttributeRegistryService registryService;
    private final AttributeSnapshotService snapshotService;
    private final ResourceManagementService resourceManagementService;
    private final DamageCalculationService damageCalculationService;
    private final CombatDebugService combatDebugService;
    private final AttributeTraceService attributeTraceService;
    private final DamageTraceService damageTraceService;
    private final AttackBatchInvulnerabilityGate attackBatchInvulnerabilityGate;
    private final PerfectTakeoverCoordinator perfectTakeoverCoordinator;
    private final PdcAttributeService pdcAttributeService;
    private final AttributeSlotRegistry slotRegistry;
    private final TemporaryAttributeService temporaryAttributeService;
    private final ParentAttributeService parentAttributeService;

    public AttributeService(EmakiAttributePlugin plugin,
            PdcService pdcService,
            AsyncTaskScheduler asyncTaskScheduler,
            AttributeConfig config,
            AttributeRegistry attributeRegistry,
            AttributeBalanceRegistry attributeBalanceRegistry,
            DamageTypeRegistry damageTypeRegistry,
            DefaultProfileRegistry defaultProfileRegistry,
            LoreFormatRegistry loreFormatRegistry,
            AttributePresetRegistry presetRegistry,
            PdcAttributeService pdcAttributeService,
            ParentAttributeService parentAttributeService) {
        this(plugin, pdcService, asyncTaskScheduler, config, attributeRegistry, attributeBalanceRegistry,
                damageTypeRegistry, defaultProfileRegistry, loreFormatRegistry, presetRegistry,
                pdcAttributeService, parentAttributeService, null, null);
    }

    public AttributeService(EmakiAttributePlugin plugin,
            PdcService pdcService,
            AsyncTaskScheduler asyncTaskScheduler,
            AttributeConfig config,
            AttributeRegistry attributeRegistry,
            AttributeBalanceRegistry attributeBalanceRegistry,
            DamageTypeRegistry damageTypeRegistry,
            DefaultProfileRegistry defaultProfileRegistry,
            LoreFormatRegistry loreFormatRegistry,
            AttributePresetRegistry presetRegistry,
            PdcAttributeService pdcAttributeService,
            ParentAttributeService parentAttributeService,
            EmakiScheduling scheduling,
            AttributeSlotRegistry slotRegistry) {
        this.plugin = plugin;
        this.asyncTaskScheduler = asyncTaskScheduler;
        this.scheduling = scheduling;
        this.config = config == null ? AttributeConfig.defaults() : config;
        this.attributeRegistry = attributeRegistry;
        this.attributeBalanceRegistry = attributeBalanceRegistry;
        this.damageTypeRegistry = damageTypeRegistry;
        this.defaultProfileRegistry = defaultProfileRegistry;
        this.loreFormatRegistry = loreFormatRegistry;
        this.presetRegistry = presetRegistry;
        this.pdcAttributeService = pdcAttributeService;
        this.parentAttributeService = parentAttributeService;
        this.slotRegistry = slotRegistry;
        this.loreParser = new LoreParser(attributeRegistry, loreFormatRegistry);
        this.damageEngine = new DamageEngine();
        this.asyncDamageEngine = new AsyncDamageEngine(asyncTaskScheduler, damageEngine);
        this.stateRepository = new AttributeStateRepository(pdcService);
        this.vanillaSynchronizer = new VanillaAttributeSynchronizer(plugin);
        this.registryService = new AttributeRegistryService(
                attributeRegistry,
                attributeBalanceRegistry,
                damageTypeRegistry,
                defaultProfileRegistry,
                loreFormatRegistry,
                presetRegistry,
                vanillaSynchronizer
        );
        this.combatDebugService = new CombatDebugService(this);
        this.attributeTraceService = new AttributeTraceService(this);
        this.damageTraceService = new DamageTraceService();
        this.temporaryAttributeService = new TemporaryAttributeService(plugin, this);
        AttributeSnapshotCollector snapshotCollector = new AttributeSnapshotCollector(this);
        this.snapshotService = new AttributeSnapshotService(snapshotCollector);
        this.resourceManagementService = new ResourceManagementService(this);
        this.damageCalculationService = new DamageCalculationService(this);
        this.attackBatchInvulnerabilityGate = new AttackBatchInvulnerabilityGate(this);
        this.perfectTakeoverCoordinator = new PerfectTakeoverCoordinator(this);
        refreshCaches();
        registryService.registerDerivedAttributeProvider(new AttributePowerProvider(this));
    }


    protected void updateConfig(AttributeConfig config) {
        AttributeConfig nextConfig = config == null ? AttributeConfig.defaults() : config;
        boolean resetHealthScaling = this.config != null
                && this.config.healthDisplayScalingEnabled()
                && !nextConfig.healthDisplayScalingEnabled();
        this.config = nextConfig;
        if (resetHealthScaling && resourceManagementService != null) {
            resourceManagementService.resetHealthDisplayScaling();
        }
    }














    AttributeStateRepository stateRepository() {
        return stateRepository;
    }

    VanillaAttributeSynchronizer vanillaSynchronizer() {
        return vanillaSynchronizer;
    }

    public List<AttributeDefinition> mmoItemsMappedDefinitions() {
        return registryService.mmoItemsMappedDefinitions();
    }

    AttributeRegistryService registryService() {
        return registryService;
    }

    AsyncTaskScheduler asyncTaskScheduler() {
        return asyncTaskScheduler;
    }

    EmakiScheduling scheduling() {
        return scheduling;
    }

    AsyncDamageEngine asyncDamageEngine() {
        return asyncDamageEngine;
    }

    PdcAttributeService pdcAttributeService() {
        return pdcAttributeService;
    }

    AttributeSlotRegistry slotRegistry() {
        return slotRegistry;
    }

    @Override
    public TemporaryAttributeService temporaryAttributeService() {
        return temporaryAttributeService;
    }

    public ParentAttributeService parentAttributeService() {
        return parentAttributeService;
    }

    public void invalidateCombatSnapshot(LivingEntity entity) {
        stateRepository.clearCombatSnapshot(entity);
    }

    public void shutdown() {
        if (config.healthDisplayScalingEnabled()) {
            resourceManagementService.resetHealthDisplayScaling();
        }
        if (parentAttributeService != null && parentAttributeService.dataStore() != null) {
            ParentAttributeDataStore.DrainReport report = parentAttributeService.dataStore().flushAndSeal();
            if (report.clean()) {
                plugin.getLogger().info("[shutdown] 父级属性数据排空: " + report);
            } else {
                plugin.getLogger().warning("[shutdown] 父级属性数据排空未完成: " + report);
            }
        }
        temporaryAttributeService.close();
    }

    public void cleanupEntityState(UUID entityId) {
        stateRepository.cleanupEntity(entityId);
        attackBatchInvulnerabilityGate.reset(entityId);
        temporaryAttributeService.discardEntity(entityId);
    }

    long projectileTtlMs() {
        return PROJECTILE_TTL_MS;
    }

    String itemLoreSignatureVersion() {
        return ITEM_LORE_SIGNATURE_VERSION;
    }

    CombatDebugService combatDebug() {
        return combatDebugService;
    }

    public AttributeTraceService attributeTraceService() {
        return attributeTraceService;
    }

    public DamageTraceService damageTraceService() {
        return damageTraceService;
    }

    public PerfectTakeoverCoordinator perfectTakeoverCoordinator() {
        return perfectTakeoverCoordinator;
    }

    public AttackBatchInvulnerabilityGate attackBatchInvulnerabilityGate() {
        return attackBatchInvulnerabilityGate;
    }

    public boolean toggleCombatDebug(Player player) {
        return combatDebugService.toggle(player);
    }

    public boolean setCombatDebug(Player player, boolean enabled) {
        return combatDebugService.setEnabled(player, enabled);
    }

    public boolean shouldTraceCombat(LivingEntity attacker, LivingEntity target) {
        return combatDebugService.shouldTrace(attacker, target);
    }

    public boolean shouldTraceCombat(Projectile projectile, LivingEntity target) {
        return combatDebugService.shouldTrace(projectile, target);
    }

    public void logCombatDebug(String phase, String messageKey) {
        combatDebugService.logMessage(phase, messageKey, Map.of());
    }

    public void logCombatDebug(String phase, String messageKey, Map<String, ?> replacements) {
        combatDebugService.logMessage(phase, messageKey, replacements);
    }

    public CompletableFuture<ResolvedDamage> resolveDamageApplicationAsync(DamageContext damageContext) {
        return damageCalculationService.resolveDamageApplicationAsync(damageContext);
    }

    List<ScalingCurveConfig> scalingCurves() {
        return config.scalingCurves();
    }

    @Override
    public void refreshCaches() {
        registryService.refreshCaches();
        damageCalculationService.refreshCaches();
    }

    @Override
    public AttributeConfig config() {
        return config;
    }

    @Override
    public AttributeRegistry attributeRegistry() {
        return attributeRegistry;
    }

    @Override
    public AttributeBalanceRegistry attributeBalanceRegistry() {
        return attributeBalanceRegistry;
    }

    @Override
    public DamageTypeRegistry damageTypeRegistry() {
        return damageTypeRegistry;
    }

    @Override
    public DefaultProfileRegistry defaultProfileRegistry() {
        return defaultProfileRegistry;
    }

    @Override
    public LoreFormatRegistry loreFormatRegistry() {
        return loreFormatRegistry;
    }

    @Override
    public AttributePresetRegistry presetRegistry() {
        return presetRegistry;
    }

    @Override
    public LoreParser loreParser() {
        return loreParser;
    }

    @Override
    public DamageEngine damageEngine() {
        return damageEngine;
    }

    @Override
    public EmakiAttributePlugin plugin() {
        return plugin;
    }

    @Override
    public void reloadConfig(AttributeConfig config) {
        updateConfig(config == null ? AttributeConfig.defaults() : config);
    }

    @Override
    public String defaultDamageTypeId() {
        return damageCalculationService.defaultDamageTypeId();
    }

    @Override
    public String defaultProjectileDamageTypeId() {
        return damageCalculationService.defaultProjectileDamageTypeId();
    }

    @Override
    public DamageTypeDefinition resolveDamageType(String damageTypeId) {
        return damageCalculationService.resolveDamageType(damageTypeId);
    }

    @Override
    public String defaultProfileSignature() {
        return registryService.combatBaseSignature();
    }

    @Override
    public Map<String, ResourceDefinition> resourceDefinitions() {
        return registryService.resourceDefinitions();
    }

    @Override
    public Map<String, Double> defaultAttributeValues() {
        return registryService.defaultAttributeValues();
    }

    @Override
    public Double resolveAttributeValue(AttributeSnapshot snapshot, String attributeId) {
        if (snapshot == null || Texts.isBlank(attributeId)) {
            return null;
        }
        AttributeDefinition definition = attributeRegistry.resolve(attributeId);
        String normalized = normalizeId(attributeId);
        Double value = definition == null ? null : snapshot.values().get(definition.id());
        if (value != null) {
            return value;
        }
        value = snapshot.values().get(normalized);
        return value != null || definition == null ? value : snapshot.values().get(definition.id());
    }

    @Override
    public void registerContributionProvider(AttributeContributionProvider provider) {
        registryService.registerContributionProvider(provider);
    }

    @Override
    public void unregisterContributionProvider(String providerId) {
        registryService.unregisterContributionProvider(providerId);
    }

    @Override
    public boolean unregisterContributionProvider(String providerId, AttributeContributionProvider expectedProvider) {
        return registryService.unregisterContributionProvider(providerId, expectedProvider);
    }

    @Override
    public AttributeSnapshot collectItemSnapshot(ItemStack itemStack) {
        return snapshotService.collectItemSnapshot(itemStack);
    }

    @Override
    public AttributeSnapshot collectCombatSnapshot(LivingEntity entity) {
        return snapshotService.collectCombatSnapshot(entity);
    }

    @Override
    public AttributeSnapshot collectPlayerCombatSnapshot(Player player) {
        return snapshotService.collectPlayerCombatSnapshot(player);
    }

    @Override
    public void resyncAllPlayers() {
        resourceManagementService.resyncAllPlayers();
    }

    @Override
    public void regenerateOnlinePlayers() {
        resourceManagementService.regenerateOnlinePlayers();
    }

    @Override
    public void resyncPlayer(Player player) {
        resourceManagementService.resyncPlayer(player);
    }

    @Override
    public void scheduleJoinHealthSync(Player player) {
        resourceManagementService.scheduleJoinHealthSync(player);
    }

    @Override
    public void scheduleRespawnHealthSync(Player player) {
        resourceManagementService.scheduleRespawnHealthSync(player);
    }

    @Override
    public void scheduleHealthSync(LivingEntity entity) {
        resourceManagementService.scheduleHealthSync(entity);
    }

    @Override
    public void scheduleEquipmentSync(Player player) {
        resourceManagementService.scheduleEquipmentSync(player);
    }

    public void scheduleEquipmentSync(Player player, String trigger) {
        resourceManagementService.scheduleEquipmentSync(player, trigger);
    }

    @Override
    public void scheduleLivingEntitySync(LivingEntity entity) {
        resourceManagementService.scheduleLivingEntitySync(entity);
    }

    @Override
    public void syncLivingEntity(LivingEntity entity) {
        resourceManagementService.syncLivingEntity(entity);
    }

    @Override
    public void syncPlayer(Player player, ResourceSyncReason reason, Double healthOverride) {
        resourceManagementService.syncPlayer(player, reason, healthOverride);
    }

    @Override
    public ResourceState syncResource(Player player,
            ResourceDefinition resourceDefinition,
            AttributeSnapshot snapshot,
            ResourceSyncReason reason,
            Double currentValueOverride) {
        return resourceManagementService.syncResource(player, resourceDefinition, snapshot, reason, currentValueOverride);
    }

    @Override
    public ResourceState readResourceState(Player player, String resourceId) {
        return resourceManagementService.readResourceState(player, resourceId);
    }

    @Override
    public void setDamageTypeOverride(LivingEntity entity, String damageTypeId) {
        damageCalculationService.setDamageTypeOverride(entity, damageTypeId);
    }

    @Override
    public String peekDamageTypeOverride(LivingEntity entity) {
        return damageCalculationService.peekDamageTypeOverride(entity);
    }

    @Override
    public String consumeDamageTypeOverride(LivingEntity entity) {
        return damageCalculationService.consumeDamageTypeOverride(entity);
    }

    @Override
    public void markSyntheticDamage(LivingEntity entity, boolean value) {
        damageCalculationService.markSyntheticDamage(entity, value);
    }

    @Override
    public boolean isSyntheticDamage(LivingEntity entity) {
        return damageCalculationService.isSyntheticDamage(entity);
    }

    @Override
    public ProjectileDamageSnapshot snapshotProjectile(Projectile projectile, LivingEntity shooter) {
        return damageCalculationService.snapshotProjectile(projectile, shooter);
    }

    @Override
    public ProjectileDamageSnapshot readProjectileSnapshot(Projectile projectile) {
        return damageCalculationService.readProjectileSnapshot(projectile);
    }

    @Override
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
        return damageCalculationService.createDamageContext(
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

    @Override
    public DamageResult calculateDamage(DamageContext damageContext) {
        return damageCalculationService.calculateDamage(damageContext);
    }

    public ResolvedDamage resolveDamageApplication(DamageContext damageContext) {
        return damageCalculationService.resolveDamageApplication(damageContext);
    }

    @Override
    public boolean applyDamage(DamageContext damageContext) {
        return damageCalculationService.applyDamage(damageContext);
    }

    public boolean applyResolvedDamage(ResolvedDamage resolvedDamage, Entity visualSource, double alreadyAppliedDamage) {
        return damageCalculationService.applyResolvedDamage(resolvedDamage, visualSource, alreadyAppliedDamage);
    }

    public CompletableFuture<Boolean> applyResolvedDamageAsync(ResolvedDamage resolvedDamage, double alreadyAppliedDamage) {
        return damageCalculationService.applyResolvedDamageAsync(resolvedDamage, alreadyAppliedDamage);
    }

    public CompletableFuture<Boolean> applyResolvedDamageAsync(ResolvedDamage resolvedDamage,
            Entity visualSource,
            double alreadyAppliedDamage) {
        return damageCalculationService.applyResolvedDamageAsync(resolvedDamage, visualSource, alreadyAppliedDamage);
    }

    public void applyDamageSideEffects(ResolvedDamage resolvedDamage, Entity visualSource) {
        damageCalculationService.applyDamageSideEffects(resolvedDamage, visualSource);
    }

    public CompletableFuture<Boolean> applyDamageSideEffectsAsync(ResolvedDamage resolvedDamage) {
        return damageCalculationService.applyDamageSideEffectsAsync(resolvedDamage);
    }

    @Override
    public boolean applyProjectileDamage(DamageContext damageContext) {
        return damageCalculationService.applyProjectileDamage(damageContext);
    }

    @Override
    public void clearPlayerDamageTypeOverride(Player player) {
        damageCalculationService.clearPlayerDamageTypeOverride(player);
    }

    @Override
    public boolean isAttackCoolingDown(Player player) {
        return resourceManagementService.isAttackCoolingDown(player);
    }

    @Override
    public int startAttackCooldown(Player player, AttributeSnapshot snapshot, ItemStack itemStack) {
        return resourceManagementService.startAttackCooldown(player, snapshot, itemStack);
    }

}
