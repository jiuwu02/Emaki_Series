package emaki.jiuwu.craft.corelib.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.text.MiniMessages}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具改由 {@code emaki-corelib-api} 契约 artifact 提供。
 * 此处保留全部 16 个方法签名并逐一委托，旧调用点行为完全不变。
 *
 * <p><strong>注意</strong>：{@code configureDefaultNoItalic} 的全局状态现由 api 侧持有，
 * 通过本类或 api 类设置都作用于同一份状态，不存在两份配置。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.text.MiniMessages}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class MiniMessages {

    private MiniMessages() {
    }

    public static void configureDefaultNoItalic(boolean enabled) {
        emaki.jiuwu.craft.corelib.api.text.MiniMessages.configureDefaultNoItalic(enabled);
    }

    public static boolean defaultNoItalic() {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.defaultNoItalic();
    }

    public static Component parse(String text) {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.parse(text);
    }

    public static Object componentObject(String text) {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.componentObject(text);
    }

    public static Component read(String text) {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.read(text);
    }

    public static String serialize(Component component) {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.serialize(component);
    }

    public static String legacy(Component component) {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.legacy(component);
    }

    public static String legacyText(String text) {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.legacyText(text);
    }

    public static Component legacyRead(String text) {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.legacyRead(text);
    }

    public static String plain(Component component) {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.plain(component);
    }

    public static String toMiniMessage(Object value) {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.toMiniMessage(value);
    }

    public static String plainText(String text) {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.plainText(text);
    }

    public static String plainText(Object value) {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.plainText(value);
    }

    public static String escape(String text) {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.escape(text);
    }

    public static String withHoverText(String content, String hoverText) {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.withHoverText(content, hoverText);
    }

    public static MiniMessage miniMessage() {
        return emaki.jiuwu.craft.corelib.api.text.MiniMessages.miniMessage();
    }
}
