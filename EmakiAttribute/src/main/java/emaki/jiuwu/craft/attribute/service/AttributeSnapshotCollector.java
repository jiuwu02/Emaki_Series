package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.api.extension.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.extension.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.attribute.model.DamageStageDefinition;
import emaki.jiuwu.craft.attribute.model.DamageStageKind;
import emaki.jiuwu.craft.attribute.model.DamageStageMode;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class AttributeSnapshotCollector {

    private static final double ZERO_EPSILON = 1.0E-9D;
    private static final Set<AttributeValueKind> SKIP_FLAT_KINDS = EnumSet.of(
            AttributeValueKind.CHANCE,
            AttributeValueKind.REGEN,
            AttributeValueKind.DERIVED
    );

    private final AttributeService service;
    private final ScalingCurveProcessor scalingCurveProcessor = new ScalingCurveProcessor();
    private volatile FusionRuleCache fusionRuleCache = new FusionRuleCache("", List.of());

    AttributeSnapshotCollector(AttributeService service) {
        this.service = service;
    }

    public AttributeSnapshot collectItemSnapshot(ItemStack itemStack) {
        PdcAttributeService.PdcAttributeCollection rawPdcContribution = service.config().readPdcAttributes()
                ? service.pdcAttributeService().collectRawContribution(itemStack)
                : emptyPdcContribution();
        return collectItemSnapshot(itemStack, rawPdcContribution);
    }

    private AttributeSnapshot collectItemSnapshot(ItemStack itemStack,
            PdcAttributeService.PdcAttributeCollection rawPdcContribution) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return AttributeSnapshot.empty("");
        }
        boolean readLoreAttributes = service.config().readLoreAttributes();
        boolean readPdcAttributes = service.config().readPdcAttributes();
        LoreParser.ParsedLore parsedLore = readLoreAttributes ? parseLore(itemStack) : emptyParsedLore();
        PdcAttributeService.PdcAttributeCollection resolvedRawContribution = readPdcAttributes && rawPdcContribution != null
                ? rawPdcContribution
                : emptyPdcContribution();
        if (parsedLore.snapshot().values().isEmpty() && resolvedRawContribution.values().isEmpty()) {
            service.stateRepository().clearItemSnapshot(itemStack);
            return AttributeSnapshot.empty("");
        }
        String sourceSignature = SignatureUtil.combine(
                service.itemLoreSignatureVersion(),
                "read_lore=" + readLoreAttributes,
                "read_pdc=" + readPdcAttributes,
                "require_match=" + service.config().requireLorePdcMatch(),
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
        Map<String, Double> values = resolveItemSourceValues(
                parsedLore.snapshot().values(),
                resolvedRawContribution.values(),
                readLoreAttributes,
                readPdcAttributes,
                service.config().requireLorePdcMatch()
        );
        expandParentAttributeBonuses(values, service.registryService().attributeDefinitions());
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
        return collectCombatSnapshot(player, resolveSlotItems(player), player);
    }

    private AttributeSnapshot collectLivingCombatSnapshot(LivingEntity entity) {
        return collectCombatSnapshot(entity, resolveSlotItems(entity), null);
    }

    private Map<String, ItemStack> resolveSlotItems(LivingEntity entity) {
        AttributeSlotRegistry registry = service.slotRegistry();
        return registry == null ? Map.of() : registry.readSlots(entity);
    }

    private AttributeSnapshot collectCombatSnapshot(LivingEntity entity,
            Map<String, ItemStack> slotItems,
            Player playerOrNull) {
        List<String> signatureParts = new ArrayList<>();
        signatureParts.add("defaults:" + service.registryService().defaultProfilesSignature());
        signatureParts.add("attributes:" + service.registryService().attributeDefinitionsSignature());
        if (slotItems != null && !slotItems.isEmpty()) {
            collectEquipmentSignatures(slotItems, playerOrNull, signatureParts);
        }
        collectContributionProviderSignatures(entity, signatureParts);
        String temporarySignature = service.temporaryAttributeService().signature(entity);
        if (Texts.isNotBlank(temporarySignature)) {
            signatureParts.add("temporary:" + temporarySignature);
        }
        if (playerOrNull != null && service.parentAttributeService() != null) {
            String parentSignature = service.parentAttributeService().signature(playerOrNull);
            if (Texts.isNotBlank(parentSignature)) {
                signatureParts.add("parent_attributes:" + parentSignature);
            }
        }
        String sourceSignature = SignatureUtil.stableSignature(signatureParts);

        String cachedSignature = service.stateRepository().readCombatSourceSignature(entity);
        AttributeSnapshot cachedSnapshot = service.stateRepository().readCombatSnapshot(entity);
        if (sourceSignature.equals(cachedSignature)
                && cachedSnapshot != null
                && cachedSnapshot.schemaVersion() == AttributeFusionMath.RAW_COMBAT_SNAPSHOT_SCHEMA_VERSION) {
            return cachedSnapshot;
        }

        Map<String, Double> values = new LinkedHashMap<>();
        mergeValues(values, service.defaultAttributeValues());
        if (slotItems != null && !slotItems.isEmpty()) {
            collectEquipmentSnapshots(slotItems, playerOrNull, values);
        }
        if (playerOrNull != null && service.parentAttributeService() != null) {
            mergeValues(values, service.parentAttributeService().contributionValues(playerOrNull));
        }
        mergeContributionProviders(entity, values);
        mergeValues(values, service.temporaryAttributeService().additiveValues(entity));
        overlayValues(values, service.temporaryAttributeService().setValues(entity));
        scalingCurveProcessor.apply(values, service.scalingCurves());
        applyDerivedValues(values);
        AttributeSnapshot snapshot = new AttributeSnapshot(
                AttributeFusionMath.RAW_COMBAT_SNAPSHOT_SCHEMA_VERSION,
                sourceSignature,
                values,
                System.currentTimeMillis()
        );
        service.stateRepository().writeCombatSnapshot(entity, snapshot);
        return snapshot;
    }

    private void collectEquipmentSnapshots(Map<String, ItemStack> slotItems,
            Player player,
            Map<String, Double> values,
            List<String> signatureParts) {
        collectEquipment(slotItems, player, values, signatureParts);
    }

    private void collectEquipmentSignatures(Map<String, ItemStack> slotItems,
            Player playerOrNull,
            List<String> signatureParts) {
        collectEquipment(slotItems, playerOrNull, null, signatureParts);
    }

    private void collectEquipmentSnapshots(Map<String, ItemStack> slotItems,
            Player playerOrNull,
            Map<String, Double> values) {
        collectEquipment(slotItems, playerOrNull, values, null);
    }

    private void collectEquipment(Map<String, ItemStack> slotItems,
            Player playerOrNull,
            Map<String, Double> values,
            List<String> signatureParts) {
        if (slotItems == null || slotItems.isEmpty()) {
            return;
        }
        boolean collectValues = values != null;
        boolean collectSignatures = signatureParts != null;
        if (!collectValues && !collectSignatures) {
            return;
        }
        for (Map.Entry<String, ItemStack> slotEntry : slotItems.entrySet()) {
            String slotName = slotEntry.getKey();
            ItemStack itemStack = slotEntry.getValue();
            PdcAttributeService.PdcAttributeViews views = !service.config().readPdcAttributes()
                    ? null
                    : service.pdcAttributeService().collectContributionViews(playerOrNull, itemStack, slotName);
            AttributeSnapshot itemSnapshot = views == null
                    ? collectItemSnapshot(itemStack)
                    : collectItemSnapshot(itemStack, views.raw());
            if (itemSnapshot == null) {
                continue;
            }
            if (views == null) {
                String rejectingGateId = service.pdcAttributeService()
                        .resolveRejectingGateId(playerOrNull, itemStack, slotName);
                boolean gateActive = rejectingGateId.isEmpty();
                if (collectValues && gateActive) {
                    mergeValues(values, itemSnapshot.values());
                } else if (collectValues) {
                    debugItemConditionGate(playerOrNull, slotName, rejectingGateId);
                }
                addEquipmentSignature(
                        signatureParts,
                        slotName,
                        itemSnapshot.sourceSignature(),
                        null,
                        collectSignatures,
                        rejectingGateId
                );
                continue;
            }
            if (collectValues) {
                Map<String, Double> effectiveValues = itemSnapshot.values();
                if (!views.itemContributionActive()) {
                    debugItemConditionGate(playerOrNull, slotName, views.rejectingGateId());
                    effectiveValues = resolveEquipmentItemValues(
                            Map.of(),
                            Map.of(),
                            service.config().readLoreAttributes(),
                            service.config().readPdcAttributes(),
                            service.config().requireLorePdcMatch(),
                            false
                    );
                } else if (views.hasExplicitSlotConstraint() && !views.itemSlotMatched()) {
                    debugAttributeSlotGate(playerOrNull, slotName, views.declaredSlots());
                    effectiveValues = resolveEquipmentItemValues(
                            Map.of(),
                            Map.of(),
                            service.config().readLoreAttributes(),
                            service.config().readPdcAttributes(),
                            service.config().requireLorePdcMatch(),
                            false
                    );
                } else if (!views.raw().values().equals(views.filtered().values())) {
                    LoreParser.ParsedLore parsedLore = service.config().readLoreAttributes()
                            ? parseLore(itemStack)
                            : emptyParsedLore();
                    effectiveValues = resolveEquipmentItemValues(
                            parsedLore.snapshot().values(),
                            views.filtered().values(),
                            service.config().readLoreAttributes(),
                            service.config().readPdcAttributes(),
                            service.config().requireLorePdcMatch(),
                            true
                    );
                    expandParentAttributeBonuses(effectiveValues, service.registryService().attributeDefinitions());
                }
                mergeValues(values, effectiveValues);
            }
            addEquipmentSignature(
                    signatureParts,
                    slotName,
                    itemSnapshot.sourceSignature(),
                    views,
                    collectSignatures,
                    views.rejectingGateId()
            );
        }
    }

    private void addEquipmentSignature(List<String> signatureParts,
            String slotName,
            String itemSignature,
            PdcAttributeService.PdcAttributeViews views,
            boolean collectSignatures,
            String rejectingGateId) {
        if (!collectSignatures) {
            return;
        }
        String gatePart = "condition_gate=" + Texts.toStringSafe(rejectingGateId);
        if (views == null) {
            signatureParts.add(slotName + ":" + SignatureUtil.combine(
                    itemSignature,
                    gatePart
            ));
            return;
        }
        signatureParts.add(slotName + ":" + SignatureUtil.combine(
                itemSignature,
                views.filtered().sourceSignature(),
                "declared_slots=" + String.join(",", views.declaredSlots()),
                "item_slot_matched=" + views.itemSlotMatched(),
                gatePart
        ));
    }

    static void expandParentAttributeBonuses(Map<String, Double> values,
            Collection<AttributeDefinition> definitions) {
        if (values == null || values.isEmpty() || definitions == null || definitions.isEmpty()) {
            return;
        }
        for (AttributeDefinition definition : definitions) {
            if (definition == null || !definition.parentAttribute() || definition.childBonuses().isEmpty()) {
                continue;
            }
            String parentId = Texts.normalizeId(definition.id());
            Double parentValue = values.get(parentId);
            if (parentValue == null) {
                continue;
            }
            double parentSpread = values.getOrDefault(AttributeSnapshot.rangeSpreadKey(parentId), 0D);
            double parentUpper = parentValue + parentSpread;
            for (Map.Entry<String, Double> entry : definition.childBonuses().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String childId = Texts.normalizeId(entry.getKey());
                double multiplier = entry.getValue();
                if (childId.isBlank() || Math.abs(multiplier) <= ZERO_EPSILON) {
                    continue;
                }
                double scaledLower = parentValue * multiplier;
                double scaledUpper = parentUpper * multiplier;
                double childLower = Math.min(scaledLower, scaledUpper);
                double childSpread = Math.abs(scaledUpper - scaledLower);
                if (Math.abs(childLower) > ZERO_EPSILON) {
                    values.merge(childId, childLower, Double::sum);
                }
                if (childSpread > ZERO_EPSILON) {
                    values.merge(AttributeSnapshot.rangeSpreadKey(childId), childSpread, Double::sum);
                }
            }
        }
    }

    static Map<String, Double> resolveEquipmentItemValues(Map<String, Double> loreValues,
            Map<String, Double> pdcValues,
            boolean readLoreAttributes,
            boolean readPdcAttributes,
            boolean requireLorePdcMatch,
            boolean itemSlotMatched) {
        if (!itemSlotMatched) {
            return new LinkedHashMap<>();
        }
        return resolveItemSourceValues(
                loreValues,
                pdcValues,
                readLoreAttributes,
                readPdcAttributes,
                requireLorePdcMatch
        );
    }

    static Map<String, Double> resolveItemSourceValues(Map<String, Double> loreValues,
            Map<String, Double> pdcValues,
            boolean readLoreAttributes,
            boolean readPdcAttributes,
            boolean requireLorePdcMatch) {
        Map<String, Double> normalizedLore = readLoreAttributes ? normalizeValues(loreValues) : Map.of();
        Map<String, Double> normalizedPdc = readPdcAttributes ? normalizeValues(pdcValues) : Map.of();
        if (requireLorePdcMatch) {
            if (!readLoreAttributes || !readPdcAttributes) {
                return new LinkedHashMap<>();
            }
            return matchItemSourceValues(normalizedLore, normalizedPdc);
        }
        Map<String, Double> values = new LinkedHashMap<>();
        mergeNormalizedValues(values, normalizedLore);
        mergeNormalizedValues(values, normalizedPdc);
        return values;
    }

    private static Map<String, Double> matchItemSourceValues(Map<String, Double> loreValues,
            Map<String, Double> pdcValues) {
        Map<String, Double> matched = new LinkedHashMap<>();
        Set<String> attributeIds = new LinkedHashSet<>();
        collectBaseAttributeIds(attributeIds, loreValues);
        collectBaseAttributeIds(attributeIds, pdcValues);
        for (String attributeId : attributeIds) {
            Double loreValue = loreValues.get(attributeId);
            Double pdcValue = pdcValues.get(attributeId);
            if (loreValue == null || pdcValue == null || !sameValue(loreValue, pdcValue)) {
                continue;
            }
            String spreadKey = AttributeSnapshot.rangeSpreadKey(attributeId);
            double loreSpread = loreValues.getOrDefault(spreadKey, 0D);
            double pdcSpread = pdcValues.getOrDefault(spreadKey, 0D);
            if (!sameValue(loreSpread, pdcSpread)) {
                continue;
            }
            matched.put(attributeId, loreValue);
            if (Math.abs(loreSpread) > ZERO_EPSILON) {
                matched.put(spreadKey, loreSpread);
            }
        }
        return matched;
    }

    private static void collectBaseAttributeIds(Set<String> target, Map<String, Double> values) {
        if (target == null || values == null || values.isEmpty()) {
            return;
        }
        for (String key : values.keySet()) {
            if (Texts.isNotBlank(key) && !AttributeSnapshot.isRangeSpreadKey(key)) {
                target.add(Texts.normalizeId(key));
            }
        }
    }

    private static Map<String, Double> normalizeValues(Map<String, Double> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.merge(Texts.normalizeId(entry.getKey()), entry.getValue(), Double::sum);
        }
        return normalized;
    }

    private static boolean sameValue(double left, double right) {
        return Math.abs(left - right) <= ZERO_EPSILON;
    }

    private void mergeValues(Map<String, Double> target, Map<String, Double> source) {
        mergeNormalizedValues(target, normalizeValues(source));
    }

    private static void mergeNormalizedValues(Map<String, Double> target, Map<String, Double> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Double::sum);
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
                if (!AttributeSnapshot.isRangeSpreadKey(key)) {
                    target.remove(AttributeSnapshot.rangeSpreadKey(key));
                }
                continue;
            }
            target.put(key, value);
            if (!AttributeSnapshot.isRangeSpreadKey(key)
                    && !source.containsKey(AttributeSnapshot.rangeSpreadKey(entry.getKey()))
                    && !source.containsKey(AttributeSnapshot.rangeSpreadKey(key))) {
                target.remove(AttributeSnapshot.rangeSpreadKey(key));
            }
        }
    }

    private void mergeContributionProviders(LivingEntity entity,
            Map<String, Double> target,
            List<String> signatureParts) {
        collectContributionProviders(entity, target, signatureParts);
    }

    private void collectContributionProviderSignatures(LivingEntity entity,
            List<String> signatureParts) {
        collectContributionProviders(entity, null, signatureParts);
    }

    private void mergeContributionProviders(LivingEntity entity,
            Map<String, Double> target) {
        collectContributionProviders(entity, target, null);
    }

    private void collectContributionProviders(LivingEntity entity,
            Map<String, Double> target,
            List<String> signatureParts) {
        if (entity == null) {
            return;
        }
        boolean mergeToTarget = target != null;
        boolean collectSignatures = signatureParts != null;
        if (!mergeToTarget && !collectSignatures) {
            return;
        }
        List<AttributeContributionProvider> providers = service.registryService().orderedContributionProviders();
        for (AttributeContributionProvider provider : providers) {
            Collection<AttributeContribution> contributions = provider.collect(entity);
            if (contributions == null || contributions.isEmpty()) {
                continue;
            }
            Map<String, Double> providerValues = collectProviderValues(
                    contributions,
                    target,
                    mergeToTarget,
                    collectSignatures
            );
            if (providerValues != null && !providerValues.isEmpty()) {
                signatureParts.add(Texts.normalizeId(provider.id()) + ":" + SignatureUtil.stableSignature(providerValues));
            }
        }
    }

    private Map<String, Double> collectProviderValues(Collection<AttributeContribution> contributions,
            Map<String, Double> target,
            boolean mergeToTarget,
            boolean collectSignatures) {
        Map<String, Double> providerValues = collectSignatures ? new LinkedHashMap<>() : null;
        for (AttributeContribution contribution : contributions) {
            if (contribution == null || contribution.attributeId() == null || contribution.attributeId().isBlank()) {
                continue;
            }
            String id = Texts.normalizeId(contribution.attributeId());
            double value = contribution.value();
            if (providerValues != null) {
                providerValues.merge(id, value, Double::sum);
            }
            if (mergeToTarget) {
                target.merge(id, value, Double::sum);
            }
        }
        return providerValues;
    }

    private void applyDerivedValues(Map<String, Double> values) {
        if (values == null) {
            return;
        }
        for (var provider : service.registryService().orderedDerivedAttributeProviders()) {
            if (provider == null) {
                continue;
            }
            String attributeId = provider.attributeId();
            if (attributeId == null || attributeId.isBlank()) {
                continue;
            }
            double derivedValue = provider.compute(values);
            values.put(attributeId, derivedValue);
        }
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
                String spreadKey = AttributeSnapshot.rangeSpreadKey(flatId);
                Double rawSpread = values.get(spreadKey);
                double effectiveValue;
                if (rawSpread != null && rawSpread > ZERO_EPSILON) {
                    double minEndpoint = AttributeFusionMath.toEffectiveFlat(rawValue, percentBonus, rule.clampPercentFactor());
                    double maxEndpoint = AttributeFusionMath.toEffectiveFlat(rawValue + rawSpread, percentBonus, rule.clampPercentFactor());
                    effectiveValue = Math.min(minEndpoint, maxEndpoint);
                    double effectiveSpread = Math.abs(maxEndpoint - minEndpoint);
                    if (effectiveSpread <= ZERO_EPSILON) {
                        values.remove(spreadKey);
                    } else {
                        values.put(spreadKey, effectiveSpread);
                    }
                } else {
                    effectiveValue = AttributeFusionMath.toEffectiveFlat(rawValue, percentBonus, rule.clampPercentFactor());
                    values.remove(spreadKey);
                }
                if (Math.abs(effectiveValue) <= ZERO_EPSILON) {
                    if (values.containsKey(spreadKey)) {
                        values.put(flatId, 0D);
                    } else {
                        values.remove(flatId);
                    }
                    continue;
                }
                values.put(flatId, effectiveValue);
            }
        }
    }

    private LoreParser.ParsedLore parseLore(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return emptyParsedLore();
        }
        var itemMeta = itemStack.getItemMeta();
        if (itemMeta == null || !itemMeta.hasLore()) {
            return emptyParsedLore();
        }
        List<String> lore = ItemTextBridge.loreLines(itemMeta);
        if (lore == null || lore.isEmpty()) {
            return emptyParsedLore();
        }
        return service.loreParser().parse(lore);
    }

    private static LoreParser.ParsedLore emptyParsedLore() {
        return new LoreParser.ParsedLore(
                AttributeSnapshot.empty(SignatureUtil.stableSignature(List.of())),
                List.of()
        );
    }

    private static PdcAttributeService.PdcAttributeCollection emptyPdcContribution() {
        return new PdcAttributeService.PdcAttributeCollection(Map.of(), "");
    }

    private void debugAttributeSlotGate(Player player, String actualSlot, List<String> declaredSlots) {
        if (player == null || service.plugin() == null || service.plugin().debugLogger() == null) {
            return;
        }
        service.plugin().debugLogger().log("resync", player, "resync.slot_gate", Map.of(
                "actual_slot", Texts.toStringSafe(actualSlot),
                "declared_slots", String.join(",", declaredSlots == null ? List.of() : declaredSlots)
        ));
    }

    private void debugItemConditionGate(Player player, String actualSlot, String rejectingGateId) {
        if (player == null || service.plugin() == null || service.plugin().debugLogger() == null) {
            return;
        }
        service.plugin().debugLogger().log("resync", player, "resync.condition_gate", Map.of(
                "actual_slot", Texts.toStringSafe(actualSlot),
                "gate", Texts.toStringSafe(rejectingGateId)
        ));
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
            if (SKIP_FLAT_KINDS.contains(definition.valueKind())) {
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
