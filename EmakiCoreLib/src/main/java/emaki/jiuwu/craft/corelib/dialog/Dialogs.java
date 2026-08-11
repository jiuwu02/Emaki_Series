package emaki.jiuwu.craft.corelib.dialog;

import java.util.List;
import emaki.jiuwu.craft.corelib.api.dialog.DialogDefinition;
import java.util.ArrayList;

/**
 * 常用程序化对话框的构造快捷方式。
 *
 * <p>业务侧多数只需要「一句说明 + 一个输入框 + 一个确认按钮」，
 * 直接手写 {@link DialogDefinition} 的十几个参数噪音太大。
 */
public final class Dialogs {

    private Dialogs() {
    }

    /**
     * 构造一个单文本输入的确认对话框。
     *
     * <p>数量类输入应当用文本而非数值滑块：滑块的取值是 {@code float}，
     * 装不下仓库这类需要 {@code long} 量级的数量。
     *
     * @param id           对话框 id，仅用于日志
     * @param title        标题
     * @param bodyLines    说明文本，每项一行
     * @param inputKey     输入项 key，提交时用它取值
     * @param inputLabel   输入项标签
     * @param initialValue 输入框初始值，可为空
     * @param maxLength    输入长度上限，小于等于 0 表示不限制
     * @param confirmLabel 确认按钮文本
     * @return 可直接交给 {@code DialogService.show(player, definition, handler)} 的定义
     */
    public static DialogDefinition textPrompt(String id,
            String title,
            List<String> bodyLines,
            String inputKey,
            String inputLabel,
            String initialValue,
            int maxLength,
            String confirmLabel) {
        List<DialogDefinition.Body> body = new ArrayList<>();
        if (bodyLines != null) {
            for (String line : bodyLines) {
                if (line != null && !line.isBlank()) {
                    body.add(new DialogDefinition.Body(line, null, 0));
                }
            }
        }
        DialogDefinition.Input input = new DialogDefinition.Input(
                DialogDefinition.InputType.TEXT,
                inputKey,
                inputLabel,
                true,
                initialValue == null ? "" : initialValue,
                Math.max(0, maxLength),
                0,
                0F,
                0F,
                0F,
                false,
                "true",
                "false",
                List.of()
        );
        DialogDefinition.Button confirm = new DialogDefinition.Button(
                confirmLabel, null, 0, new DialogDefinition.Action(DialogDefinition.ActionType.NONE, id));
        return new DialogDefinition(
                id,
                DialogDefinition.Type.NOTICE,
                title,
                null,
                true,
                false,
                DialogDefinition.AfterAction.CLOSE,
                body,
                List.of(input),
                List.of(confirm),
                null,
                1
        );
    }
}
