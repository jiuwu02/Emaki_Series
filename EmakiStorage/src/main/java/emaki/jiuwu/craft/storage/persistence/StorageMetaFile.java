package emaki.jiuwu.craft.storage.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.storage.model.SortMode;

public final class StorageMetaFile {

    private static final String META_FILE_NAME = "meta.yml";

    public static final String CURRENT_VERSION = "1.0.0";

    public record Meta(String playerName,
            int grantedSlots,
            int purchasedSlots,
            long defaultStackLimit,
            SortMode sortMode,
            boolean autoPickupEnabled) {

        public static Meta defaults(SortMode fallbackSort) {
            return defaults(fallbackSort, false);
        }

        public static Meta defaults(SortMode fallbackSort, boolean autoPickupByDefault) {
            return new Meta("", 0, 0, 0L, fallbackSort, autoPickupByDefault);
        }
    }

    private final Path dataRoot;

    public StorageMetaFile(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    public Path metaFile(UUID playerId) {
        return dataRoot.resolve(playerId.toString()).resolve(META_FILE_NAME);
    }

    public Meta load(UUID playerId, SortMode fallbackSort) {
        return load(playerId, fallbackSort, false);
    }

    public Meta load(UUID playerId, SortMode fallbackSort, boolean autoPickupByDefault) {
        YamlSection section = YamlFiles.load(metaFile(playerId).toFile());
        if (section == null || section.isEmpty()) {
            return Meta.defaults(fallbackSort, autoPickupByDefault);
        }
        return new Meta(
                section.getString("player_name", ""),
                intValue(section, "granted_slots", 0),
                Math.max(0, intValue(section, "purchased_slots", 0)),
                Math.max(0L, longValue(section, "default_stack_limit", 0L)),
                SortMode.fromId(section.getString("sort_mode", null), fallbackSort),
                Boolean.TRUE.equals(section.getBoolean("auto_pickup", autoPickupByDefault)));
    }

    public void save(UUID playerId, Meta meta) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("version", CURRENT_VERSION);
        values.put("player_name", meta.playerName() == null ? "" : meta.playerName());
        values.put("granted_slots", meta.grantedSlots());
        values.put("purchased_slots", meta.purchasedSlots());
        values.put("default_stack_limit", meta.defaultStackLimit());
        values.put("sort_mode", meta.sortMode() == null ? SortMode.AMOUNT_DESC.id() : meta.sortMode().id());
        values.put("auto_pickup", meta.autoPickupEnabled());
        YamlFiles.save(metaFile(playerId).toFile(), values);
    }

    private static int intValue(YamlSection section, String path, int fallback) {
        Integer value = section.getInt(path, fallback);
        return value == null ? fallback : value;
    }

    private static long longValue(YamlSection section, String path, long fallback) {
        Object raw = section.get(path);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
