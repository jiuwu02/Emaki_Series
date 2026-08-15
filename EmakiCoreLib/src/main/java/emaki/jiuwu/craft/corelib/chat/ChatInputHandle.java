package emaki.jiuwu.craft.corelib.chat;

import java.util.Objects;

public final class ChatInputHandle {

    private final ChatInputService service;
    private final ChatInputService.PendingInput input;

    ChatInputHandle(ChatInputService service, ChatInputService.PendingInput input) {
        this.service = Objects.requireNonNull(service, "service");
        this.input = Objects.requireNonNull(input, "input");
    }

    public boolean cancel() {
        return service.cancel(input);
    }

    public boolean isActive() {
        return !input.isCompleted();
    }
}
