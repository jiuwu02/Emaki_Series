package emaki.jiuwu.craft.attribute.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.loader.AttributeBalanceRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.pdc.PdcService;

public final class AttributeService extends AbstractAttributeServiceFacade {

    private static final long PROJECTILE_TTL_MS = 5 * 60 * 1000L;
    private static final String ITEM_LORE_SIGNATURE_VERSION = "lore_parser_v2";

    private final EmakiAttributePlugin plugin;
    private final AsyncTaskScheduler asyncTaskScheduler;
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
    private final PdcAttributeService pdcAttributeService;

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
            PdcAttributeService pdcAttributeService) {
        this.plugin = plugin;
        this.asyncTaskScheduler = asyncTaskScheduler;
        this.config = config == null ? AttributeConfig.defaults() : config;
        this.attributeRegistry = attributeRegistry;
        this.attributeBalanceRegistry = attributeBalanceRegistry;
        this.damageTypeRegistry = damageTypeRegistry;
        this.defaultProfileRegistry = defaultProfileRegistry;
        this.loreFormatRegistry = loreFormatRegistry;
        this.presetRegistry = presetRegistry;
        this.pdcAttributeService = pdcAttributeService;
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
        AttributeSnapshotCollector snapshotCollector = new AttributeSnapshotCollector(this);
        this.snapshotService = new AttributeSnapshotService(snapshotCollector);
        this.resourceManagementService = new ResourceManagementService(this);
        this.damageCalculationService = new DamageCalculationService(this);
        refreshCaches();
    }

    @Override
    protected AttributeConfig configRef() {
        return config;
    }

    @Override
    protected void updateConfig(AttributeConfig config) {
        this.config = config;
    }

    @Override
    protected AttributeRegistry attributeRegistryRef() {
        return attributeRegistry;
    }

    @Override
    protected AttributeBalanceRegistry attributeBalanceRegistryRef() {
        return attributeBalanceRegistry;
    }

    @Override
    protected DamageTypeRegistry damageTypeRegistryRef() {
        return damageTypeRegistry;
    }

    @Override
    protected DefaultProfileRegistry defaultProfileRegistryRef() {
        return defaultProfileRegistry;
    }

    @Override
    protected LoreFormatRegistry loreFormatRegistryRef() {
        return loreFormatRegistry;
    }

    @Override
    protected AttributePresetRegistry presetRegistryRef() {
        return presetRegistry;
    }

    @Override
    protected LoreParser loreParserRef() {
        return loreParser;
    }

    @Override
    protected DamageEngine damageEngineRef() {
        return damageEngine;
    }

    @Override
    protected EmakiAttributePlugin pluginRef() {
        return plugin;
    }

    @Override
    protected AttributeRegistryService registryServiceRef() {
        return registryService;
    }

    @Override
    protected AttributeSnapshotService snapshotServiceRef() {
        return snapshotService;
    }

    @Override
    protected ResourceManagementService resourceManagementServiceRef() {
        return resourceManagementService;
    }

    @Override
    protected DamageCalculationService damageCalculationServiceRef() {
        return damageCalculationService;
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

    AsyncDamageEngine asyncDamageEngine() {
        return asyncDamageEngine;
    }

    PdcAttributeService pdcAttributeService() {
        return pdcAttributeService;
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

    @Override
    public void refreshCaches() {
        super.refreshCaches();
        damageCalculationService.refreshCaches();
    }
}
