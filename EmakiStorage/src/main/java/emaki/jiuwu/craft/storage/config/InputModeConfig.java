package emaki.jiuwu.craft.storage.config;

import java.util.Locale;
import java.util.function.Consumer;

import emaki.jiuwu.craft.corelib.api.dialog.DialogDefinition;
import emaki.jiuwu.craft.corelib.dialog.DialogDefinitions;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

/**
 * 单个交互的输入方式与对话框定义。
 *
 * <p>自定义取出数量与搜索关键词各自持有一份，因此两者可以分别使用聊天输入或对话框。
 *
 * <p>对话框需要客户端 1.21.6 及以上并且 CoreLib 启用了该能力；
 * {@code auto} 会在不可用时自动回退聊天输入，因此聊天输入的实现必须保留。
 *
 * <p>{@code dialog} 里的文本位置存的是语言键名，展示时才解析为文案，
 * 这样切换语言或重载语言文件后对话框会跟随变化。
 */
public record InputModeConfig(Mode mode, DialogDefinition dialog, String inputKey) {

    public InputModeConfig {
        mode = mode == null ? Mode.AUTO : mode;
        inputKey = inputKey == null ? "" : inputKey;
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

    public static InputModeConfig defaults(String inputKey) {
        return new InputModeConfig(Mode.AUTO, null, inputKey);
    }

    /**
     * 读取一个交互段的 {@code mode} 与 {@code dialog}。
     *
     * @param section  交互段，例如 {@code behavior.withdraw_prompt}
     * @param dialogId 对话框 id，仅用于日志与提交标识
     * @param inputKey 业务需要的输入项 key
     * @param issues   配置问题接收者，可为 {@code null}
     * @return 解析结果；段缺失时为该交互的默认值
     */
    public static InputModeConfig fromConfig(YamlSection section,
            String dialogId,
            String inputKey,
            Consumer<String> issues) {
        if (section == null) {
            return defaults(inputKey);
        }
        Mode mode = Mode.parse(section.getString("mode", null), Mode.AUTO);
        DialogDefinition dialog = DialogDefinitions.parse(dialogId, section.getSection("dialog"),
                issue -> report(issues, dialogId, issue));
        InputModeConfig config = new InputModeConfig(mode, dialog, inputKey);
        if (mode != Mode.CHAT && !config.dialogUsable()) {
            report(issues, dialogId, dialog == null
                    ? "no dialog configured, "
                            + (mode == Mode.DIALOG ? "so this interaction is disabled." : "using chat input.")
                    : "needs an input with key '" + inputKey + "' and at least one button, "
                            + (mode == Mode.DIALOG ? "so this interaction is disabled." : "using chat input."));
        }
        return config;
    }

    public boolean allowsDialog() {
        return mode != Mode.CHAT;
    }

    public boolean allowsChat() {
        return mode != Mode.DIALOG;
    }

    /**
     * 判断配置出的对话框是否真的能用来完成该交互。
     *
     * <p>服主可能删掉输入框、改掉 key 或删掉按钮；这些情况下对话框能弹出却取不到值，
     * 因此在这里判定为不可用，由调用方按 {@link #mode()} 回退或禁用。
     *
     * @return 定义存在、含所需 key 的输入项且至少有一个按钮时返回 {@code true}
     */
    public boolean dialogUsable() {
        if (dialog == null || dialog.buttons().isEmpty() || inputKey.isEmpty()) {
            return false;
        }
        for (DialogDefinition.Input input : dialog.inputs()) {
            if (inputKey.equals(input.key())) {
                return true;
            }
        }
        return false;
    }

    private static void report(Consumer<String> issues, String dialogId, String issue) {
        if (issues != null) {
            issues.accept("dialog '" + dialogId + "': " + issue);
        }
    }
}
