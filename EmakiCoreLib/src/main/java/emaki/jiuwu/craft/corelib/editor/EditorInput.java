package emaki.jiuwu.craft.corelib.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputRequest;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputResult;
import emaki.jiuwu.craft.corelib.api.dialog.DialogDefinition;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.dialog.DialogService;
import emaki.jiuwu.craft.corelib.service.AbstractMessageService;

public final class EditorInput {

    public enum Channel {
        DIALOG,
        CHAT
    }

    private static final String DIALOG_ID = "emaki_editor_input";
    private static final long TIMEOUT_SECONDS = 60L;
    private static final List<String> CANCEL_KEYWORDS = List.of("cancel", "取消");

    private final JavaPlugin plugin;
    private final ChatInputService chatInput;
    private final AbstractMessageService messages;

    public EditorInput(JavaPlugin plugin, ChatInputService chatInput, AbstractMessageService messages) {
        this.plugin = plugin;
        this.chatInput = chatInput;
        this.messages = messages;
    }

    public Channel promptText(Player player,
            String title,
            String hint,
            String label,
            String initialValue,
            String chatPrompt,
            Consumer<String> callback) {
        promptChat(player, hint, chatPrompt, callback);
        return Channel.CHAT;
    }

    public void promptChat(Player player, String hint, String chatPrompt, Consumer<String> callback) {
        messages.send(player, chatPrompt);
        if (hint != null && !hint.isBlank()) {
            messages.send(player, hint);
        }
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
            DialogService dialog = EmakiCoreLibPlugin.lookup().dialogService();
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
}
