package emaki.jiuwu.craft.dungeon.world;

import java.io.File;

import org.bukkit.Bukkit;

/**
 * 副本实例世界名生成器
 *
 * <p>末尾计数器采用"最小可用序号": 每次取号都从 1 开始扫描, 返回第一个
 * 既未加载也不在磁盘上的序号.这样每一轮创建都从 1 开始, 同一轮内并发创建
 * 会自然递增, 而历史轮次销毁掉的序号会被下一轮重新使用, 不会像累计计数器
 * 那样一路涨到 164/165.</p>
 */
public final class WorldNames {

    public static final String PREFIX = "emd_dungeon_";

    private static final int MAX_SEQUENCE = 512;

    private final File worldContainer;

    public WorldNames(File worldContainer) {
        this.worldContainer = worldContainer;
    }

    /**
     * 取下一个可用的实例世界名: 从 {@code 1} 开始找最小空位
     *
     * @param dungeonId 副本 ID
     * @return 形如 {@code emd_dungeon_<dungeonId>_<n>} 的世界名
     */
    public String next(String dungeonId) {
        String key = dungeonId == null ? "" : dungeonId;
        for (int sequence = 1; sequence <= MAX_SEQUENCE; sequence++) {
            String candidate = PREFIX + key + "_" + sequence;
            if (!inUse(candidate)) {
                return candidate;
            }
        }
        return PREFIX + key + "_" + System.currentTimeMillis();
    }

    private boolean inUse(String worldName) {
        if (Bukkit.getWorld(worldName) != null) {
            return true;
        }
        return WorldFolders.anyPresent(worldContainer, worldName);
    }

    public static boolean isInstanceWorld(String worldName) {
        return worldName != null && worldName.startsWith(PREFIX);
    }
}
