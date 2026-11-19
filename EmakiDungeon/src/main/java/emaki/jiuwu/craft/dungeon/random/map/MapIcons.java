package emaki.jiuwu.craft.dungeon.random.map;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MapIcons {

    private static final String[] STAR = {
        "...XX...",
        "...XX...",
        ".XXXXXX.",
        "XXXXXXXX",
        "XXXXXXXX",
        ".XXXXXX.",
        "..X..X..",
        ".X....X."
    };
    private static final String[] SKULL = {
        ".XXXXXX.",
        "XXXXXXXX",
        "X.XXXX.X",
        "XXXXXXXX",
        ".XXXXXX.",
        "..X..X..",
        ".XXXXXX.",
        "..X..X.."
    };
    private static final String[] CHEST = {
        "..XXXX..",
        ".XXXXXX.",
        "XXXXXXXX",
        "XXX..XXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXX..XXX",
        "XXXXXXXX"
    };
    private static final String[] FLAG = {
        "X.......",
        "XXXX....",
        "XXXXXX..",
        "XXXX....",
        "X.......",
        "X.......",
        "X.......",
        "X......."
    };
    private static final String[] DOT = {
        "........",
        "........",
        "...XX...",
        "..XXXX..",
        "..XXXX..",
        "...XX...",
        "........",
        "........"
    };
    private static final String[] GATE = {
        "XXXXXXXX",
        "X..XX..X",
        "X..XX..X",
        "X..XX..X",
        "X..XX..X",
        "X..XX..X",
        "X..XX..X",
        "XXXXXXXX"
    };
    private static final String[] STAIRS = {
        "......XX",
        ".....XXX",
        "....XXXX",
        "...XXXXX",
        "..XXXXXX",
        ".XXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX"
    };
    private static final String[] RING = {
        "..XXXX..",
        ".X....X.",
        "X......X",
        "X......X",
        "X......X",
        "X......X",
        ".X....X.",
        "..XXXX.."
    };

    private static final Map<String, String[]> ICONS = new LinkedHashMap<>();

    static {
        ICONS.put("star", STAR);
        ICONS.put("skull", SKULL);
        ICONS.put("chest", CHEST);
        ICONS.put("flag", FLAG);
        ICONS.put("dot", DOT);
        ICONS.put("gate", GATE);
        ICONS.put("stairs", STAIRS);
        ICONS.put("ring", RING);
    }

    public static String[] icon(String key) {
        return key == null ? null : ICONS.get(key.toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean exists(String key) {
        return icon(key) != null;
    }

    private MapIcons() {
    }
}
