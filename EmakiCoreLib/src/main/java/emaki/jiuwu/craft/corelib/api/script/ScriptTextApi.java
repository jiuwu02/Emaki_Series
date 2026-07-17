package emaki.jiuwu.craft.corelib.api.script;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.script.ScriptDeferredOperationQueue;
import emaki.jiuwu.craft.corelib.script.ScriptDeferredOperationQueue.OperationResult;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

public final class ScriptTextApi {

    private final Plugin sourcePlugin;
    private final ScriptDeferredOperationQueue deferredOperations;
    private final Server server;

    public ScriptTextApi() {
        this(null, null);
    }

    public ScriptTextApi(Plugin sourcePlugin) {
        this(sourcePlugin, null);
    }

    public ScriptTextApi(Plugin sourcePlugin, ScriptDeferredOperationQueue deferredOperations) {
        this.sourcePlugin = sourcePlugin;
        this.deferredOperations = deferredOperations;
        this.server = sourcePlugin == null ? null : sourcePlugin.getServer();
    }

    @HostAccess.Export
    public String string(Object value) {
        return Texts.toStringSafe(value);
    }

    @HostAccess.Export
    public boolean blank(String value) {
        return Texts.isBlank(value);
    }

    @HostAccess.Export
    public boolean notBlank(String value) {
        return Texts.isNotBlank(value);
    }

    @HostAccess.Export
    public String lower(String value) {
        return Texts.lower(value);
    }

    @HostAccess.Export
    public String normalizeId(String value) {
        return Texts.normalizeId(value);
    }

    @HostAccess.Export
    public Object component(String miniMessage) {
        return MiniMessages.parse(miniMessage);
    }

    @HostAccess.Export
    public String plain(String miniMessage) {
        return MiniMessages.plainText(miniMessage);
    }

    @HostAccess.Export
    public String legacy(String miniMessage) {
        return MiniMessages.legacyText(miniMessage);
    }

    @HostAccess.Export
    public void sendMini(Object target, String miniMessage) {
        if (!canDefer(miniMessage)) {
            return;
        }
        Component component = MiniMessages.parse(miniMessage);
        Object raw = unwrap(target);
        if (raw instanceof Player player) {
            enqueuePlayerMessage(player, component);
        } else if (raw instanceof CommandSender sender) {
            enqueueSenderMessage(sender, component);
        } else if (raw instanceof String name) {
            enqueueNamedPlayer("text:send-mini-name", name, player -> player.sendMessage(component));
        }
    }

    @HostAccess.Export
    public void broadcastMini(String miniMessage) {
        if (!canDefer(miniMessage) || server == null) {
            return;
        }
        Component component = MiniMessages.parse(miniMessage);
        deferredOperations.enqueue("text:broadcast-mini", () -> scheduleGlobal(() -> {
            List<Player> players = new ArrayList<>(server.getOnlinePlayers());
            List<CompletableFuture<OperationResult>> deliveries = new ArrayList<>(players.size());
            for (Player player : players) {
                deliveries.add(schedulePlayer(player, target -> target.sendMessage(component)));
            }
            CommandSender console = server.getConsoleSender();
            if (console != null) {
                console.sendMessage(component);
            }
            return awaitAll(deliveries);
        }));
    }

    @HostAccess.Export
    public void actionBar(Object target, String miniMessage) {
        if (!canDefer(miniMessage)) {
            return;
        }
        Component component = MiniMessages.parse(miniMessage);
        Object raw = unwrap(target);
        if (raw instanceof Player player) {
            enqueuePlayerActionBar(player, component);
        } else if (raw instanceof String name) {
            enqueueNamedPlayer("text:action-bar-name", name, player -> player.sendActionBar(component));
        }
    }

    private boolean canDefer(String miniMessage) {
        return sourcePlugin != null && deferredOperations != null && Texts.isNotBlank(miniMessage);
    }

    private Object unwrap(Object target) {
        if (target instanceof ScriptServerApi.ScriptEntityApi entityApi) {
            return entityApi.entity();
        }
        return target;
    }

    private void enqueueSenderMessage(CommandSender sender, Component component) {
        if (sender instanceof Player player) {
            enqueuePlayerMessage(player, component);
            return;
        }
        deferredOperations.enqueueGlobal("text:send-mini-sender", () -> sender.sendMessage(component));
    }

    private void enqueuePlayerMessage(Player player, Component component) {
        if (player != null) {
            deferredOperations.enqueueEntity("text:send-mini", player, entity -> {
                if (entity instanceof Player targetPlayer) {
                    targetPlayer.sendMessage(component);
                }
            });
        }
    }

    private void enqueuePlayerActionBar(Player player, Component component) {
        if (player != null) {
            deferredOperations.enqueueEntity("text:action-bar", player, entity -> {
                if (entity instanceof Player targetPlayer) {
                    targetPlayer.sendActionBar(component);
                }
            });
        }
    }

    private void enqueueNamedPlayer(String description, String name, Consumer<Player> operation) {
        if (server == null || Texts.isBlank(name)) {
            return;
        }
        String safeName = Texts.trim(name);
        deferredOperations.enqueue(description, () -> scheduleGlobal(() -> {
            Player player = server.getPlayerExact(safeName);
            return player == null
                    ? CompletableFuture.completedFuture(OperationResult.ok())
                    : schedulePlayer(player, operation);
        }));
    }

    private CompletionStage<OperationResult> scheduleGlobal(
            java.util.function.Supplier<? extends CompletionStage<OperationResult>> operation) {
        CompletableFuture<OperationResult> future = new CompletableFuture<>();
        try {
            FoliaSchedulerAdapter.runTask(sourcePlugin, () -> {
                try {
                    CompletionStage<OperationResult> stage = operation.get();
                    if (stage == null) {
                        future.complete(OperationResult.failure("Deferred text operation returned no completion stage."));
                        return;
                    }
                    stage.whenComplete((result, throwable) -> complete(future, result, throwable));
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private CompletableFuture<OperationResult> schedulePlayer(Player player, Consumer<Player> operation) {
        CompletableFuture<OperationResult> future = new CompletableFuture<>();
        try {
            FoliaSchedulerAdapter.runEntityTask(sourcePlugin, player, () -> {
                try {
                    operation.accept(player);
                    future.complete(OperationResult.ok());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private CompletableFuture<OperationResult> awaitAll(List<CompletableFuture<OperationResult>> futures) {
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(OperationResult.ok());
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(_ -> OperationResult.ok());
    }

    private void complete(CompletableFuture<OperationResult> future,
            OperationResult result,
            Throwable throwable) {
        Throwable failure = unwrapThrowable(throwable);
        if (failure != null) {
            future.completeExceptionally(failure);
        } else {
            future.complete(result == null
                    ? OperationResult.failure("Deferred text operation returned no result.")
                    : result);
        }
    }

    private Throwable unwrapThrowable(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
