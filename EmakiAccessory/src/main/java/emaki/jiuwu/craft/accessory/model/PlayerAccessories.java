package emaki.jiuwu.craft.accessory.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.session.SessionData;

public final class PlayerAccessories implements SessionData<PlayerAccessories> {

    private final UUID playerId;
    private final Map<String, ItemStack> items = new LinkedHashMap<>();
    private String playerName = "";
    private long revision;
    private long persistedRevision;

    public PlayerAccessories(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public void playerName(String playerName) {
        if (Texts.isNotBlank(playerName) && !playerName.equals(this.playerName)) {
            this.playerName = playerName;
            markDirty();
        }
    }

    public Map<String, ItemStack> items() {
        return Map.copyOf(items);
    }

    public Set<String> slotKeys() {
        return Set.copyOf(items.keySet());
    }

    public ItemStack itemAt(String slotInstanceId) {
        return items.get(Texts.normalizeId(slotInstanceId));
    }

    public ItemStack put(String slotInstanceId, ItemStack item) {
        String key = Texts.normalizeId(slotInstanceId);
        if (Texts.isBlank(key)) {
            return null;
        }
        ItemStack previous;
        if (item == null || item.getType().isAir()) {
            previous = items.remove(key);
        } else {
            previous = items.put(key, item.clone());
        }
        markDirty();
        return previous;
    }

    public ItemStack remove(String slotInstanceId) {
        ItemStack removed = items.remove(Texts.normalizeId(slotInstanceId));
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    public Map<String, ItemStack> clearAll() {
        if (items.isEmpty()) {
            return Map.of();
        }
        Map<String, ItemStack> removed = new LinkedHashMap<>(items);
        items.clear();
        markDirty();
        return Map.copyOf(removed);
    }

    public int occupiedCount() {
        return items.size();
    }

    public void installLoaded(Map<String, ItemStack> loaded) {
        items.clear();
        if (loaded != null) {
            loaded.forEach((key, value) -> {
                String normalized = Texts.normalizeId(key);
                if (Texts.isNotBlank(normalized) && value != null && !value.getType().isAir()) {
                    items.put(normalized, value.clone());
                }
            });
        }
    }

    @Override
    public PlayerAccessories copy() {
        PlayerAccessories copy = new PlayerAccessories(playerId);
        copy.playerName = playerName;
        items.forEach((key, value) -> copy.items.put(key, value.clone()));
        copy.revision = revision;
        copy.persistedRevision = persistedRevision;
        return copy;
    }

    @Override
    public long revision() {
        return revision;
    }

    @Override
    public long persistedRevision() {
        return persistedRevision;
    }

    @Override
    public boolean dirty() {
        return revision > persistedRevision;
    }

    @Override
    public void markDirty() {
        revision++;
    }

    @Override
    public void markPersisted(long revision) {
        if (revision > persistedRevision) {
            persistedRevision = revision;
        }
    }

    @Override
    public void clearDirty() {
        persistedRevision = revision;
    }
}
