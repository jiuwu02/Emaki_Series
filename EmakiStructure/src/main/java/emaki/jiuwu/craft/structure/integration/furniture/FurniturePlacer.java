package emaki.jiuwu.craft.structure.integration.furniture;

import emaki.jiuwu.craft.structure.integration.FurnitureIdAdapter;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class FurniturePlacer {

    private final FurnitureIdAdapter adapter;
    private final Map<String, FurnitureRestorer> restorers = new ConcurrentHashMap<>();

    public FurniturePlacer(FurnitureIdAdapter adapter) {
        this.adapter = adapter;
    }

    public Entity place(String canonicalId, Location location) {
        ParsedId parsed = parse(canonicalId);
        if (parsed == null) {
            return null;
        }
        if (!sourcePresent(parsed.namespace())) {
            return null;
        }
        FurnitureRestorer restorer = restorer(parsed.namespace());
        if (restorer == null) {
            return null;
        }
        try {
            return restorer.place(parsed.rawId(), location);
        } catch (LinkageError | RuntimeException exception) {
            return null;
        }
    }

    public boolean remove(Entity entity) {
        Optional<String> source = adapter.identifySource(entity);
        if (source.isEmpty()) {
            return false;
        }
        String namespace = source.get().toLowerCase(Locale.ROOT);
        if (!sourcePresent(namespace)) {
            return false;
        }
        FurnitureRestorer restorer = restorer(namespace);
        if (restorer == null) {
            return false;
        }
        try {
            return restorer.remove(entity);
        } catch (LinkageError | RuntimeException exception) {
            return false;
        }
    }

    public boolean sourcePresent(String namespace) {
        String sourceName = FurnitureIdAdapter.sourceOfNamespace(namespace);
        return sourceName != null && adapter.sourcePresent(sourceName);
    }

    private FurnitureRestorer restorer(String namespace) {
        FurnitureRestorer existing = restorers.get(namespace);
        if (existing != null) {
            return existing;
        }
        FurnitureRestorer created = switch (namespace) {
            case "itemsadder" -> tryCreate(ItemsAdderFurnitureRestorer::new);
            case "oraxen" -> tryCreate(OraxenFurnitureRestorer::new);
            case "nexo" -> tryCreate(NexoFurnitureRestorer::new);
            case "craftengine" -> tryCreate(CraftEngineFurnitureRestorer::new);
            default -> null;
        };
        if (created != null) {
            restorers.put(namespace, created);
        }
        return created;
    }

    private static FurnitureRestorer tryCreate(java.util.function.Supplier<FurnitureRestorer> factory) {
        try {
            return factory.get();
        } catch (LinkageError | RuntimeException exception) {
            return null;
        }
    }

    private static ParsedId parse(String canonicalId) {
        if (canonicalId == null) {
            return null;
        }
        int colon = canonicalId.indexOf(':');
        if (colon <= 0 || colon == canonicalId.length() - 1) {
            return null;
        }
        String namespace = canonicalId.substring(0, colon).toLowerCase(Locale.ROOT);
        if (FurnitureIdAdapter.sourceOfNamespace(namespace) == null) {
            return null;
        }
        return new ParsedId(namespace, canonicalId.substring(colon + 1));
    }

    private record ParsedId(String namespace, String rawId) {
    }
}
