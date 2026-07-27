package emaki.jiuwu.craft.storage.config;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.yaml.YamlSection;

/**
 * 自定义数量与搜索关键词的输入方式。
 *
 * <p>对话框需要客户端 1.21.6 及以上并且 CoreLib 启用了该能力；
 * {@code auto} 会在不可用时自动回退聊天输入，因此聊天输入的实现必须保留。
 */
public record InputModeConfig(Mode mode) {

    public InputModeConfig {
        mode = mode == null ? Mode.AUTO : mode;
    }

    /** 输入方式。 */
    public enum Mode {
        /** 对话框可用则用对话框，否则聊天输入。 */
        AUTO,
        /** 强制对话框；不可用时该交互被禁用。 */
        DIALOG,
        /** 强制聊天输入。 */
        CHAT;

        public static Mode parse(String raw, Mode fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "auto" -> AUTO;
                case "dialog" -> DIALOG;
                case "chat" -> CHAT;
                default -> fallback;
            };
        }
    }

    public static InputModeConfig defaults() {
        return new InputModeConfig(Mode.AUTO);
    }

    public static InputModeConfig fromConfig(YamlSection section) {
        if (section == null) {
            return defaults();
        }
        return new InputModeConfig(Mode.parse(section.getString("input_mode", null), Mode.AUTO));
    }

    public boolean allowsDialog() {
        return mode != Mode.CHAT;
    }

    public boolean allowsChat() {
        return mode != Mode.DIALOG;
    }
}
