package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.attribute.api.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.attribute.model.DamageStageDefinition;
import emaki.jiuwu.craft.attribute.model.DamageStageKind;
import emaki.jiuwu.craft.attribute.model.DamageStageMode;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import net.kyori.adventure.text.Component;

final class AttributeSnapshotCollector {

    private static final double ZERO_EPSILON = 1.0E-9D;

    private final AttributeService service;
    private volatile FusionRuleCache fusionRuleCache = new FusionRuleCache("", List.of());

    AttributeSnapshotCollector(AttributeService service) {
        this.service = service;
    }

    public AttributeSnapshot collectItemSnapshot(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return AttributeSnapshot.empty("");
        }
        LoreParser.ParsedLore parsedLore = parseLore(itemStack);
        PdcAttributeService.PdcAttributeCollection rawPdcContribution = service.pdcAttributeService().collectRawContribution(itemStack);
        if (parsedLore.snapshot().values().isEmpty() && rawPdcContribution.values().isEmpty()) {
            service.stateRepository().clearItemSnapshot(itemStack);
            return AttributeSnapshot.empty("");
        }
        String sourceSignature = SignatureUtil.combine(
                service.itemLoreSignatureVersion(),
                parsedLore.snapshot().sourceSignature(),
                rawPdcContribution.sourceSignature(),
                service.registryService().attributeDefinitionsSignature()
        );
        String cachedSignature = service.stateRepository().readItemSourceSignature(itemStack);
        AttributeSnapshot cachedSnapshot = service.stateRepository().readItemSnapshot(itemStack);
        if (sourceSignature.equals(cachedSignature)
                && cachedSnapshot != null
                && cachedSnapshot.schemaVersion() == AttributeFusionMath.LEGACY_SNAPSHOT_SCHEMA_VERSION) {
            return cachedSnapshot;
        }
        Map<String, Double> values = new LinkedHashMap<>();
        mergeValues(values, parsedLore.snapshot().values());
        overlayValues(values, rawPdcContribution.values());
        AttributeSnapshot snapshot = new AttributeSnapshot(
                AttributeFusionMath.LEGACY_SNAPSHOT_SCHEMA_VERSION,
                sourceSignature,
                values,
                System.currentTimeMillis()
        );
        service.stateRepository().writeItemSnapshot(itemStack, snapshot);
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
        mergeValues(values, service.defaultAttributeValues());
        signatureParts.add("defaults:" + service.registryService().defaultProfilesSignature());
        signatureParts.add("attributes:" + service.registryService().attributeDefinitionsSignature());
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
            Map<String, Double> effectiveValues = new LinkedHashMap<>(itemSnapshot.values());
            PdcAttributeService.PdcAttributeCollection rawPdcContribution = service.pdcAttributeService().collectRawContribution(slot.item());
            PdcAttributeService.PdcAttributeCollection filteredPdcContribution = service.pdcAttributeService().collectFilteredContribution(player, slot.item());
            replacePdcValues(effectiveValues, rawPdcContribution.values(), filteredPdcContribution.values());
            mergeValues(values, effectiveValues);
            signatureParts.add(slot.name() + ":" + SignatureUtil.combine(itemSnapshot.sourceSignature(), filteredPdcContribution.sourceSignature()));
        }
        mergeContributionProviders(player, values, signatureParts);
        applyDerivedValues(values);
        String sourceSignature = SignatureUtil.stableSignature(signatureParts);
        AttributeSnapshot snapshot = new AttributeSnapshot(
                AttributeFusionMath.FUSED_COMBAT_SNAPSHOT_SCHEMA_VERSION,
                sourceSignature,
                values,
                System.currentTimeMillis()
        );
        String cachedSignature = service.stateRepository().readCombatSourceSignature(player);
        AttributeSnapshot cachedSnapshot = service.stateRepository().readCombatSnapshot(player);
        if (sourceSignature.equals(cachedSignature)
                && cachedSnapshot != null
                && cachedSnapshot.schemaVersion() >= AttributeFusionMath.FUSED_COMBAT_SNAPSHOT_SCHEMA_VERSION) {
            return cachedSnapshot;
        }
        service.stateRepository().writeCombatSnapshot(player, snapshot);
        return snapshot;
    }

    private AttributeSnapshot collectLivingCombatSnapshot(LivingEntity entity) {
        Map<String, Double> values = new LinkedHashMap<>();
        List<String> signatureParts = new ArrayList<>();
        mergeValues(values, service.defaultAttributeValues());
        signatureParts.add("defaults:" + service.registryService().defaultProfilesSignature());
        signatureParts.add("attributes:" + service.registryService().attributeDefinitionsSignature());
        AttributeSnapshot cached = service.stateRepository().readCombatSnapshot(entity);
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
        AttributeSnapshot snapshot = new AttributeSnapshot(
                AttributeFusionMath.FUSED_COMBAT_SNAPSHOT_SCHEMA_VERSION,
                sourceSignature,
                values,
                System.currentTimeMillis()
        );
        String cachedSignature = service.stateRepository().readCombatSourceSignature(entity);
        if (sourceSignature.equals(cachedSignature)
                && cached != null
                && cached.schemaVersion() >= AttributeFusionMath.FUSED_COMBAT_SNAPSHOT_SCHEMA_VERSION) {
            return cached;
        }
        service.stateRepository().writeCombatSnapshot(entity, snapshot);
        return snapshot;
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

    private void overlayValues(Map<String, Double> target, Map<String, Double> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = normalizeId(entry.getKey());
            double value = entry.getValue();
            if (Math.abs(value) <= ZERO_EPSILON) {
                target.remove(key);
                continue;
            }
            target.put(key, value);
        }
    }

    private void mergeContributionProviders(LivingEntity entity,
            Map<String, Double> target,
            List<String> signatureParts) {
        if (entity == null) {
            return;
        }
        List<AttributeContributionProvider> providers = service.registryService().orderedContributionProviders();
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

    private void applyDerivedValues(Map<String, Double> values) {
        if (values == null) {
            return;
        }
        applyCombatFusion(values);
        values.put("attribute_power", computeAttributePower(values));
    }

    private void applyCombatFusion(Map<String, Double> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (FusionRule rule : fusionRules()) {
            if (rule == null || rule.flatIds().isEmpty() || rule.percentIds().isEmpty()) {
                continue;
            }
            double percentBonus = sumValues(values, rule.percentIds());
            for (String flatId : rule.flatIds()) {
                Double rawValue = values.get(flatId);
                if (rawValue == null) {
                    continue;
                }
                double effectiveValue = AttributeFusionMath.toEffectiveFlat(rawValue, percentBonus, rule.clampPercentFactor());
                if (Math.abs(effectiveValue) <= ZERO_EPSILON) {
                    values.remove(flatId);
                    continue;
                }
                values.put(flatId, effectiveValue);
            }
        }
    }

    private LoreParser.ParsedLore parseLore(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return new LoreParser.ParsedLore(AttributeSnapshot.empty(SignatureUtil.stableSignature(List.of())), List.of());
        }
        var itemMeta = itemStack.getItemMeta();
        if (itemMeta == null || !itemMeta.hasLore()) {
            return new LoreParser.ParsedLore(AttributeSnapshot.empty(SignatureUtil.stableSignature(List.of())), List.of());
        }
        List<Component> lore = ItemTextBridge.lore(itemMeta);
        if (lore == null || lore.isEmpty()) {
            return new LoreParser.ParsedLore(AttributeSnapshot.empty(SignatureUtil.stableSignature(List.of())), List.of());
        }
        return service.loreParser().parse(lore);
    }

    private void replacePdcValues(Map<String, Double> values,
            Map<String, Double> rawPdcValues,
            Map<String, Double> filteredPdcValues) {
        if (values == null) {
            return;
        }
        if (rawPdcValues != null) {
            for (Map.Entry<String, Double> entry : rawPdcValues.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String key = normalizeId(entry.getKey());
                values.computeIfPresent(key, (ignored, current) -> current - entry.getValue());
                if (values.containsKey(key) && Math.abs(values.get(key)) <= 1.0E-9D) {
                    values.remove(key);
                }
            }
        }
        mergeValues(values, filteredPdcValues);
    }

    private List<FusionRule> fusionRules() {
        String cacheKey = SignatureUtil.combine(
                service.registryService().attributeDefinitionsSignature(),
                service.damageTypeRegistry() == null ? "" : service.damageTypeRegistry().definitionSignature()
        );
        FusionRuleCache cached = fusionRuleCache;
        if (cached.matches(cacheKey)) {
            return cached.rules();
        }
        FusionRuleBuilder builder = new FusionRuleBuilder();
        collectDamageStageFusionRules(builder);
        for (Collection<AttributeDefinition> definitions : service.registryService().resourceAttributeDefinitions().values()) {
            addDefinitionFusionRule(builder, definitions, true);
        }
        addDefinitionFusionRule(builder, service.registryService().genericSpeedDefinitions(), true);
        addDefinitionFusionRule(builder, service.registryService().genericAttackSpeedDefinitions(), true);
        List<FusionRule> rules = builder.rules();
        fusionRuleCache = new FusionRuleCache(cacheKey, rules);
        return rules;
    }

    private void collectDamageStageFusionRules(FusionRuleBuilder builder) {
        if (builder == null) {
            return;
        }
        Map<String, DamageTypeDefinition> damageTypes = service.damageTypeRegistry() == null
                ? Map.of()
                : service.damageTypeRegistry().all();
        for (DamageTypeDefinition damageType : damageTypes.values()) {
            if (damageType == null || damageType.stages().isEmpty()) {
                continue;
            }
            for (DamageStageDefinition stage : damageType.stages()) {
                if (stage == null
                        || stage.kind() != DamageStageKind.FLAT_PERCENT
                        || stage.mode() != DamageStageMode.ADD
                        || stage.flatAttributes().isEmpty()
                        || stage.percentAttributes().isEmpty()) {
                    continue;
                }
                builder.add(stage.flatAttributes(), stage.percentAttributes(), false);
            }
        }
    }

    private void addDefinitionFusionRule(FusionRuleBuilder builder,
            Collection<AttributeDefinition> definitions,
            boolean clampPercentFactor) {
        if (builder == null || definitions == null || definitions.isEmpty()) {
            return;
        }
        List<String> flatIds = new ArrayList<>();
        List<String> percentIds = new ArrayList<>();
        for (AttributeDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            if (definition.valueKind() == AttributeValueKind.PERCENT) {
                percentIds.add(definition.id());
                continue;
            }
            if (Set.of(
                    AttributeValueKind.CHANCE,
                    AttributeValueKind.REGEN,
                    AttributeValueKind.DERIVED
            ).contains(definition.valueKind())) {
                continue;
            }
            flatIds.add(definition.id());
        }
        builder.add(flatIds, percentIds, clampPercentFactor);
    }

    private List<String> normalizeIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String id : ids) {
            String normalizedId = normalizeId(id);
            if (normalizedId.isBlank()) {
                continue;
            }
            normalized.add(normalizedId);
        }
        return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
    }

    private double sumValues(Map<String, Double> values, Collection<String> ids) {
        if (values == null || values.isEmpty() || ids == null || ids.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (String id : ids) {
            if (id == null) {
                continue;
            }
            total += values.getOrDefault(normalizeId(id), 0D);
        }
        return total;
    }

    private double computeAttributePower(Map<String, Double> values) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (AttributeDefinition definition : service.registryService().attributeDefinitions()) {
            if (definition == null || "attribute_power".equals(definition.id())) {
                continue;
            }
            Double value = values.get(definition.id());
            if (value == null) {
                continue;
            }
            double weight = service.attributeBalanceRegistry() == null
                    ? definition.attributePower()
                    : service.attributeBalanceRegistry().weightOf(definition.id(), definition.attributePower());
            total += value * weight;
        }
        return Math.max(0D, total);
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private record ItemSlot(String name, ItemStack item) {

    }

    private record FusionRule(List<String> flatIds, List<String> percentIds, boolean clampPercentFactor) {

        private FusionRule {
            flatIds = flatIds == null ? List.of() : List.copyOf(flatIds);
            percentIds = percentIds == null ? List.of() : List.copyOf(percentIds);
        }
    }

    private record FusionRuleCache(String key, List<FusionRule> rules) {

        private FusionRuleCache {
            key = key == null ? "" : key;
            rules = rules == null ? List.of() : List.copyOf(rules);
        }

        private boolean matches(String candidate) {
            return key.equals(candidate);
        }
    }

    private final class FusionRuleBuilder {

        private final Map<String, FusionRule> rules = new LinkedHashMap<>();
        private final Set<String> registeredFlatIds = new LinkedHashSet<>();

        private void add(Collection<String> flatIds, Collection<String> percentIds, boolean clampPercentFactor) {
            List<String> normalizedFlatIds = normalizeIds(flatIds);
            List<String> normalizedPercentIds = normalizeIds(percentIds);
            if (normalizedFlatIds.isEmpty() || normalizedPercentIds.isEmpty()) {
                return;
            }
            String ruleKey = String.join(",", normalizedFlatIds)
                    + "|"
                    + String.join(",", normalizedPercentIds)
                    + "|"
                    + clampPercentFactor;
            if (rules.containsKey(ruleKey)) {
                return;
            }
            for (String flatId : normalizedFlatIds) {
                if (registeredFlatIds.contains(flatId)) {
                    return;
                }
            }
            rules.put(ruleKey, new FusionRule(normalizedFlatIds, normalizedPercentIds, clampPercentFactor));
            registeredFlatIds.addAll(normalizedFlatIds);
        }

        private List<FusionRule> rules() {
            return rules.isEmpty() ? List.of() : List.copyOf(rules.values());
        }
    }
}
