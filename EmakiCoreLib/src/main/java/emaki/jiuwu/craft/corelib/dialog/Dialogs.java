package emaki.jiuwu.craft.corelib.dialog;

import java.util.List;
import emaki.jiuwu.craft.corelib.api.dialog.DialogDefinition;
import java.util.ArrayList;

public final class Dialogs {

    private Dialogs() {
    }

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
