package emaki.jiuwu.craft.corelib.item;

import org.bukkit.Bukkit;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Minecraft 服务器版本比较工具。
 *
 * <p>需要同时兼容两套版本命名：旧式 {@code 1.21.x} 与新式 {@code 26.x}（Mojang 在 1.21.11 之后
 * 改用年份式命名，{@code 26.1} 晚于 {@code 1.21.11}）。比较时统一按数字段逐段比较，缺失段补 0，
 * 因此 {@code 26.1 > 1.21.11} 会自动成立，无需为两套命名分别写分支。
 */
public final class MinecraftServerVersions {

    private MinecraftServerVersions() {
    }

    /** 当前运行服务器的 Minecraft 版本；无法获取时返回空串。 */
    public static String currentServerVersion() {
        try {
            return Bukkit.getServer() == null ? "" : Texts.toStringSafe(Bukkit.getServer().getMinecraftVersion());
        } catch (Throwable unavailable) {
            return "";
        }
    }

    /**
     * 判断服务器版本是否满足最低版本表达式（如 {@code ">=1.21.11"}）。
     * 支持 {@code >= > <= < =} 前缀，裸版本号按 {@code >=} 处理；表达式为空或服务器版本未知时一律返回 true。
     */
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

    /** 比较两个版本号，返回负数/0/正数。 */
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

    /** 去掉 {@code -R0.1-SNAPSHOT} 之类的后缀，仅保留版本号主体（数字与点）。 */
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
