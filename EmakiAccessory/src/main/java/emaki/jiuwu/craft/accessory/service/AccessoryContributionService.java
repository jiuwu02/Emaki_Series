package emaki.jiuwu.craft.accessory.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.model.AccessoryContributionSnapshot;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.api.model.PdcAttributePayload;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPayload;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPdcCodec;

/**
 * Builds and caches each player's accessory attribute and skill contributions.
 *
 * <p>Recomputation happens when accessory contents change - a GUI edit, a completed load, a reload -
 * and never inside the provider callbacks. That split is forced by EmakiAttribute: its combat snapshot
 * collector calls the contribution provider while computing its cache signature, before deciding
 * whether its own cache hit applies, so the provider runs on every combat snapshot read. Parsing PDC
 * there would put item deserialisation on the combat hot path.
 *
 * <p>Attribute values are read through {@code AttributeCatalog#itemSnapshot}, the same parser the
 * equipment path uses, so an accessory and a helmet with identical data produce identical numbers.
 * Parent-attribute expansion and range-spread companion keys come along with it.
 *
 * <p>The slot gate is reimplemented here on purpose. {@code itemSnapshot} performs no slot filtering,
 * and the equipment path's own gate is internal to EmakiAttribute. This class therefore reads the
 * {@code active_slot} metadata itself and applies the accessory matching rule, which additionally
 * accepts a bare part id. If the equipment-side gate rules ever change, this copy must be updated to
 * match - that is the known cost of not changing EmakiAttribute.
 */
public final class AccessoryContributionService {

    /** Source id reported to EmakiAttribute for single-accessory contributions. */
    public static final String SOURCE_ACCESSORY = "emakiaccessory:accessory";

    /** Source id reported to EmakiAttribute for accessory set bonuses. */
    public static final String SOURCE_SET = "emakiaccessory:set";

    private final AccessorySetService setService;
    private final Map<UUID, AccessoryContributionSnapshot> snapshots = new ConcurrentHashMap<>();
    private volatile AccessoryPartRegistry registry = AccessoryPartRegistry.empty();

    /**
     * Creates the service.
     *
     * @param setService the accessory set evaluator whose bonuses are folded in
     */
    public AccessoryContributionService(AccessorySetService setService) {
        this.setService = setService;
    }

    /**
     * Applies a new part configuration.
     *
     * @param registry the active part configuration
     */
    public void reconfigure(AccessoryPartRegistry registry) {
        this.registry = registry == null ? AccessoryPartRegistry.empty() : registry;
    }

    /**
     * {@return the cached snapshot for a player; never {@code null}}
     *
     * <p>This is what the provider callbacks read. It is a plain map lookup with no parsing, no IO and
     * no allocation beyond the lookup itself.
     *
     * @param playerId the player id
     */
    public AccessoryContributionSnapshot snapshot(UUID playerId) {
        if (playerId == null) {
            return AccessoryContributionSnapshot.empty();
        }
        AccessoryContributionSnapshot cached = snapshots.get(playerId);
        return cached == null ? AccessoryContributionSnapshot.empty() : cached;
    }

    /**
     * Drops a player's cached snapshot.
     *
     * @param playerId the player id
     */
    public void invalidate(UUID playerId) {
        if (playerId != null) {
            snapshots.remove(playerId);
        }
    }

    /** Drops every cached snapshot, for a reload that changes part or set definitions. */
    public void invalidateAll() {
        snapshots.clear();
    }

    /**
     * Recomputes and caches one player's contributions.
     *
     * <p>Must run on the owner thread of whoever holds these items: {@code itemSnapshot} writes its
     * parse result back into the item's PDC as a cache, so it is not safe to call asynchronously.
     *
     * @param accessories the player's current contents; {@code null} clears the snapshot
     * @return the freshly cached snapshot
     */
    public AccessoryContributionSnapshot recompute(PlayerAccessories accessories) {
        if (accessories == null) {
            return AccessoryContributionSnapshot.empty();
        }
        Map<String, Double> attributes = new LinkedHashMap<>();
        Map<String, String> skills = new LinkedHashMap<>();

        // Iterate the configured slots rather than the stored keys: an orphaned key is then skipped by
        // construction instead of by a check somebody has to remember to write.
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
        // Range-spread companion keys ride along in the same map and must be summed like any other key,
        // otherwise a ranged accessory would keep its lower bound and lose its spread.
        snapshot.values().forEach((attributeId, value) -> {
            if (Texts.isNotBlank(attributeId) && value != null && Double.isFinite(value)) {
                attributes.merge(attributeId, value, Double::sum);
            }
        });
    }

    /**
     * Applies the item's own slot restriction.
     *
     * <p>An item with no declaration fits anywhere. An item that declares one or more slots must have
     * every declaration satisfied by this cell, matching the equipment path's behaviour of dropping the
     * whole item's contribution when a declared slot does not match.
     */
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
                // First slot wins, mirroring how EmakiSkills de-duplicates unlocked skills by id.
                skills.putIfAbsent(normalized, slotInstanceId);
            }
        }
    }
}
