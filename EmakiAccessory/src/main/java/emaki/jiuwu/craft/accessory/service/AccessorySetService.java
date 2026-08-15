package emaki.jiuwu.craft.accessory.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.api.event.AccessorySetBonusChangeEvent;
import emaki.jiuwu.craft.accessory.model.AccessorySetDefinition;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.api.EmakiItemApi;

public final class AccessorySetService {

    private final Map<UUID, Map<String, Integer>> lastCounts = new ConcurrentHashMap<>();
    private volatile Map<String, AccessorySetDefinition> definitions = Map.of();

    public void reconfigure(Map<String, AccessorySetDefinition> definitions) {
        this.definitions = definitions == null ? Map.of() : Map.copyOf(definitions);

        lastCounts.clear();
    }

    public Map<String, AccessorySetDefinition> definitions() {
        return definitions;
    }

    public Map<String, Integer> countPieces(PlayerAccessories accessories, AccessoryPartRegistry registry) {
        Map<String, AccessorySetDefinition> active = definitions;
        if (accessories == null || registry == null || active.isEmpty() || !EmakiItemApi.status().usable()) {
            return Map.of();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String slotInstanceId : registry.slotInstanceIds()) {
            ItemStack item = accessories.itemAt(slotInstanceId);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String definitionId = EmakiItemApi.catalog().identify(item).orElse("");
            if (Texts.isBlank(definitionId)) {
                continue;
            }
            for (AccessorySetDefinition set : active.values()) {
                if (matchesPiece(set, definitionId, slotInstanceId)) {
                    counts.merge(set.setId(), 1, Integer::sum);
                }
            }
        }
        return Map.copyOf(counts);
    }

    private boolean matchesPiece(AccessorySetDefinition set, String definitionId, String slotInstanceId) {
        for (AccessorySetDefinition.Piece piece : set.pieces().values()) {
            if (!piece.itemId().equals(definitionId)) {
                continue;
            }
            if (AccessoryPartRegistry.matchesAccessorySlot(slotInstanceId, piece.slot())) {
                return true;
            }
        }
        return false;
    }

    public void applyBonuses(Map<String, Integer> setPieces,
            Map<String, Double> attributes,
            Map<String, String> skills) {
        if (setPieces == null || setPieces.isEmpty()) {
            return;
        }
        Map<String, AccessorySetDefinition> active = definitions;
        setPieces.forEach((setId, pieces) -> {
            AccessorySetDefinition set = active.get(setId);
            if (set == null || pieces == null) {
                return;
            }
            for (AccessorySetDefinition.Threshold threshold : set.activeThresholds(pieces)) {
                threshold.attributes().forEach((attributeId, value) ->
                        attributes.merge(attributeId, value, Double::sum));
                for (String skillId : threshold.skills()) {
                    skills.putIfAbsent(skillId, "set:" + setId);
                }
            }
        });
    }

    public int equippedPieces(UUID playerId, String setId) {
        if (playerId == null) {
            return 0;
        }
        Map<String, Integer> counts = lastCounts.get(playerId);
        if (counts == null) {
            return 0;
        }
        Integer count = counts.get(Texts.normalizeId(setId));
        return count == null ? 0 : count;
    }

    public void publishChanges(Player player, Map<String, Integer> newCounts) {
        if (player == null) {
            return;
        }
        Map<String, Integer> resolved = newCounts == null ? Map.of() : newCounts;
        Map<String, Integer> previous = lastCounts.getOrDefault(player.getUniqueId(), Map.of());
        lastCounts.put(player.getUniqueId(), Map.copyOf(resolved));
        Map<String, AccessorySetDefinition> active = definitions;

        List<String> touched = new ArrayList<>(resolved.keySet());
        for (String setId : previous.keySet()) {
            if (!touched.contains(setId)) {
                touched.add(setId);
            }
        }
        for (String setId : touched) {
            int oldCount = previous.getOrDefault(setId, 0);
            int newCount = resolved.getOrDefault(setId, 0);
            if (oldCount == newCount) {
                continue;
            }
            AccessorySetDefinition set = active.get(setId);
            int totalPieces = set == null ? 0 : set.totalPieces();
            List<Integer> activeThresholds = new ArrayList<>();
            if (set != null) {
                set.activeThresholds(newCount)
                        .forEach(threshold -> activeThresholds.add(threshold.requiredPieces()));
            }
            Bukkit.getPluginManager().callEvent(new AccessorySetBonusChangeEvent(
                    player, setId, oldCount, newCount, totalPieces, activeThresholds));
        }
    }

    public void forget(UUID playerId) {
        if (playerId != null) {
            lastCounts.remove(playerId);
        }
    }
}
