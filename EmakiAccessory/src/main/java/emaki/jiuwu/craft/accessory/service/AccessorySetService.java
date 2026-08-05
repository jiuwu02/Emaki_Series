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

/**
 * Counts equipped accessory set pieces and folds their threshold bonuses into the contribution maps.
 *
 * <p>Self-contained rather than delegating to EmakiItem's set service, for three reasons established
 * during design: EmakiItem scans raw Bukkit inventory indices and structurally cannot see an accessory
 * slot; its {@code equip_slot} parser rejects custom slot ids and would silently widen a part id to
 * "any slot"; and its set values are baked into item PDC, so participation would require EmakiItem to
 * query this module and create a dependency cycle.
 *
 * <p>The bonuses need no new delivery mechanism. They are merged into the same attribute and skill maps
 * single accessories produce, so to EmakiAttribute and EmakiSkills a set bonus is indistinguishable
 * from an accessory's own contribution apart from its source id.
 */
public final class AccessorySetService {

    private final Map<UUID, Map<String, Integer>> lastCounts = new ConcurrentHashMap<>();
    private volatile Map<String, AccessorySetDefinition> definitions = Map.of();

    /**
     * Applies a new set configuration.
     *
     * @param definitions the loaded sets keyed by set id
     */
    public void reconfigure(Map<String, AccessorySetDefinition> definitions) {
        this.definitions = definitions == null ? Map.of() : Map.copyOf(definitions);
        // Piece counts are derived from these definitions, so a reload must not leave stale edge state
        // behind or the next recompute would compare against a set that no longer exists.
        lastCounts.clear();
    }

    /** {@return the active set definitions keyed by set id} */
    public Map<String, AccessorySetDefinition> definitions() {
        return definitions;
    }

    /**
     * {@return how many pieces of each set the player currently has equipped}
     *
     * <p>Only configured slots are scanned, so an orphaned accessory never counts toward a set. A piece
     * must also satisfy its own {@code slot} declaration, which may name a part or one exact instance.
     *
     * @param accessories the player's current contents
     * @param registry    the active part configuration
     */
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

    /**
     * Merges the active threshold bonuses into the contribution maps.
     *
     * @param setPieces  equipped piece counts from {@link #countPieces}
     * @param attributes attribute accumulator to add into
     * @param skills     skill accumulator to add into, keyed by skill id
     */
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

    /**
     * {@return the equipped piece count recorded for one set at the last recompute}
     *
     * @param playerId the player id
     * @param setId    the set id
     */
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

    /**
     * Publishes {@link AccessorySetBonusChangeEvent} for every set whose piece count changed.
     *
     * <p>Edge-triggered: recomputation happens on every content change, but an unchanged count must not
     * produce an event, or listeners would fire on every unrelated slot edit. Call on the player's
     * owner thread, after the contribution snapshot has been installed.
     *
     * @param player    the player whose counts were recomputed; {@code null} only records state
     * @param newCounts the freshly computed counts
     */
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

    /**
     * Drops a player's recorded counts, so their next recompute is treated as a fresh baseline.
     *
     * @param playerId the player id
     */
    public void forget(UUID playerId) {
        if (playerId != null) {
            lastCounts.remove(playerId);
        }
    }
}
