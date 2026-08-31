package emaki.jiuwu.craft.corelib.item;

import java.util.IdentityHashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

public final class ItemComponentSnapshotScope implements AutoCloseable {

    private static final ThreadLocal<ItemComponentSnapshotScope> ACTIVE = new ThreadLocal<>();

    private final Map<ItemStack, ItemComponentSnapshot> cache = new IdentityHashMap<>();
    private final ItemComponentSnapshotScope enclosing;
    private boolean closed;

    private ItemComponentSnapshotScope(ItemComponentSnapshotScope enclosing) {
        this.enclosing = enclosing;
    }

    public static ItemComponentSnapshotScope open() {
        ItemComponentSnapshotScope scope = new ItemComponentSnapshotScope(ACTIVE.get());
        ACTIVE.set(scope);
        return scope;
    }

    static ItemComponentSnapshotScope active() {
        return ACTIVE.get();
    }

    ItemComponentSnapshot snapshot(ItemStack item) {
        if (closed || item == null) {
            return ItemComponentSnapshot.uncached(item);
        }
        return cache.computeIfAbsent(item, ItemComponentSnapshot::uncached);
    }

    public int cachedCount() {
        return cache.size();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        cache.clear();
        if (enclosing == null) {
            ACTIVE.remove();
        } else {
            ACTIVE.set(enclosing);
        }
    }
}
