package emaki.jiuwu.craft.corelib.chat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.AsyncChatEvent;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputRequest;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputResult;

/**
 * 共享的聊天输入等待服务：让玩家在 GUI 之外通过聊天框提交一个值。
 *
 * <p>与 {@code GuiService} 同一模式——CoreLib 只提供类，业务插件各自
 * {@code new ChatInputService(...)} 并 {@code registerEvents} 为自己的监听器，
 * 停用时调用 {@link #close()}。CoreLib 不持有共享实例，pending 状态随各插件自身释放。</p>
 *
 * <p>提示文案由调用方自行发送；本服务只负责机制。</p>
 *
 * <p>{@code AsyncChatEvent} 在异步线程触发，因此 pending 表为并发表，
 * 回调统一调度回玩家的 entity owner 线程；仅当该线程已不可调度时才就地执行。
 * 提交/取消/超时/退出/被顶替五条路径共用同一个 CAS，保证回调恰好一次。</p>
 */
public final class ChatInputService implements Listener {

    private final Plugin owner;
    private final ExecutionDispatcher executionDispatcher;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public ChatInputService(Plugin owner, ExecutionDispatcher executionDispatcher) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.executionDispatcher = Objects.requireNonNull(executionDispatcher, "executionDispatcher");
    }

    /**
     * 注册一次性输入等待。同一玩家重复注册时，旧 pending 先以
     * {@link ChatInputResult.Status#CANCELLED} 结束。
     *
     * @return 本次 pending 的句柄；若超时任务无法调度，pending 已以 CANCELLED 结束且句柄不再活动
     */
    public ChatInputHandle await(ChatInputRequest request) {
        Objects.requireNonNull(request, "request");
        Player player = request.player();
        PendingInput input = new PendingInput(request);
        debug(player, "common.chat_input.await_registered", ChatInputDebugSupport.requestFields(request));
        PendingInput previous = pending.put(player.getUniqueId(), input);
        if (previous != null) {
            debug(player, "common.chat_input.await_replaced_previous",
                    ChatInputDebugSupport.requestFields(previous.request));
            finish(previous, ChatInputResult.cancelled(), true);
        }
        if (request.timeoutSeconds() > 0L) {
            scheduleTimeout(input);
        }
        return new ChatInputHandle(this, input);
    }

    /**
     * 撤销该玩家当前的 pending，回调以 {@link ChatInputResult.Status#CANCELLED} 结束。
     *
     * @return 是否确实撤销了一个 pending
     */
    public boolean cancel(Player player) {
        if (player == null) {
            return false;
        }
        PendingInput input = pending.get(player.getUniqueId());
        if (input == null) {
            return false;
        }
        debug(player, "common.chat_input.cancelled_by_caller", ChatInputDebugSupport.requestFields(input.request));
        return finish(input, ChatInputResult.cancelled(), true);
    }

    /**
     * 停用时清理全部 pending，各自以 {@link ChatInputResult.Status#CANCELLED} 结束。
     *
     * <p>停用阶段已无法再调度任务，因此回调在调用线程就地执行；
     * 处理 {@code CANCELLED} 的回调分支不应访问 Bukkit 状态。</p>
     */
    public void close() {
        for (PendingInput input : List.copyOf(pending.values())) {
            debug(input.request.player(), "common.chat_input.closed_pending_cancelled",
                    ChatInputDebugSupport.requestFields(input.request));
            finish(input, ChatInputResult.cancelled(), false);
        }
        pending.clear();
    }

    /**
     * 以最低优先级拦截聊天。命中 pending 时取消事件，输入不进入公共聊天。
     *
     * <p>本方法在异步聊天线程执行，因此只做纯文本转换与 pending 查表，
     * 回调统一交给 {@link #finish} 调度回玩家的 entity owner 线程。</p>
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingInput input = pending.get(player.getUniqueId());
        if (input == null) {
            debug(player, "common.chat_input.chat_skipped_no_pending");
            return;
        }
        event.setCancelled(true);
        String text = MiniMessages.plainText(event.message());
        if (input.request.isCancelKeyword(text)) {
            debug(player, "common.chat_input.cancel_keyword_matched",
                    ChatInputDebugSupport.requestFields(input.request, Map.of("input", text)));
            finish(input, ChatInputResult.cancelled(), true);
            return;
        }
        debug(player, "common.chat_input.chat_submitted",
                ChatInputDebugSupport.requestFields(input.request, Map.of("input", text)));
        finish(input, ChatInputResult.submitted(text), true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        finishOnDisconnect(event.getPlayer(), "common.chat_input.player_quit_cancelled");
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        finishOnDisconnect(event.getPlayer(), "common.chat_input.player_kick_cancelled");
    }

    /** 供 {@link ChatInputHandle} 撤销它自己那一次 pending。 */
    boolean cancel(PendingInput input) {
        if (input == null) {
            return false;
        }
        debug(input.request.player(), "common.chat_input.cancelled_by_handle",
                ChatInputDebugSupport.requestFields(input.request));
        return finish(input, ChatInputResult.cancelled(), true);
    }

    private void finishOnDisconnect(Player player, String langKey) {
        PendingInput input = pending.get(player.getUniqueId());
        if (input == null) {
            return;
        }
        debug(player, langKey, ChatInputDebugSupport.requestFields(input.request));
        finish(input, ChatInputResult.quit(), false);
    }

    /**
     * 结束一条 pending：CAS 胜出者才移除记录、取消超时任务并投递回调。
     *
     * @param dispatch {@code true} 时把回调调度回玩家的 entity owner 线程；
     *                 {@code false} 用于本身已在该线程的路径（退出/踢出/停用）
     * @return 本次调用是否胜出 CAS
     */
    private boolean finish(PendingInput input, ChatInputResult result, boolean dispatch) {
        if (!input.completed.compareAndSet(false, true)) {
            return false;
        }
        pending.remove(input.request.player().getUniqueId(), input);
        TaskHandle timeout = input.timeoutHandle;
        if (timeout != null) {
            timeout.cancel();
        }
        if (dispatch) {
            dispatch(input, result);
        } else {
            deliver(input, result);
        }
        return true;
    }

    private void dispatch(PendingInput input, ChatInputResult result) {
        Player player = input.request.player();
        AtomicBoolean delivered = new AtomicBoolean();
        Runnable task = () -> {
            if (delivered.compareAndSet(false, true)) {
                deliver(input, result);
            }
        };
        try {
            if (executionDispatcher.runEntity(input.request.owner(), player, task, task) != null) {
                return;
            }
        } catch (Throwable throwable) {
            debug(player, "common.chat_input.callback_dispatch_failed",
                    ChatInputDebugSupport.errorFields(throwable, ChatInputDebugSupport.requestFields(input.request)));
        }
        debug(player, "common.chat_input.callback_dispatch_unavailable",
                ChatInputDebugSupport.requestFields(input.request));
        task.run();
    }

    private void deliver(PendingInput input, ChatInputResult result) {
        try {
            input.request.callback().accept(result);
        } catch (Throwable throwable) {
            debug(input.request.player(), "common.chat_input.callback_failed",
                    ChatInputDebugSupport.errorFields(throwable, ChatInputDebugSupport.requestFields(
                            input.request,
                            Map.of("status", result.status().name())
                    )));
            owner.getLogger().warning("Chat input callback failed for "
                    + input.request.player().getName()
                    + " (status=" + result.status().name() + "): "
                    + throwable.getClass().getSimpleName()
                    + ": " + throwable.getMessage());
        }
    }

    private void scheduleTimeout(PendingInput input) {
        Player player = input.request.player();
        Runnable task = () -> {
            debug(player, "common.chat_input.timed_out", ChatInputDebugSupport.requestFields(input.request));
            finish(input, ChatInputResult.timeout(), false);
        };
        TaskHandle handle;
        try {
            handle = executionDispatcher.runEntityLater(
                    input.request.owner(),
                    player,
                    task,
                    () -> finish(input, ChatInputResult.cancelled(), false),
                    input.request.timeoutSeconds() * 20L);
        } catch (Throwable throwable) {
            debug(player, "common.chat_input.timeout_schedule_failed",
                    ChatInputDebugSupport.errorFields(throwable, ChatInputDebugSupport.requestFields(input.request)));
            finish(input, ChatInputResult.cancelled(), true);
            return;
        }
        if (handle == null) {
            debug(player, "common.chat_input.timeout_schedule_rejected",
                    ChatInputDebugSupport.requestFields(input.request));
            finish(input, ChatInputResult.cancelled(), true);
            return;
        }
        input.timeoutHandle = handle;
        if (input.isCompleted()) {
            handle.cancel();
        }
    }

    private void debug(Player player, String langKey) {
        ChatInputDebugSupport.log(owner, player, langKey);
    }

    private void debug(Player player, String langKey, Map<String, ?> replacements) {
        ChatInputDebugSupport.log(owner, player, langKey, replacements);
    }

    /** 单次 pending 记录。五条结束路径共用 {@code completed} 这一个 CAS，保证回调恰好一次。 */
    static final class PendingInput {

        private final ChatInputRequest request;
        private final AtomicBoolean completed = new AtomicBoolean();
        private volatile TaskHandle timeoutHandle;

        private PendingInput(ChatInputRequest request) {
            this.request = request;
        }

        boolean isCompleted() {
            return completed.get();
        }
    }
}
