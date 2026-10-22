package emaki.jiuwu.craft.corelib.item;

import org.bukkit.Bukkit;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class MinecraftServerVersions {

    private MinecraftServerVersions() {
    }

    public static String currentServerVersion() {
        try {
            return Bukkit.getServer() == null ? "" : Texts.toStringSafe(Bukkit.getServer().getMinecraftVersion());
        } catch (Throwable unavailable) {
            return "";
        }
    }

    /**
     * 判断当前服务端版本是否大于等于指定下限（如 "26"）。
     * 版本取不到时返回 false，回落旧版路径。
     */
    public static boolean atLeast(String minimum) {
        String version = currentServerVersion();
        if (version == null || version.isBlank()) {
            return false;
        }
        return satisfies(">=" + minimum, version);
    }

    public static boolean satisfies(String requirement, String serverVersion) {
        if (Texts.isBlank(requirement) || Texts.isBlank(serverVersion)) {
            return true;
        }
        String expression = requirement.trim();
        String operator = ">=";
        for (String candidate : new String[] { ">=", "<=", ">", "<", "=" }) {
            if (expression.startsWith(candidate)) {
                operator = candidate;
                expression = expression.substring(candidate.length()).trim();
                break;
            }
        }
        if (expression.isEmpty()) {
            return true;
        }
        int comparison = compare(serverVersion, expression);
        return switch (operator) {
            case ">" -> comparison > 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            case "=" -> comparison == 0;
            default -> comparison >= 0;
        };
    }

    public static int compare(String left, String right) {
        int[] a = segments(left);
        int[] b = segments(right);
        int length = Math.max(a.length, b.length);
        for (int index = 0; index < length; index++) {
            int x = index < a.length ? a[index] : 0;
            int y = index < b.length ? b[index] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    static int[] segments(String version) {
        String normalized = normalize(version);
        if (normalized.isEmpty()) {
            return new int[0];
        }
        String[] parts = normalized.split("\\.");
        int[] result = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            result[index] = leadingNumber(parts[index]);
        }
        return result;
    }

    private static String normalize(String version) {
        if (version == null) {
            return "";
        }
        String text = version.trim();
        int cut = text.length();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character != '.' && !Character.isDigit(character)) {
                cut = index;
                break;
            }
        }
        return text.substring(0, cut);
    }

    private static int leadingNumber(String part) {
        int index = 0;
        while (index < part.length() && Character.isDigit(part.charAt(index))) {
            index++;
        }
        if (index == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(part.substring(0, index));
        } catch (NumberFormatException overflow) {
            return Integer.MAX_VALUE;
        }
    }
}
