package emaki.jiuwu.craft.storage.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.storage.model.SortMode;

/**
 * Reads and writes one player's {@code meta.yml}.
 *
 * <p>Kept as text on purpose: slot counts and limits are the values an admin may legitimately
 * want to hand-edit, while the entry payloads stay binary because YAML cannot express a full
 * {@code ItemStack}.
 *
 * <p>Only {@code purchasedSlots} as a count is stored — never a set of specific slot numbers —
 * so changing {@code capacity.base_slots} can never consume purchased capacity.
 *
 * <p>Blocking IO; call from an async file lane only.
 */
public final class StorageMetaFile {

    private static final String META_FILE_NAME = "meta.yml";

    /** Current meta schema version, tracked separately from the plugin version. */
    public static final String CURRENT_VERSION = "1.0.0";

    /**
     * Persisted metadata.
     *
     * @param playerName        last known name, for human troubleshooting only
     * @param grantedSlots      slots granted by command or API, may be negative
     * @param purchasedSlots    slots bought through the unlock flow
     * @param defaultStackLimit player-level ceiling; {@code 0} inherits config
     * @param sortMode          the persisted sort mode
     */
    public record Meta(String playerName,
            int grantedSlots,
            int purchasedSlots,
            long defaultStackLimit,
            SortMode sortMode,
            boolean autoPickupEnabled) {

        public static Meta defaults(SortMode fallbackSort) {
            return defaults(fallbackSort, false);
        }

        /**
         * @param fallbackSort         缺省排序
         * @param autoPickupByDefault  新玩家的自动拾取默认状态
         * @return 缺省元数据
         */
        public static Meta defaults(SortMode fallbackSort, boolean autoPickupByDefault) {
            return new Meta("", 0, 0, 0L, fallbackSort, autoPickupByDefault);
        }
    }

    private final Path dataRoot;

    public StorageMetaFile(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    /** {@return the meta file for a player} */
    public Path metaFile(UUID playerId) {
        return dataRoot.resolve(playerId.toString()).resolve(META_FILE_NAME);
    }

    /**
     * Loads metadata, falling back to defaults for any missing or malformed key.
     *
     * @param playerId     the storage owner
     * @param fallbackSort the sort mode to use when none is persisted
     * @return the parsed metadata, never {@code null}
     */
    public Meta load(UUID playerId, SortMode fallbackSort) {
        return load(playerId, fallbackSort, false);
    }

    /**
     * Loads metadata, falling back to defaults for any missing or malformed key.
     *
     * @param playerId            the storage owner
     * @param fallbackSort        the sort mode to use when none is persisted
     * @param autoPickupByDefault the auto pickup state for players without a stored value
     * @return the parsed metadata, never {@code null}
     */
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

    /**
     * Writes metadata through CoreLib's atomic YAML save.
     *
     * @param playerId the storage owner
     * @param meta     the metadata to persist
     * @throws IOException when the file cannot be written
     */
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
