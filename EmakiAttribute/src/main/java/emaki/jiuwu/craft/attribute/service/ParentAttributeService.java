package emaki.jiuwu.craft.attribute.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.PlayerAttributePointAllocateEvent;
import emaki.jiuwu.craft.attribute.api.PlayerAttributePointResetEvent;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.ParentAttributeData;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ParentAttributeService {

    private static final double ZERO_EPSILON = 1.0E-9D;

    private final EmakiAttributePlugin plugin;
    private final ParentAttributeDataStore dataStore;

    public ParentAttributeService(EmakiAttributePlugin plugin, ParentAttributeDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    public ParentAttributeDataStore dataStore() {
        return dataStore;
    }

    public ParentAttributeData load(Player player) {
        return dataStore.load(player);
    }

    public ParentAttributeData data(Player player) {
        return player == null ? null : dataStore.getOrLoad(player.getUniqueId());
    }

    public ParentAttributeData data(UUID uuid) {
        return uuid == null ? null : dataStore.getOrLoad(uuid);
    }

    public List<AttributeDefinition> parentAttributes() {
        if (plugin.attributeRegistry() == null) {
            return List.of();
        }
        return plugin.attributeRegistry().all().values().stream()
                .filter(AttributeDefinition::parentAttribute)
                .sorted((left, right) -> Integer.compare(right.priority(), left.priority()))
                .toList();
    }

    public AttributeDefinition parentAttribute(String attributeId) {
        if (plugin.attributeRegistry() == null) {
            return null;
        }
        AttributeDefinition definition = plugin.attributeRegistry().resolve(attributeId);
        return definition == null || !definition.parentAttribute() ? null : definition;
    }

    public boolean addAvailablePoints(Player player, int amount) {
        if (player == null || amount == 0) {
            return false;
        }
        ParentAttributeData data = data(player);
        data.availablePoints(data.availablePoints() + amount);
        afterMutation(player, data);
        return true;
    }

    public boolean setAvailablePoints(Player player, int amount) {
        if (player == null) {
            return false;
        }
        ParentAttributeData data = data(player);
        data.availablePoints(amount);
        afterMutation(player, data);
        return true;
    }

    public boolean addResetPoints(Player player, int amount) {
        if (player == null || amount == 0) {
            return false;
        }
        ParentAttributeData data = data(player);
        data.resetPoints(data.resetPoints() + amount);
        afterMutation(player, data);
        return true;
    }

    public boolean setResetPoints(Player player, int amount) {
        if (player == null) {
            return false;
        }
        ParentAttributeData data = data(player);
        data.resetPoints(amount);
        afterMutation(player, data);
        return true;
    }

    public AllocateResult allocate(Player player, String attributeId, int amount) {
        if (player == null) {
            return AllocateResult.NOT_PLAYER;
        }
        AttributeDefinition definition = parentAttribute(attributeId);
        if (definition == null) {
            return AllocateResult.UNKNOWN_ATTRIBUTE;
        }
        ParentAttributeData data = data(player);
        int safeAmount = Math.max(1, amount);
        if (data.availablePoints() < safeAmount) {
            return AllocateResult.NOT_ENOUGH_POINTS;
        }
        safeAmount = fireAllocateEvent(player, definition, data, safeAmount);
        if (safeAmount <= 0 || data.availablePoints() < safeAmount) {
            return AllocateResult.NOT_ENOUGH_POINTS;
        }
        data.availablePoints(data.availablePoints() - safeAmount);
        data.allocations().merge(definition.id(), safeAmount, Integer::sum);
        data.markDirty();
        afterMutation(player, data);
        return AllocateResult.SUCCESS;
    }

    public ResetResult reset(Player player, boolean consumeResetPoint) {
        if (player == null) {
            return ResetResult.NOT_PLAYER;
        }
        ParentAttributeData data = data(player);
        int allocated = data.allocatedTotal();
        if (allocated <= 0) {
            return ResetResult.NO_ALLOCATIONS;
        }
        if (consumeResetPoint && data.resetPoints() <= 0) {
            return ResetResult.NOT_ENOUGH_RESET_POINTS;
        }
        if (!fireResetEvent(player, data, allocated, consumeResetPoint)) {
            return ResetResult.CANCELLED;
        }
        if (consumeResetPoint) {
            data.resetPoints(data.resetPoints() - 1);
        }
        data.availablePoints(data.availablePoints() + allocated);
        data.allocations().clear();
        data.markDirty();
        afterMutation(player, data);
        return ResetResult.SUCCESS;
    }

    public Map<String, Double> contributionValues(Player player) {
        ParentAttributeData data = player == null ? null : dataStore.cached(player.getUniqueId());
        if (data == null) {
            data = data(player);
        }
        return contributionValues(data);
    }

    public Map<String, Double> contributionValues(ParentAttributeData data) {
        if (data == null || data.allocations().isEmpty()) {
            return Map.of();
        }
        Map<String, Double> values = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : data.allocations().entrySet()) {
            String parentId = Texts.normalizeId(entry.getKey());
            int points = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            if (Texts.isBlank(parentId) || points <= 0) {
                continue;
            }
            AttributeDefinition parent = parentAttribute(parentId);
            if (parent == null) {
                continue;
            }
            values.merge(parent.id(), (double) points, Double::sum);
            for (Map.Entry<String, Double> bonus : parent.childBonuses().entrySet()) {
                if (bonus.getValue() == null) {
                    continue;
                }
                double value = points * bonus.getValue();
                if (Math.abs(value) > ZERO_EPSILON) {
                    values.merge(Texts.normalizeId(bonus.getKey()), value, Double::sum);
                }
            }
        }
        values.entrySet().removeIf(entry -> Math.abs(entry.getValue()) <= ZERO_EPSILON);
        return values.isEmpty() ? Map.of() : Map.copyOf(values);
    }

    public String signature(Player player) {
        ParentAttributeData data = player == null ? null : dataStore.cached(player.getUniqueId());
        if (data == null) {
            data = data(player);
        }
        if (data == null || data.allocations().isEmpty()) {
            return "";
        }
        return SignatureUtil.stableSignature(contributionValues(data));
    }

    public void unload(UUID uuid, boolean save) {
        dataStore.unload(uuid, save);
    }

    public void saveAll() {
        dataStore.saveAll();
    }

    /**
     * Fires the public allocation event when the caller owns the player's thread.
     *
     * @return the amount to allocate, or {@code 0} when cancelled. Off-thread
     *         callers proceed with {@code requestedAmount} without firing.
     */
    private int fireAllocateEvent(Player player,
            AttributeDefinition definition,
            ParentAttributeData data,
            int requestedAmount) {
        ThreadOwnership threadOwnership = plugin.threadOwnership();
        if (threadOwnership == null || !threadOwnership.isEntityOwned(player)) {
            return requestedAmount;
        }
        PlayerAttributePointAllocateEvent event = new PlayerAttributePointAllocateEvent(
                player,
                definition.id(),
                requestedAmount,
                data.availablePoints(),
                data.allocations().getOrDefault(definition.id(), 0));
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled() ? 0 : event.getAmount();
    }

    /**
     * Fires the public reset event when the caller owns the player's thread.
     *
     * @return {@code true} to proceed with the reset, {@code false} when
     *         cancelled. Off-thread callers proceed without firing.
     */
    private boolean fireResetEvent(Player player,
            ParentAttributeData data,
            int refundedPoints,
            boolean consumeResetPoint) {
        ThreadOwnership threadOwnership = plugin.threadOwnership();
        if (threadOwnership == null || !threadOwnership.isEntityOwned(player)) {
            return true;
        }
        PlayerAttributePointResetEvent event = new PlayerAttributePointResetEvent(
                player,
                refundedPoints,
                data.availablePoints(),
                data.resetPoints(),
                consumeResetPoint);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    private void afterMutation(Player player, ParentAttributeData data) {
        dataStore.save(data);
        if (player != null && plugin.attributeService() != null) {
            plugin.attributeService().invalidateCombatSnapshot(player);
            plugin.attributeService().resyncPlayer(player);
        }
    }

    public Player onlinePlayer(String name) {
        return Texts.isBlank(name) ? null : Bukkit.getPlayerExact(name);
    }

    public enum AllocateResult {
        SUCCESS,
        NOT_PLAYER,
        UNKNOWN_ATTRIBUTE,
        NOT_ENOUGH_POINTS
    }

    public enum ResetResult {
        SUCCESS,
        NOT_PLAYER,
        NO_ALLOCATIONS,
        NOT_ENOUGH_RESET_POINTS,
        CANCELLED
    }
}
