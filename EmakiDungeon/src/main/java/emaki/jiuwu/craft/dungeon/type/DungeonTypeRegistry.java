package emaki.jiuwu.craft.dungeon.type;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class DungeonTypeRegistry {

    public static final String NORMAL = "normal";
    public static final String RANDOM = "random";

    private final Map<String, DungeonType> types = new ConcurrentHashMap<>();
    private final BiConsumer<String, String> unknownTypeWarning;

    public DungeonTypeRegistry(BiConsumer<String, String> unknownTypeWarning) {
        this.unknownTypeWarning = unknownTypeWarning == null
                ? (dungeon, raw) -> { } : unknownTypeWarning;
        register(new NormalDungeonType());
        register(new RandomDungeonType());
    }

    public void register(DungeonType type) {
        if (type == null || type.id() == null || type.id().isBlank()) {
            return;
        }
        types.put(type.id(), type);
    }

    public DungeonType resolve(String raw, String dungeonId) {
        if (raw == null || raw.isBlank()) {
            return types.get(NORMAL);
        }
        DungeonType type = types.get(raw);
        if (type != null) {
            return type;
        }
        unknownTypeWarning.accept(dungeonId, raw);
        return types.get(NORMAL);
    }

    public Set<String> ids() {
        return Set.copyOf(types.keySet());
    }
}
