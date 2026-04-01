package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.attribute.api.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import net.kyori.adventure.text.Component;

final class AttributeSnapshotCollector {

    private final AttributeService service;

    AttributeSnapshotCollector(AttributeService service) {
        this.service = service;
    }

    public AttributeSnapshot collectItemSnapshot(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return AttributeSnapshot.empty("");
        }
        var itemMeta = itemStack.getItemMeta();
        if (itemMeta == null || !itemMeta.hasLore()) {
            service.stateRepositoryInternal().clearItemSnapshot(itemStack);
            return AttributeSnapshot.empty("");
        }
        List<Component> lore = itemMeta.lore();
        List<String> normalizedLore = service.loreParser().normalizeLore(lore);
        if (normalizedLore.isEmpty()) {
            service.stateRepositoryInternal().clearItemSnapshot(itemStack);
            return AttributeSnapshot.empty(SignatureUtil.stableSignature(List.of()));
        }
        String sourceSignature = SignatureUtil.combine(
                service.itemLoreSignatureVersion(),
                SignatureUtil.stableSignature(normalizedLore),
                service.attributeDefinitionsSignatureInternal()
        );
        String cachedSignature = service.stateRepositoryInternal().readItemSourceSignature(itemStack);
        AttributeSnapshot cachedSnapshot = service.stateRepositoryInternal().readItemSnapshot(itemStack);
        if (sourceSignature.equals(cachedSignature) && cachedSnapshot != null) {
            return cachedSnapshot;
        }
        LoreParser.ParsedLore parsedLore = service.loreParser().parse(lore);
        AttributeSnapshot snapshot = parsedLore.snapshot();
        if (!sourceSignature.equals(snapshot.sourceSignature())) {
            snapshot = new AttributeSnapshot(
                    snapshot.schemaVersion(),
                    sourceSignature,
                    snapshot.values(),
                    snapshot.updatedAt()
            );
        }
        service.stateRepositoryInternal().writeItemSnapshot(itemStack, snapshot);
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
        mergeValues(values, service.defaultAttributeValuesInternal());
        signatureParts.add("defaults:" + service.defaultProfilesSignatureInternal());
        signatureParts.add("attributes:" + service.attributeDefinitionsSignatureInternal());
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
        String cachedSignature = service.stateRepositoryInternal().readCombatSourceSignature(player);
        AttributeSnapshot cachedSnapshot = service.stateRepositoryInternal().readCombatSnapshot(player);
        if (sourceSignature.equals(cachedSignature) && cachedSnapshot != null) {
            return cachedSnapshot;
        }
        service.stateRepositoryInternal().writeCombatSnapshot(player, snapshot);
        return snapshot;
    }

    private AttributeSnapshot collectLivingCombatSnapshot(LivingEntity entity) {
        Map<String, Double> values = new LinkedHashMap<>();
        List<String> signatureParts = new ArrayList<>();
        mergeValues(values, service.defaultAttributeValuesInternal());
        signatureParts.add("defaults:" + service.defaultProfilesSignatureInternal());
        signatureParts.add("attributes:" + service.attributeDefinitionsSignatureInternal());
        AttributeSnapshot cached = service.stateRepositoryInternal().readCombatSnapshot(entity);
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
        String cachedSignature = service.stateRepositoryInternal().readCombatSourceSignature(entity);
        if (sourceSignature.equals(cachedSignature) && cached != null) {
            return cached;
        }
        service.stateRepositoryInternal().writeCombatSnapshot(entity, snapshot);
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

    private void mergeContributionProviders(LivingEntity entity,
            Map<String, Double> target,
            List<String> signatureParts) {
        if (entity == null) {
            return;
        }
        List<AttributeContributionProvider> providers = service.orderedContributionProvidersInternal();
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
        values.put("attribute_power", computeAttributePower(values));
    }

    private double computeAttributePower(Map<String, Double> values) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (AttributeDefinition definition : service.attributeDefinitionsInternal()) {
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
}
