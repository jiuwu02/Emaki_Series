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
    private final Map<String, Map<String, ItemStack>> pages = new LinkedHashMap<>();
    private String playerName = "";
    private String enabledPage = "";
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

    public String enabledPage() {
        return enabledPage;
    }

    public void enabledPage(String pageId) {
        String normalized = Texts.normalizeId(pageId);
        if (Texts.isNotBlank(normalized) && !normalized.equals(enabledPage)) {
            enabledPage = normalized;
            markDirty();
        }
    }

    public Set<String> pageIds() {
        return Set.copyOf(pages.keySet());
    }

    public Map<String, Map<String, ItemStack>> allPages() {
        Map<String, Map<String, ItemStack>> copy = new LinkedHashMap<>();
        pages.forEach((pageId, items) -> copy.put(pageId, Map.copyOf(items)));
        return Map.copyOf(copy);
    }

    public Map<String, ItemStack> items(String pageId) {
        Map<String, ItemStack> items = pages.get(Texts.normalizeId(pageId));
        return items == null ? Map.of() : Map.copyOf(items);
    }

    public Set<String> slotKeys(String pageId) {
        Map<String, ItemStack> items = pages.get(Texts.normalizeId(pageId));
        return items == null ? Set.of() : Set.copyOf(items.keySet());
    }

    public ItemStack itemAt(String pageId, String slotInstanceId) {
        Map<String, ItemStack> items = pages.get(Texts.normalizeId(pageId));
        return items == null ? null : items.get(Texts.normalizeId(slotInstanceId));
    }

    public ItemStack put(String pageId, String slotInstanceId, ItemStack item) {
        String page = Texts.normalizeId(pageId);
        String key = Texts.normalizeId(slotInstanceId);
        if (Texts.isBlank(page) || Texts.isBlank(key)) {
            return null;
        }
        ItemStack previous;
        if (item == null || item.getType().isAir()) {
            Map<String, ItemStack> items = pages.get(page);
            previous = items == null ? null : items.remove(key);
            if (items != null && items.isEmpty()) {
                pages.remove(page);
            }
        } else {
            previous = pages.computeIfAbsent(page, ignored -> new LinkedHashMap<>())
                    .put(key, item.clone());
        }
        markDirty();
        return previous;
    }

    public ItemStack remove(String pageId, String slotInstanceId) {
        String page = Texts.normalizeId(pageId);
        Map<String, ItemStack> items = pages.get(page);
        if (items == null) {
            return null;
        }
        ItemStack removed = items.remove(Texts.normalizeId(slotInstanceId));
        if (items.isEmpty()) {
            pages.remove(page);
        }
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    public Map<String, ItemStack> clearPage(String pageId) {
        String page = Texts.normalizeId(pageId);
        Map<String, ItemStack> items = pages.get(page);
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        Map<String, ItemStack> removed = Map.copyOf(items);
        pages.remove(page);
        markDirty();
        return removed;
    }

    public Map<String, Map<String, ItemStack>> clearAll() {
        if (pages.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, ItemStack>> removed = new LinkedHashMap<>();
        pages.forEach((pageId, items) -> removed.put(pageId, Map.copyOf(items)));
        pages.clear();
        markDirty();
        return Map.copyOf(removed);
    }

    public int occupiedCount(String pageId) {
        Map<String, ItemStack> items = pages.get(Texts.normalizeId(pageId));
        return items == null ? 0 : items.size();
    }

    public int totalOccupiedCount() {
        int total = 0;
        for (Map<String, ItemStack> items : pages.values()) {
            total += items.size();
        }
        return total;
    }

    public void installLoaded(Map<String, Map<String, ItemStack>> loaded, String enabledPage) {
        pages.clear();
        if (loaded != null) {
            loaded.forEach((pageId, items) -> {
                String page = Texts.normalizeId(pageId);
                if (Texts.isBlank(page) || items == null) {
                    return;
                }
                Map<String, ItemStack> target = new LinkedHashMap<>();
                items.forEach((key, value) -> {
                    String normalized = Texts.normalizeId(key);
                    if (Texts.isNotBlank(normalized) && value != null && !value.getType().isAir()) {
                        target.put(normalized, value.clone());
                    }
                });
                if (!target.isEmpty()) {
                    pages.put(page, target);
                }
            });
        }
        this.enabledPage = Texts.normalizeId(enabledPage);
    }

    @Override
    public PlayerAccessories copy() {
        PlayerAccessories copy = new PlayerAccessories(playerId);
        copy.playerName = playerName;
        copy.enabledPage = enabledPage;
        pages.forEach((pageId, items) -> {
            Map<String, ItemStack> target = new LinkedHashMap<>();
            items.forEach((key, value) -> target.put(key, value.clone()));
            copy.pages.put(pageId, target);
        });
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
