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
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputRequest;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputResult;

public final class ChatInputService implements Listener {

    private final Plugin owner;
    private final ExecutionDispatcher executionDispatcher;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public ChatInputService(Plugin owner, ExecutionDispatcher executionDispatcher) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.executionDispatcher = Objects.requireNonNull(executionDispatcher, "executionDispatcher");
    }

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

    public void close() {
        for (PendingInput input : List.copyOf(pending.values())) {
            debug(input.request.player(), "common.chat_input.closed_pending_cancelled",
                    ChatInputDebugSupport.requestFields(input.request));
            finish(input, ChatInputResult.cancelled(), false);
        }
        pending.clear();
    }

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

    private boolean finish(PendingInput input, ChatInputResult result, boolean dispatch) {
        if (!input.completed.compareAndSet(false, true)) {
            return false;
        }
        pending.remove(input.request.player().getUniqueId(), input);
        TaskToken timeout = input.timeoutHandle;
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
        TaskToken handle;
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

    static final class PendingInput {

        private final ChatInputRequest request;
        private final AtomicBoolean completed = new AtomicBoolean();
        private volatile TaskToken timeoutHandle;

        private PendingInput(ChatInputRequest request) {
            this.request = request;
        }

        boolean isCompleted() {
            return completed.get();
        }
    }
}
