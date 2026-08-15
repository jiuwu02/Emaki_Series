package emaki.jiuwu.craft.accessory.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.model.AccessoryContributionSnapshot;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.api.model.PdcAttributePayload;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPayload;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPdcCodec;

public final class AccessoryContributionService {

    public static final String SOURCE_ACCESSORY = "emakiaccessory:accessory";

    public static final String SOURCE_SET = "emakiaccessory:set";

    private final AccessorySetService setService;
    private final Supplier<DebugLogger> debugLoggerSupplier;
    private final Map<UUID, AccessoryContributionSnapshot> snapshots = new ConcurrentHashMap<>();
    private volatile AccessoryPartRegistry registry = AccessoryPartRegistry.empty();

    public AccessoryContributionService(AccessorySetService setService,
            Supplier<DebugLogger> debugLoggerSupplier) {
        this.setService = setService;
        this.debugLoggerSupplier = debugLoggerSupplier;
    }

    public void reconfigure(AccessoryPartRegistry registry) {
        this.registry = registry == null ? AccessoryPartRegistry.empty() : registry;
    }

    public AccessoryContributionSnapshot snapshot(UUID playerId) {
        if (playerId == null) {
            return AccessoryContributionSnapshot.empty();
        }
        AccessoryContributionSnapshot cached = snapshots.get(playerId);
        return cached == null ? AccessoryContributionSnapshot.empty() : cached;
    }

    public void invalidate(UUID playerId) {
        if (playerId != null) {
            snapshots.remove(playerId);
        }
    }

    public void invalidateAll() {
        snapshots.clear();
    }

    public AccessoryContributionSnapshot recompute(PlayerAccessories accessories) {
        if (accessories == null) {
            return AccessoryContributionSnapshot.empty();
        }
        Map<String, Double> attributes = new LinkedHashMap<>();
        Map<String, String> skills = new LinkedHashMap<>();

        for (String slotInstanceId : registry.slotInstanceIds()) {
            ItemStack item = accessories.itemAt(slotInstanceId);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            collectAttributes(accessories.playerId(), slotInstanceId, item, attributes);
            collectSkills(slotInstanceId, item, skills);
        }

        Map<String, Integer> setPieces = setService == null
                ? Map.of()
                : setService.countPieces(accessories, registry);
        if (setService != null) {
            setService.applyBonuses(setPieces, attributes, skills);
        }

        AccessoryContributionSnapshot snapshot =
                new AccessoryContributionSnapshot(attributes, skills, setPieces);
        snapshots.put(accessories.playerId(), snapshot);
        DebugLogger dl = debugLoggerSupplier == null ? null : debugLoggerSupplier.get();
        if (dl != null) {
            dl.log("accessory", accessories.playerId(), "accessory.recompute", Map.of(
                    "player", accessories.playerId().toString(),
                    "attributes", String.valueOf(attributes.size()),
                    "skills", String.valueOf(skills.size()),
                    "sets", String.valueOf(setPieces.size())));
        }
        return snapshot;
    }

    private void collectAttributes(UUID playerId,
            String slotInstanceId,
            ItemStack item,
            Map<String, Double> attributes) {
        if (!EmakiAttributeApi.status().usable()) {
            return;
        }
        if (!passesSlotGate(item, slotInstanceId)) {
            return;
        }
        AttributeSnapshot snapshot = EmakiAttributeApi.catalog().itemSnapshot(item).orElse(null);
        if (snapshot == null) {
            return;
        }

        snapshot.values().forEach((attributeId, value) -> {
            if (Texts.isNotBlank(attributeId) && value != null && Double.isFinite(value)) {
                attributes.merge(attributeId, value, Double::sum);
            }
        });
    }

    private boolean passesSlotGate(ItemStack item, String slotInstanceId) {
        Map<String, PdcAttributePayload> payloads = EmakiAttributeApi.extensions().pdc().readAll(item);
        for (PdcAttributePayload payload : payloads.values()) {
            if (payload == null) {
                continue;
            }
            String declared = payload.meta().get(EquipmentSlotMatcher.ACTIVE_SLOT_META_KEY);
            if (Texts.isBlank(declared)) {
                continue;
            }
            if (!AccessoryPartRegistry.matchesAccessorySlot(slotInstanceId, declared)) {
                return false;
            }
        }
        return true;
    }

    private void collectSkills(String slotInstanceId, ItemStack item, Map<String, String> skills) {
        if (!EquipmentSkillPdcCodec.hasPayload(item)) {
            return;
        }
        EquipmentSkillPayload payload = EquipmentSkillPdcCodec.read(item);
        if (payload == null || payload.empty()) {
            return;
        }
        if (!AccessoryPartRegistry.matchesAccessorySlot(slotInstanceId, payload.activeSlot())) {
            return;
        }
        for (String skillId : payload.skillIds()) {
            String normalized = Texts.normalizeId(skillId);
            if (Texts.isNotBlank(normalized)) {

                skills.putIfAbsent(normalized, slotInstanceId);
            }
        }
    }
}
