package emaki.jiuwu.craft.storage.config;

import java.util.Locale;
import java.util.function.Consumer;

import emaki.jiuwu.craft.corelib.api.dialog.DialogDefinition;
import emaki.jiuwu.craft.corelib.dialog.DialogDefinitions;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record InputModeConfig(Mode mode, DialogDefinition dialog, String inputKey) {

    public InputModeConfig {
        mode = mode == null ? Mode.AUTO : mode;
        inputKey = inputKey == null ? "" : inputKey;
    }

    public enum Mode {

        AUTO,

        DIALOG,

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
