package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

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
import emaki.jiuwu.craft.corelib.text.Texts;

final class AttributeSnapshotCollector {

    private static final double ZERO_EPSILON = 1.0E-9D;
    private static final String[] EQUIPMENT_SLOT_NAMES = {
            "main_hand",
            "off_hand",
            "helmet",
            "chestplate",
            "leggings",
            "boots"
    };

    private final AttributeService service;
    private volatile FusionRuleCache fusionRuleCache = new FusionRuleCache("", List.of());

    AttributeSnapshotCollector(AttributeService service) {
        this.service = service;
    }

    public AttributeSnapshot collectItemSnapshot(ItemStack itemStack) {
        return collectItemSnapshot(itemStack, service.pdcAttributeService().collectRawContribution(itemStack));
    }

    private AttributeSnapshot collectItemSnapshot(ItemStack itemStack,
            PdcAttributeService.PdcAttributeCollection rawPdcContribution) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return AttributeSnapshot.empty("");
        }
        LoreParser.ParsedLore parsedLore = parseLore(itemStack);
        PdcAttributeService.PdcAttributeCollection resolvedRawContribution = rawPdcContribution == null
                ? new PdcAttributeService.PdcAttributeCollection(Map.of(), "")
                : rawPdcContribution;
        if (parsedLore.snapshot().values().isEmpty() && resolvedRawContribution.values().isEmpty()) {
            service.stateRepository().clearItemSnapshot(itemStack);
            return AttributeSnapshot.empty("");
        }
        String sourceSignature = SignatureUtil.combine(
                service.itemLoreSignatureVersion(),
                parsedLore.snapshot().sourceSignature(),
                resolvedRawContribution.sourceSignature(),
                service.registryService().attributeDefinitionsSignature()
        );
        String cachedSignature = service.stateRepository().readItemSourceSignature(itemStack);
        AttributeSnapshot cachedSnapshot = service.stateRepository().readItemSnapshot(itemStack);
        if (sourceSignature.equals(cachedSignature)
                && cachedSnapshot != null
                && cachedSnapshot.schemaVersion() == AttributeFusionMath.ITEM_SNAPSHOT_SCHEMA_VERSION) {
            return cachedSnapshot;
        }
        Map<String, Double> values = new LinkedHashMap<>();
        mergeValues(values, parsedLore.snapshot().values());
        overlayValues(values, resolvedRawContribution.values());
        AttributeSnapshot snapshot = new AttributeSnapshot(
                AttributeFusionMath.ITEM_SNAPSHOT_SCHEMA_VERSION,
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
        PlayerInventory inventory = player.getInventory();
        return collectCombatSnapshot(
                player,
                index -> switch (index) {
                    case 0 -> inventory.getItemInMainHand();
                    case 1 -> inventory.getItemInOffHand();
                    case 2 -> inventory.getHelmet();
                    case 3 -> inventory.getChestplate();
                    case 4 -> inventory.getLeggings();
                    case 5 -> inventory.getBoots();
                    default -> null;
                },
                player
        );
    }

    private AttributeSnapshot collectLivingCombatSnapshot(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        IntFunction<org.bukkit.inventory.ItemStack> itemResolver = equipment == null ? null : index -> switch (index) {
            case 0 -> equipment.getItemInMainHand();
            case 1 -> equipment.getItemInOffHand();
            case 2 -> equipment.getHelmet();
            case 3 -> equipment.getChestplate();
            case 4 -> equipment.getLeggings();
            case 5 -> equipment.getBoots();
            default -> null;
        };
        return collectCombatSnapshot(entity, itemResolver, null);
    }

    private AttributeSnapshot collectCombatSnapshot(LivingEntity entity,
            IntFunction<org.bukkit.inventory.ItemStack> itemResolver,
            Player playerOrNull) {
        Map<String, Double> values = new LinkedHashMap<>();
        List<String> signatureParts = new ArrayList<>();
        mergeValues(values, service.defaultAttributeValues());
        signatureParts.add("defaults:" + service.registryService().defaultProfilesSignature());
        signatureParts.add("attributes:" + service.registryService().attributeDefinitionsSignature());
        if (itemResolver != null) {
            collectEquipmentSnapshots(itemResolver, playerOrNull, values, signatureParts);
        }
        mergeContributionProviders(entity, values, signatureParts);
        if (playerOrNull != null) {
            mergeValues(values, service.temporaryAttributeService().additiveValues(playerOrNull));
            overlayValues(values, service.temporaryAttributeService().setValues(playerOrNull));
            String temporarySignature = service.temporaryAttributeService().signature(playerOrNull);
            if (Texts.isNotBlank(temporarySignature)) {
                signatureParts.add("temporary:" + temporarySignature);
            }
        }
        applyDerivedValues(values);
        String sourceSignature = SignatureUtil.stableSignature(signatureParts);
        AttributeSnapshot snapshot = new AttributeSnapshot(
                AttributeFusionMath.FUSED_COMBAT_SNAPSHOT_SCHEMA_VERSION,
                sourceSignature,
                values,
                System.currentTimeMillis()
        );
        String cachedSignature = service.stateRepository().readCombatSourceSignature(entity);
        AttributeSnapshot cachedSnapshot = service.stateRepository().readCombatSnapshot(entity);
        if (sourceSignature.equals(cachedSignature)
                && cachedSnapshot != null
                && cachedSnapshot.schemaVersion() >= AttributeFusionMath.FUSED_COMBAT_SNAPSHOT_SCHEMA_VERSION) {
            return cachedSnapshot;
        }
        service.stateRepository().writeCombatSnapshot(entity, snapshot);
        return snapshot;
    }

    private void collectEquipmentSnapshots(IntFunction<org.bukkit.inventory.ItemStack> itemResolver,
            Player player,
            Map<String, Double> values,
            List<String> signatureParts) {
        if (itemResolver == null) {
            return;
        }
        for (int index = 0; index < EQUIPMENT_SLOT_NAMES.length; index++) {
            org.bukkit.inventory.ItemStack itemStack = itemResolver.apply(index);
            PdcAttributeService.PdcAttributeViews views = player == null
                    ? null
                    : service.pdcAttributeService().collectContributionViews(player, itemStack);
            AttributeSnapshot itemSnapshot = player == null
                    ? collectItemSnapshot(itemStack)
                    : collectItemSnapshot(itemStack, views.raw());
            if (itemSnapshot == null) {
                continue;
            }
            if (player == null) {
                mergeValues(values, itemSnapshot.values());
                signatureParts.add(EQUIPMENT_SLOT_NAMES[index] + ":" + itemSnapshot.sourceSignature());
                continue;
            }
            Map<String, Double> effectiveValues = new LinkedHashMap<>(itemSnapshot.values());
            replacePdcValues(effectiveValues, views.raw().values(), views.filtered().values());
            mergeValues(values, effectiveValues);
            signatureParts.add(EQUIPMENT_SLOT_NAMES[index] + ":" + SignatureUtil.combine(
                    itemSnapshot.sourceSignature(),
                    views.filtered().sourceSignature()
            ));
        }
    }

    private void mergeValues(Map<String, Double> target, Map<String, Double> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            target.merge(Texts.normalizeId(entry.getKey()), entry.getValue(), Double::sum);
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
            String key = Texts.normalizeId(entry.getKey());
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
                String id = Texts.normalizeId(contribution.attributeId());
                providerValues.merge(id, contribution.value(), Double::sum);
                target.merge(id, contribution.value(), Double::sum);
            }
            if (!providerValues.isEmpty()) {
                signatureParts.add(Texts.normalizeId(provider.id()) + ":" + SignatureUtil.stableSignature(providerValues));
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
        List<String> lore = ItemTextBridge.loreLines(itemMeta);
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
                String key = Texts.normalizeId(entry.getKey());
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
            String normalizedId = Texts.normalizeId(id);
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
            total += values.getOrDefault(Texts.normalizeId(id), 0D);
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
