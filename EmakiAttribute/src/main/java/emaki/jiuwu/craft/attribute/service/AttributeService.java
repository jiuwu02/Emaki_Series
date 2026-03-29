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
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.service.VanillaAttributeSynchronizer.VanillaAttributeBinding;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AttributeService extends AbstractAttributeServiceFacade {

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
    private final AttributeRegistryService registryService;
    private final AttributeSnapshotService snapshotService;
    private final ResourceManagementService resourceManagementService;
    private final DamageCalculationService damageCalculationService;

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
        this.config = config == null ? AttributeConfig.defaults() : config;
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
        this.registryService = new AttributeRegistryService(
            attributeRegistry,
            attributeBalanceRegistry,
            damageTypeRegistry,
            defaultProfileRegistry,
            loreFormatRegistry,
            presetRegistry,
            vanillaSynchronizer
        );
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

    AttributeStateRepository stateRepositoryInternal() {
        return stateRepository;
    }

    VanillaAttributeSynchronizer vanillaSynchronizerInternal() {
        return vanillaSynchronizer;
    }

    List<AttributeDefinition> attributeDefinitionsInternal() {
        return registryService.attributeDefinitions();
    }

    Map<String, Double> defaultAttributeValuesInternal() {
        return registryService.defaultAttributeValues();
    }

    Map<String, ResourceDefinition> resourceDefinitionsInternal() {
        return registryService.resourceDefinitions();
    }

    Map<String, List<AttributeDefinition>> resourceAttributeDefinitionsInternal() {
        return registryService.resourceAttributeDefinitions();
    }

    Map<String, List<AttributeDefinition>> resourceRegenDefinitionsInternal() {
        return registryService.resourceRegenDefinitions();
    }

    List<VanillaAttributeBinding> vanillaAttributeBindingsInternal() {
        return registryService.vanillaAttributeBindings();
    }

    Set<String> vanillaMappedAttributeIdsInternal() {
        return registryService.vanillaMappedAttributeIds();
    }

    List<AttributeDefinition> genericSpeedDefinitionsInternal() {
        return registryService.genericSpeedDefinitions();
    }

    List<AttributeDefinition> genericAttackSpeedDefinitionsInternal() {
        return registryService.genericAttackSpeedDefinitions();
    }

    String defaultProfilesSignatureInternal() {
        return registryService.defaultProfilesSignature();
    }

    String attributeDefinitionsSignatureInternal() {
        return registryService.attributeDefinitionsSignature();
    }

    List<AttributeContributionProvider> orderedContributionProvidersInternal() {
        return registryService.orderedContributionProviders();
    }

    long projectileTtlMs() {
        return PROJECTILE_TTL_MS;
    }

    String itemLoreSignatureVersion() {
        return ITEM_LORE_SIGNATURE_VERSION;
    }
}
