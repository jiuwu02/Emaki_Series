package emaki.jiuwu.craft.corelib.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.command.CommandTabHelper}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具与契约类型改由 {@code emaki-corelib-api}
 * 提供。此处保留全部 5 个 public static 方法签名并逐一委托，
 * 旧调用点行为完全不变。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.command.CommandTabHelper}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class CommandTabHelper {

    private CommandTabHelper() {
    }

    public static List<String> filterByPrefix(Collection<String> candidates, String prefix) {
        return emaki.jiuwu.craft.corelib.api.command.CommandTabHelper.filterByPrefix(candidates, prefix);
    }

    public static List<String> completeOnlinePlayers(String prefix) {
        return emaki.jiuwu.craft.corelib.api.command.CommandTabHelper.completeOnlinePlayers(prefix);
    }

    public static List<String> completeSubcommands(Collection<String> subcommands, String prefix) {
        return emaki.jiuwu.craft.corelib.api.command.CommandTabHelper.completeSubcommands(subcommands, prefix);
    }

    public static List<String> completeLiterals(String prefix, String... values) {
        return emaki.jiuwu.craft.corelib.api.command.CommandTabHelper.completeLiterals(prefix, values);
    }

    public static List<String> completeIntRange(String prefix, int min, int max) {
        return emaki.jiuwu.craft.corelib.api.command.CommandTabHelper.completeIntRange(prefix, min, max);
    }
}
