package emaki.jiuwu.craft.dungeon.world;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorldNames {

    public static final String PREFIX = "emd_dungeon_";

    private static final Pattern NAME_PATTERN = Pattern.compile(PREFIX + ".*_(\\d+)");
    private final AtomicLong sequence = new AtomicLong(0);

    public WorldNames(File worldContainer) {
        long max = 0;
        if (worldContainer != null) {
            for (java.nio.file.Path root : WorldFolders.sweepRoots(worldContainer)) {
                File[] children = root.toFile().listFiles(File::isDirectory);
                if (children == null) {
                    continue;
                }
                for (File child : children) {
                    Matcher matcher = NAME_PATTERN.matcher(child.getName());
                    if (matcher.matches()) {
                        try {
                            max = Math.max(max, Long.parseLong(matcher.group(1)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        sequence.set(max);
    }

    public String next(String dungeonId) {
        return PREFIX + dungeonId + "_" + sequence.incrementAndGet();
    }

    public static boolean isInstanceWorld(String worldName) {
        return worldName != null && worldName.startsWith(PREFIX);
    }
}
