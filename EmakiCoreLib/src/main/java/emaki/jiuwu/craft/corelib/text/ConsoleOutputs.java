package emaki.jiuwu.craft.corelib.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具与契约类型改由 {@code emaki-corelib-api}
 * 提供。此处保留全部 2 个 public static 方法签名并逐一委托，
 * 旧调用点行为完全不变。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class ConsoleOutputs {

    private ConsoleOutputs() {
    }

    public static void sendGradientAscii(JavaPlugin plugin, String asciiArt) {
        emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs.sendGradientAscii(plugin, asciiArt);
    }

    public static void sendGradientAscii(JavaPlugin plugin, String asciiArt, int startColor, int endColor) {
        emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs.sendGradientAscii(plugin, asciiArt, startColor, endColor);
    }
}
