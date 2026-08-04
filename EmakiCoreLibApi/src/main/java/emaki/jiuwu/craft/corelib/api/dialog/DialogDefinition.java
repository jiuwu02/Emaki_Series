package emaki.jiuwu.craft.corelib.api.dialog;

import java.util.List;
import java.util.Locale;

/**
 * 一份对话框定义的不可变模型。
 *
 * <p>只描述配置意图，不持有任何 Bukkit 或 Paper 运行时对象；
 * 转换为原版对话框由 {@code DialogService} 负责。
 */
public record DialogDefinition(
        String id,
        Type type,
        String title,
        String externalTitle,
        boolean canCloseWithEscape,
        boolean pause,
        AfterAction afterAction,
        List<Body> body,
        List<Input> inputs,
        List<Button> buttons,
        Button exitButton,
        int columns) {

    public DialogDefinition {
        body = body == null ? List.of() : List.copyOf(body);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        buttons = buttons == null ? List.of() : List.copyOf(buttons);
        type = type == null ? Type.NOTICE : type;
        afterAction = afterAction == null ? AfterAction.CLOSE : afterAction;
        columns = Math.max(1, columns);
    }

    /** 对话框类型。 */
    public enum Type {
        /** 单按钮通知。 */
        NOTICE,
        /** 是/否确认。 */
        CONFIRMATION,
        /** 多按钮。 */
        MULTI_ACTION;

        public static Type parse(String raw, Type fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "notice" -> NOTICE;
                case "confirmation", "confirm" -> CONFIRMATION;
                case "multi_action", "multi", "multiaction" -> MULTI_ACTION;
                default -> fallback;
            };
        }
    }

    /** 对话框关闭后的行为。 */
    public enum AfterAction {
        CLOSE,
        NONE,
        WAIT_FOR_RESPONSE;

        public static AfterAction parse(String raw, AfterAction fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "close" -> CLOSE;
                case "none" -> NONE;
                case "wait_for_response", "wait" -> WAIT_FOR_RESPONSE;
                default -> fallback;
            };
        }
    }

    /** 正文条目。{@code item} 为空表示纯文本条目。 */
    public record Body(String text, String item, int width) {

        public Body {
            width = width <= 0 ? 0 : Math.clamp(width, 1, 1024);
        }
    }

    /** 输入控件。 */
    public record Input(
            InputType type,
            String key,
            String label,
            boolean labelVisible,
            String initial,
            int maxLength,
            int width,
            float start,
            float end,
            float step,
            boolean initialBoolean,
            String onTrue,
            String onFalse,
            List<Option> options) {

        public Input {
            options = options == null ? List.of() : List.copyOf(options);
            type = type == null ? InputType.TEXT : type;
            width = width <= 0 ? 0 : Math.clamp(width, 1, 1024);
        }

        /** 单选项条目。 */
        public record Option(String id, String display, boolean initial) {
        }
    }

    /** 输入控件类型。 */
    public enum InputType {
        TEXT,
        BOOLEAN,
        NUMBER_RANGE,
        SINGLE_OPTION;

        public static InputType parse(String raw, InputType fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "text" -> TEXT;
                case "boolean", "bool" -> BOOLEAN;
                case "number_range", "number", "range" -> NUMBER_RANGE;
                case "single_option", "option", "options" -> SINGLE_OPTION;
                default -> fallback;
            };
        }
    }

    /** 按钮。 */
    public record Button(String label, String tooltip, int width, Action action) {

        public Button {
            width = width <= 0 ? 0 : Math.clamp(width, 1, 1024);
        }
    }

    /** 按钮动作。 */
    public record Action(ActionType type, String value) {

        public Action {
            type = type == null ? ActionType.NONE : type;
        }
    }

    /** 按钮动作类型。 */
    public enum ActionType {
        /** 无动作，仅关闭。 */
        NONE,
        /** 以玩家身份执行命令模板，可引用输入项。 */
        COMMAND_TEMPLATE,
        /** 打开链接。 */
        OPEN_URL,
        /** 以玩家身份运行命令。 */
        RUN_COMMAND;

        public static ActionType parse(String raw, ActionType fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "none" -> NONE;
                case "command_template", "template" -> COMMAND_TEMPLATE;
                case "open_url", "url", "link" -> OPEN_URL;
                case "run_command", "command" -> RUN_COMMAND;
                default -> fallback;
            };
        }
    }
}
