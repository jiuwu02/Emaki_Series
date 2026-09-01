package emaki.jiuwu.craft.accessory.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.config.AccessorySlotSourceConfig;
import emaki.jiuwu.craft.accessory.model.AccessoryContributionSnapshot;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;

public final class AccessoryContributionService {

    public static final String SOURCE_ACCESSORY = "emakiaccessory:accessory";

    public static final String SOURCE_SET = "emakiaccessory:set";

    private final AccessorySetService setService;
    private final Supplier<DebugLogger> debugLoggerSupplier;
    private final Map<UUID, AccessoryContributionSnapshot> snapshots = new ConcurrentHashMap<>();
    private volatile AccessoryPartRegistry registry = AccessoryPartRegistry.empty();
    private volatile AccessoryPageRegistry pageRegistry = AccessoryPageRegistry.empty();
    private volatile AccessorySlotSourceConfig slotSources = AccessorySlotSourceConfig.defaults();

    public AccessoryContributionService(AccessorySetService setService,
            Supplier<DebugLogger> debugLoggerSupplier) {
        this.setService = setService;
        this.debugLoggerSupplier = debugLoggerSupplier;
    }

    public void reconfigure(AccessoryPartRegistry registry,
            AccessoryPageRegistry pageRegistry,
            AccessorySlotSourceConfig slotSources) {
        this.registry = registry == null ? AccessoryPartRegistry.empty() : registry;
        this.pageRegistry = pageRegistry == null ? AccessoryPageRegistry.empty() : pageRegistry;
        this.slotSources = slotSources == null ? AccessorySlotSourceConfig.defaults() : slotSources;
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
        String activePage = effectivePage(accessories);

        for (String slotInstanceId : pageRegistry.slotsOf(activePage)) {
            ItemStack item = accessories.itemAt(activePage, slotInstanceId);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            collectAttributes(accessories.playerId(), slotInstanceId, item, attributes);
            collectSkills(slotInstanceId, item, skills);
        }

        Map<String, Integer> setPieces = setService == null
                ? Map.of()
                : setService.countPieces(accessories, activePage, pageRegistry);
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

    public String effectivePage(PlayerAccessories accessories) {
        if (accessories == null) {
            return "";
        }
        String requested = pageRegistry.resolveEnabledPage(accessories.enabledPage());
        if (Texts.isBlank(requested)) {
            return "";
        }
        String permission = pageRegistry.permissionOf(requested);
        if (Texts.isBlank(permission)) {
            return requested;
        }
        Player holder = Bukkit.getPlayer(accessories.playerId());
        return holder != null && holder.hasPermission(permission) ? requested : "";
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
        Set<String> declared = AccessorySlotDeclarations.read(item, slotSources);
        return AccessorySlotDeclarations.matchesAny(slotInstanceId, declared);
    }

    private void collectSkills(String slotInstanceId, ItemStack item, Map<String, String> skills) {
        AccessorySkillPayloadCodec.Payload payload = AccessorySkillPayloadCodec.read(item);
        if (!payload.present() || payload.skillIds().isEmpty()) {
            return;
        }
        if (!AccessorySlotDeclarations.matchesAny(slotInstanceId,
                AccessorySlotDeclarations.read(item, slotSources))) {
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
