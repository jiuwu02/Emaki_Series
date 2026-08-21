package emaki.jiuwu.craft.corelib.item;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import io.papermc.paper.datacomponent.DataComponentType;

public final class ItemComponentSnapshot {

    private static final String MINECRAFT_PREFIX = "minecraft:";

    private static final class BridgeHolder {
        private static final PaperItemComponentBridge INSTANCE = new PaperItemComponentBridge();
    }

    private final ItemStack item;
    private final Map<String, Object> patchValues;
    private final Set<String> removedIds;

    private ItemComponentSnapshot(ItemStack item, Map<String, Object> patchValues, Set<String> removedIds) {
        this.item = item;
        this.patchValues = patchValues;
        this.removedIds = removedIds;
    }

    public static ItemComponentSnapshot of(ItemStack item) {
        ItemComponentSnapshotScope scope = ItemComponentSnapshotScope.active();
        return scope == null ? uncached(item) : scope.snapshot(item);
    }

    static ItemComponentSnapshot uncached(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return new ItemComponentSnapshot(null, Map.of(), Set.of());
        }
        Map<String, Object> values = new LinkedHashMap<>();
        Set<String> removed = new LinkedHashSet<>();
        readPatch(item, values, removed);
        return new ItemComponentSnapshot(item, values, removed);
    }

    public static String normalizeComponentId(String componentId) {
        String normalized = Texts.toStringSafe(componentId).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (normalized.startsWith("!")) {
            normalized = normalized.substring(1).trim();
        }
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized.indexOf(':') >= 0 ? normalized : MINECRAFT_PREFIX + normalized;
    }

    public boolean isEmpty() {
        return item == null;
    }

    public boolean removed(String componentId) {
        return removedIds.contains(normalizeComponentId(componentId));
    }

    public boolean present(String componentId) {
        String id = normalizeComponentId(componentId);
        if (id.isEmpty() || item == null) {
            return false;
        }
        if (removedIds.contains(id)) {
            return false;
        }
        if (patchValues.containsKey(id)) {
            return true;
        }
        return hasRuntimeData(id);
    }

    public boolean hasPatchValue(String componentId) {
        String id = normalizeComponentId(componentId);
        return !id.isEmpty() && !removedIds.contains(id) && patchValues.containsKey(id);
    }

    public List<Object> resolve(String componentId, ComponentPath path) {
        String id = normalizeComponentId(componentId);
        if (id.isEmpty() || item == null || removedIds.contains(id) || !patchValues.containsKey(id)) {
            return List.of();
        }
        Object root = patchValues.get(id);
        if (root == null) {
            return List.of();
        }
        return path == null ? List.of(root) : path.evaluate(root);
    }

    private boolean hasRuntimeData(String componentId) {
        try {
            DataComponentType type = BridgeHolder.INSTANCE.componentType(componentId);
            return type != null && item.hasData(type);
        } catch (RuntimeException | LinkageError _) {
            return false;
        }
    }

    private static void readPatch(ItemStack item, Map<String, Object> values, Set<String> removed) {
        String raw = rawComponentString(item);
        String normalized = Texts.toStringSafe(raw).trim();
        if (normalized.length() < 2 || !normalized.startsWith("[") || !normalized.endsWith("]")) {
            return;
        }
        String body = normalized.substring(1, normalized.length() - 1).trim();
        if (body.isEmpty()) {
            return;
        }
        for (String entry : ComponentEntrySplitter.split(body)) {
            readEntry(entry, values, removed);
        }
    }

    private static void readEntry(String rawEntry, Map<String, Object> values, Set<String> removed) {
        String entry = Texts.toStringSafe(rawEntry).trim();
        if (entry.isEmpty()) {
            return;
        }
        boolean explicitlyRemoved = entry.startsWith("!");
        if (explicitlyRemoved) {
            entry = entry.substring(1).trim();
        }
        int assignment = ComponentEntrySplitter.findTopLevel(entry, '=');
        String id = normalizeComponentId(assignment < 0 ? entry : entry.substring(0, assignment));
        if (id.isEmpty()) {
            return;
        }
        if (explicitlyRemoved) {
            removed.add(id);
            values.remove(id);
            return;
        }
        removed.remove(id);
        String value = assignment < 0 ? "" : entry.substring(assignment + 1).trim();
        values.put(id, ComponentValueParser.parseLenient(value));
    }

    private static String rawComponentString(ItemStack item) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return "";
            }
            return meta.getAsComponentString();
        } catch (RuntimeException | LinkageError _) {
            return "";
        }
    }
}
