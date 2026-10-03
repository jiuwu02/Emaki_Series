package emaki.jiuwu.craft.item.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.chat.ChatInputRequest;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputResult;
import emaki.jiuwu.craft.corelib.api.dialog.DialogDefinition;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.dialog.DialogService;
import emaki.jiuwu.craft.corelib.dialog.Dialogs;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemEditorInput {

    public enum Channel {
        DIALOG,
        CHAT
    }

    private static final String INPUT_KEY = "value";
    private static final String DIALOG_ID = "emaki_item_editor_input";
    private static final long TIMEOUT_SECONDS = 60L;
    private static final List<String> CANCEL_KEYWORDS = List.of("cancel", "取消");
    private static final int MAX_LENGTH = 4096;

    private final EmakiItemPlugin plugin;
    private final ChatInputService chatInput;

    public ItemEditorInput(EmakiItemPlugin plugin, ChatInputService chatInput) {
        this.plugin = plugin;
        this.chatInput = chatInput;
    }

    public Channel promptText(Player player,
            String title,
            String label,
            String initialValue,
            String chatPrompt,
            Consumer<String> callback) {
        if (tryDialog(player, title, label, initialValue, callback)) {
            return Channel.DIALOG;
        }
        promptChat(player, chatPrompt, callback);
        return Channel.CHAT;
    }

    public void promptChat(Player player, String chatPrompt, Consumer<String> callback) {
        plugin.messageService().send(player, chatPrompt);
        chatInput.await(new ChatInputRequest(plugin, player, TIMEOUT_SECONDS, CANCEL_KEYWORDS, result -> {
            if (result.status() == ChatInputResult.Status.SUBMITTED) {
                callback.accept(result.text() == null ? "" : result.text());
            }
        }));
    }

    public boolean confirm(Player player,
            String title,
            List<String> bodyLines,
            String confirmLabel,
            String cancelLabel,
            String confirmButtonId,
            String cancelButtonId,
            Consumer<String> buttonCallback) {
        try {
            DialogService dialog = plugin.coreLib().dialogService();
            if (dialog == null || !dialog.enabled()) {
                return false;
            }
            List<DialogDefinition.Body> body = new ArrayList<>();
            for (String line : bodyLines) {
                if (line != null && !line.isBlank()) {
                    body.add(new DialogDefinition.Body(line, null, 0));
                }
            }
            DialogDefinition definition = new DialogDefinition(
                    DIALOG_ID,
                    DialogDefinition.Type.CONFIRMATION,
                    title,
                    null,
                    true,
                    false,
                    DialogDefinition.AfterAction.CLOSE,
                    body,
                    List.of(),
                    List.of(
                            new DialogDefinition.Button(confirmLabel, null, 0,
                                    new DialogDefinition.Action(DialogDefinition.ActionType.NONE, confirmButtonId)),
                            new DialogDefinition.Button(cancelLabel, null, 0,
                                    new DialogDefinition.Action(DialogDefinition.ActionType.NONE, cancelButtonId))),
                    null,
                    2);
            return dialog.show(player, definition, (viewer, submission) ->
                    buttonCallback.accept(submission.buttonId() == null ? cancelButtonId : submission.buttonId()));
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private boolean tryDialog(Player player, String title, String label, String initialValue, Consumer<String> callback) {
        try {
            DialogService dialog = plugin.coreLib().dialogService();
            if (dialog == null || !dialog.enabled()) {
                return false;
            }
            DialogDefinition definition = Dialogs.textPrompt(
                    DIALOG_ID,
                    title,
                    List.of(),
                    INPUT_KEY,
                    label,
                    initialValue,
                    MAX_LENGTH,
                    title);
            return dialog.show(player, definition, (viewer, submission) -> {
                String text = submission.text(INPUT_KEY);
                callback.accept(text == null ? "" : text);
            });
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }
}
