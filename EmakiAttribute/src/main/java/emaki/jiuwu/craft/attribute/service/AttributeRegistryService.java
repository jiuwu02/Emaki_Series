package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.loader.AttributeBalanceRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.attribute.model.DefaultProfile;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.service.VanillaAttributeSynchronizer.VanillaAttributeBinding;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AttributeRegistryService {

    private final AttributeRegistry attributeRegistry;
    private final AttributeBalanceRegistry attributeBalanceRegistry;
    private final DamageTypeRegistry damageTypeRegistry;
    private final DefaultProfileRegistry defaultProfileRegistry;
    private final LoreFormatRegistry loreFormatRegistry;
    private final AttributePresetRegistry presetRegistry;
    private final VanillaAttributeSynchronizer vanillaSynchronizer;
    private final Map<String, AttributeContributionProvider> contributionProviders = new LinkedHashMap<>();
    private volatile List<AttributeDefinition> attributeDefinitions = List.of();
    private volatile List<DefaultProfile> defaultProfiles = List.of();
    private volatile Map<String, ResourceDefinition> resourceDefinitions = Map.of();
    private volatile Map<String, Double> defaultAttributeValues = Map.of();
    private volatile Map<String, List<AttributeDefinition>> resourceAttributeDefinitions = Map.of();
    private volatile Map<String, List<AttributeDefinition>> resourceRegenDefinitions = Map.of();
    private volatile List<VanillaAttributeBinding> vanillaAttributeBindings = List.of();
    private volatile Set<String> vanillaMappedAttributeIds = Set.of();
    private volatile List<AttributeDefinition> mmoItemsMappedDefinitions = List.of();
    private volatile List<AttributeDefinition> genericSpeedDefinitions = List.of();
    private volatile List<AttributeDefinition> genericAttackSpeedDefinitions = List.of();
    private volatile String defaultProfilesSignature = "";
    private volatile String attributeDefinitionsSignature = "";
    private volatile String combatBaseSignature = "";
    private volatile List<AttributeContributionProvider> orderedContributionProviders = List.of();

    AttributeRegistryService(AttributeRegistry attributeRegistry,
                             AttributeBalanceRegistry attributeBalanceRegistry,
                             DamageTypeRegistry damageTypeRegistry,
                             DefaultProfileRegistry defaultProfileRegistry,
                             LoreFormatRegistry loreFormatRegistry,
                             AttributePresetRegistry presetRegistry,
                             VanillaAttributeSynchronizer vanillaSynchronizer) {
        this.attributeRegistry = attributeRegistry;
        this.attributeBalanceRegistry = attributeBalanceRegistry;
        this.damageTypeRegistry = damageTypeRegistry;
        this.defaultProfileRegistry = defaultProfileRegistry;
        this.loreFormatRegistry = loreFormatRegistry;
        this.presetRegistry = presetRegistry;
        this.vanillaSynchronizer = vanillaSynchronizer;
    }

    void refreshCaches() {
        List<AttributeDefinition> definitions = attributeRegistry == null
            ? List.of()
            : List.copyOf(attributeRegistry.all().values());
        List<DefaultProfile> profiles = defaultProfileRegistry == null
            ? List.of()
            : defaultProfileRegistry.mergedProfiles();
        Map<String, List<AttributeDefinition>> resourceBuckets = new LinkedHashMap<>();
        Map<String, List<AttributeDefinition>> resourceRegenBuckets = new LinkedHashMap<>();
        List<VanillaAttributeBinding> vanillaBindings = new ArrayList<>();
        List<AttributeDefinition> mmoItemsDefinitions = new ArrayList<>();
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
            if (Texts.isNotBlank(definition.mmoItemsStatId())) {
                mmoItemsDefinitions.add(definition);
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
        mmoItemsMappedDefinitions = mmoItemsDefinitions.isEmpty() ? List.of() : List.copyOf(mmoItemsDefinitions);
        genericSpeedDefinitions = List.copyOf(speedDefinitions);
        genericAttackSpeedDefinitions = List.copyOf(attackSpeedDefinitions);
        defaultProfilesSignature = SignatureUtil.stableSignature(defaultProfiles);
        attributeDefinitionsSignature = SignatureUtil.stableSignature(attributeDefinitions);
        combatBaseSignature = SignatureUtil.combine(defaultProfilesSignature, attributeDefinitionsSignature);
        refreshContributionProviderCache();
    }

    void registerContributionProvider(AttributeContributionProvider provider) {
        if (provider == null || Texts.isBlank(provider.id())) {
            return;
        }
        contributionProviders.put(normalizeId(provider.id()), provider);
        refreshContributionProviderCache();
    }

    void unregisterContributionProvider(String providerId) {
        if (Texts.isBlank(providerId)) {
            return;
        }
        contributionProviders.remove(normalizeId(providerId));
        refreshContributionProviderCache();
    }

    List<AttributeDefinition> attributeDefinitions() {
        return attributeDefinitions;
    }

    Map<String, ResourceDefinition> resourceDefinitions() {
        return resourceDefinitions;
    }

    Map<String, Double> defaultAttributeValues() {
        return defaultAttributeValues;
    }

    Map<String, List<AttributeDefinition>> resourceAttributeDefinitions() {
        return resourceAttributeDefinitions;
    }

    Map<String, List<AttributeDefinition>> resourceRegenDefinitions() {
        return resourceRegenDefinitions;
    }

    List<VanillaAttributeBinding> vanillaAttributeBindings() {
        return vanillaAttributeBindings;
    }

    Set<String> vanillaMappedAttributeIds() {
        return vanillaMappedAttributeIds;
    }

    List<AttributeDefinition> mmoItemsMappedDefinitions() {
        return mmoItemsMappedDefinitions;
    }

    List<AttributeDefinition> genericSpeedDefinitions() {
        return genericSpeedDefinitions;
    }

    List<AttributeDefinition> genericAttackSpeedDefinitions() {
        return genericAttackSpeedDefinitions;
    }

    String defaultProfilesSignature() {
        return defaultProfilesSignature;
    }

    String attributeDefinitionsSignature() {
        return attributeDefinitionsSignature;
    }

    String combatBaseSignature() {
        return combatBaseSignature;
    }

    List<AttributeContributionProvider> orderedContributionProviders() {
        return orderedContributionProviders;
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
}
