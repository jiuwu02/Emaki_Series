package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.attribute.api.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.model.AttributeContributionTrace;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.AttributeSourceTraceReport;
import emaki.jiuwu.craft.attribute.model.ParentAttributeData;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class AttributeTraceService {

    private static final String[] EQUIPMENT_SLOT_NAMES = {
            "main_hand",
            "off_hand",
            "helmet",
            "chestplate",
            "leggings",
            "boots"
    };

    private final AttributeService service;

    AttributeTraceService(AttributeService service) {
        this.service = service;
    }

    public AttributeSourceTraceReport trace(Player player, String attributeFilter) {
        if (player == null) {
            return new AttributeSourceTraceReport(null, "", System.currentTimeMillis(), AttributeSnapshot.empty(""), List.of(), Map.of());
        }
        String filter = Texts.normalizeId(attributeFilter);
        Map<String, Double> reconstructed = new LinkedHashMap<>();
        List<AttributeContributionTrace> traces = new ArrayList<>();
        addDefaultProfileContributions(reconstructed, traces, filter);
        addEquipmentContributions(player, reconstructed, traces, filter);
        addParentAttributeContributions(player, reconstructed, traces, filter);
        addProviderContributions(player, reconstructed, traces, filter);
        addTemporaryContributions(player, reconstructed, traces, filter);
        AttributeSnapshot finalSnapshot = service.collectCombatSnapshot(player);
        addDerivedOrAdjustedContributions(reconstructed, finalSnapshot.values(), traces, filter);
        return new AttributeSourceTraceReport(
                player.getUniqueId(),
                player.getName(),
                System.currentTimeMillis(),
                finalSnapshot,
                traces,
                resourceSummary(player)
        );
    }

    private void addDefaultProfileContributions(Map<String, Double> reconstructed,
            List<AttributeContributionTrace> traces,
            String filter) {
        for (Map.Entry<String, Double> entry : service.defaultAttributeValues().entrySet()) {
            addTrace(reconstructed, traces, entry.getKey(), entry.getValue(), "EmakiAttribute", "base_profile", "default_profile", "默认属性档案", "", "", "", true, "", filter);
        }
    }

    private void addEquipmentContributions(Player player,
            Map<String, Double> reconstructed,
            List<AttributeContributionTrace> traces,
            String filter) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] items = {
                inventory.getItemInMainHand(),
                inventory.getItemInOffHand(),
                inventory.getHelmet(),
                inventory.getChestplate(),
                inventory.getLeggings(),
                inventory.getBoots()
        };
        for (int index = 0; index < items.length; index++) {
            ItemStack item = items[index];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String slot = EQUIPMENT_SLOT_NAMES[index];
            String label = itemLabel(item);
            PdcAttributeService.PdcAttributeViews views = service.pdcAttributeService().collectContributionViews(player, item, slot);
            AttributeSnapshot itemSnapshot = service.collectItemSnapshot(item);
            Map<String, Double> rawPdc = views.raw().values();
            Map<String, Double> filteredPdc = views.filtered().values();
            Map<String, Double> loreApprox = subtract(itemSnapshot.values(), rawPdc);
            boolean itemSlotMatched = views.itemSlotMatched();
            for (Map.Entry<String, Double> entry : loreApprox.entrySet()) {
                if (itemSlotMatched) {
                    addTrace(reconstructed, traces, entry.getKey(), entry.getValue(), "EmakiAttribute", "lore", slot, label + " / Lore", slot, "", "lore", true, "", filter);
                } else {
                    addRejectedTrace(traces, entry.getKey(), entry.getValue(), "lore", slot, label + " / Lore（槽位未通过）", "lore", filter);
                }
            }
            for (Map.Entry<String, Double> entry : rawPdc.entrySet()) {
                String id = Texts.normalizeId(entry.getKey());
                double raw = entry.getValue() == null ? 0D : entry.getValue();
                double effective = filteredPdc.getOrDefault(id, 0D);
                boolean passed = itemSlotMatched && Math.abs(effective) > 1.0E-9D;
                if (passed) {
                    addTrace(reconstructed, traces, id, effective, "EmakiAttribute", "pdc", slot, label + " / PDC", slot, "", "pdc", true, "", filter, raw, effective);
                } else {
                    String rejectedLabel = itemSlotMatched
                            ? label + " / PDC（条件未通过）"
                            : label + " / PDC（槽位未通过）";
                    addRejectedTrace(traces, id, raw, "pdc", slot, rejectedLabel, "pdc", filter);
                }
            }
        }
    }

    private void addParentAttributeContributions(Player player,
            Map<String, Double> reconstructed,
            List<AttributeContributionTrace> traces,
            String filter) {
        if (service.parentAttributeService() == null) {
            return;
        }
        ParentAttributeData data = service.parentAttributeService().data(player);
        if (data == null || data.allocations().isEmpty()) {
            return;
        }
        for (AttributeDefinition parent : service.parentAttributeService().parentAttributes()) {
            int points = data.allocation(parent.id());
            if (points <= 0) {
                continue;
            }
            addTrace(reconstructed, traces, parent.id(), (double) points, "EmakiAttribute", "parent_attribute", parent.id(), parent.displayName() + " 加点", "", "", "parent_attribute", true, "points", filter);
            for (Map.Entry<String, Double> bonus : parent.childBonuses().entrySet()) {
                if (bonus.getValue() == null) {
                    continue;
                }
                double value = points * bonus.getValue();
                addTrace(reconstructed, traces, bonus.getKey(), value, "EmakiAttribute", "parent_attribute", parent.id(), parent.displayName() + " 加点", "", "", "parent_attribute", true, parent.id() + " * " + bonus.getValue(), filter, bonus.getValue(), value);
            }
        }
    }

    private void addProviderContributions(LivingEntity entity,
            Map<String, Double> reconstructed,
            List<AttributeContributionTrace> traces,
            String filter) {
        for (AttributeContributionProvider provider : service.registryService().orderedContributionProviders()) {
            if (provider == null) {
                continue;
            }
            Collection<AttributeContribution> contributions = provider.collect(entity);
            if (contributions == null || contributions.isEmpty()) {
                continue;
            }
            String providerId = Texts.normalizeId(provider.id());
            for (AttributeContribution contribution : contributions) {
                if (contribution == null) {
                    continue;
                }
                addTrace(reconstructed, traces, contribution.attributeId(), contribution.value(), providerId, "external", providerId, provider.id(), "", "", "provider", true, "", filter);
            }
        }
    }

    private void addTemporaryContributions(Player player,
            Map<String, Double> reconstructed,
            List<AttributeContributionTrace> traces,
            String filter) {
        for (Map.Entry<String, Double> entry : service.temporaryAttributeService().additiveValues(player).entrySet()) {
            addTrace(reconstructed, traces, entry.getKey(), entry.getValue(), "EmakiAttribute", "temporary", "additive", "临时属性 ADD", "", "", "temporary", true, "", filter);
        }
        for (Map.Entry<String, Double> entry : service.temporaryAttributeService().setValues(player).entrySet()) {
            String id = Texts.normalizeId(entry.getKey());
            double before = reconstructed.getOrDefault(id, 0D);
            double after = entry.getValue() == null ? 0D : entry.getValue();
            reconstructed.put(id, after);
            if (matches(filter, id)) {
                traces.add(new AttributeContributionTrace(id, after - before, "EmakiAttribute", "temporary", "set", "临时属性 SET", "", "", "temporary", true, "", before, after));
            }
        }
    }

    private void addDerivedOrAdjustedContributions(Map<String, Double> reconstructed,
            Map<String, Double> finalValues,
            List<AttributeContributionTrace> traces,
            String filter) {
        Map<String, Double> all = new LinkedHashMap<>();
        all.putAll(reconstructed);
        if (finalValues != null) {
            all.putAll(finalValues);
        }
        for (String id : all.keySet()) {
            if (!matches(filter, id)) {
                continue;
            }
            double before = reconstructed.getOrDefault(id, 0D);
            double after = finalValues == null ? 0D : finalValues.getOrDefault(id, 0D);
            double delta = after - before;
            if (Math.abs(delta) <= 1.0E-9D) {
                continue;
            }
            traces.add(new AttributeContributionTrace(id, delta, "EmakiAttribute", "derived", "post_process", "衰减曲线 / 派生属性", "", "", "derived", true, "", before, after));
        }
    }

    private Map<String, Object> resourceSummary(Player player) {
        Map<String, Object> resources = new LinkedHashMap<>();
        service.resourceDefinitions().forEach((id, definition) -> {
            ResourceState state = service.readResourceState(player, id);
            if (state == null) {
                return;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("defaultMax", state.defaultMax());
            data.put("bonusMax", state.bonusMax());
            data.put("currentMax", state.currentMax());
            data.put("current", state.currentValue());
            resources.put(id, data);
        });
        return resources;
    }

    private void addTrace(Map<String, Double> reconstructed,
            List<AttributeContributionTrace> traces,
            String attributeId,
            Double value,
            String sourceModule,
            String sourceType,
            String sourceId,
            String sourceLabel,
            String slot,
            String itemId,
            String layer,
            boolean conditionPassed,
            String formula,
            String filter) {
        double safeValue = value == null ? 0D : value;
        addTrace(reconstructed, traces, attributeId, safeValue, sourceModule, sourceType, sourceId, sourceLabel, slot, itemId, layer, conditionPassed, formula, filter, 0D, safeValue);
    }

    private void addTrace(Map<String, Double> reconstructed,
            List<AttributeContributionTrace> traces,
            String attributeId,
            double value,
            String sourceModule,
            String sourceType,
            String sourceId,
            String sourceLabel,
            String slot,
            String itemId,
            String layer,
            boolean conditionPassed,
            String formula,
            String filter,
            double rawValue,
            double finalValue) {
        String id = Texts.normalizeId(attributeId);
        if (Texts.isBlank(id) || Math.abs(value) <= 1.0E-9D) {
            return;
        }
        reconstructed.merge(id, value, Double::sum);
        if (matches(filter, id)) {
            traces.add(new AttributeContributionTrace(id, value, sourceModule, sourceType, sourceId, sourceLabel, slot, itemId, layer, conditionPassed, formula, rawValue, finalValue));
        }
    }

    private void addRejectedTrace(List<AttributeContributionTrace> traces,
            String attributeId,
            Double rawValue,
            String sourceType,
            String slot,
            String sourceLabel,
            String layer,
            String filter) {
        String id = Texts.normalizeId(attributeId);
        if (Texts.isBlank(id) || !matches(filter, id)) {
            return;
        }
        double raw = rawValue == null ? 0D : rawValue;
        traces.add(new AttributeContributionTrace(
                id,
                0D,
                "EmakiAttribute",
                sourceType,
                slot,
                sourceLabel,
                slot,
                "",
                layer,
                false,
                "",
                raw,
                0D
        ));
    }

    private Map<String, Double> subtract(Map<String, Double> left, Map<String, Double> right) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (left != null) {
            left.forEach((key, value) -> {
                if (key != null && value != null) {
                    result.put(Texts.normalizeId(key), value);
                }
            });
        }
        if (right != null) {
            right.forEach((key, value) -> {
                if (key != null && value != null) {
                    result.merge(Texts.normalizeId(key), -value, Double::sum);
                }
            });
        }
        result.entrySet().removeIf(entry -> Math.abs(entry.getValue()) <= 1.0E-9D);
        return result;
    }

    private boolean matches(String filter, String attributeId) {
        return Texts.isBlank(filter) || Texts.normalizeId(filter).equals(Texts.normalizeId(attributeId));
    }

    private String itemLabel(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "AIR";
        }
        String name = ItemTextBridge.effectiveNamePlain(itemStack);
        return Texts.isBlank(name) ? itemStack.getType().name() : name;
    }
}
