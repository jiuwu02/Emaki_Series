package emaki.jiuwu.craft.corelib.chat;

import java.util.Objects;

/**
 * {@link ChatInputService#await(ChatInputRequest)} 返回的单次 pending 句柄。
 *
 * <p>句柄只代表注册它的那一次等待。若该玩家的 pending 已被后续 {@code await} 顶替，
 * 旧句柄的 {@link #cancel()} 返回 {@code false} 且不会影响新的 pending。</p>
 */
public final class ChatInputHandle {

    private final ChatInputService service;
    private final ChatInputService.PendingInput input;

    ChatInputHandle(ChatInputService service, ChatInputService.PendingInput input) {
        this.service = Objects.requireNonNull(service, "service");
        this.input = Objects.requireNonNull(input, "input");
    }

    /**
     * 撤销这一次 pending，回调以 {@link ChatInputResult.Status#CANCELLED} 结束。
     *
     * @return 本次调用是否真正结束了该 pending；已结束时返回 {@code false}
     */
    public boolean cancel() {
        return service.cancel(input);
    }

    /** 该 pending 是否仍在等待输入。 */
    public boolean isActive() {
        return !input.isCompleted();
    }
}
