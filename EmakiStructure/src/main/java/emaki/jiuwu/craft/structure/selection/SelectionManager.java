package emaki.jiuwu.craft.structure.selection;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SelectionManager {

    private static final long SWEEP_PERIOD_TICKS = 20L;

    private final EmakiStructurePlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final Supplier<Duration> sessionTimeout;
    private final SelectionHighlighter highlighter;
    private final Map<UUID, SelectionSession> sessions = new ConcurrentHashMap<>();

    private TaskToken sweepToken;

    public SelectionManager(EmakiStructurePlugin plugin,
            ExecutionDispatcher dispatcher,
            Supplier<Duration> sessionTimeout,
            SelectionHighlighter highlighter) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.sessionTimeout = sessionTimeout;
        this.highlighter = highlighter;
    }

    public void startSweep() {
        if (sweepToken != null) {
            return;
        }
        sweepToken = dispatcher.runGlobalTimer(plugin, this::sweepExpired, SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
    }

    public SelectionSession session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new SelectionSession(
                player.getWorld().getName()));
    }

    public SelectionSession sessionFor(Player player, Consumer<String> onWorldSwitch) {
        SelectionSession existing = sessions.get(player.getUniqueId());
        if (existing != null && !existing.worldName().equals(player.getWorld().getName())) {
            sessions.remove(player.getUniqueId());
            highlighter.destroy(player);
            onWorldSwitch.accept(existing.worldName());
            existing = null;
        }
        if (existing != null) {
            existing.touch();
            return existing;
        }
        return session(player);
    }

    public SelectionSession boundSession(Player player) {
        SelectionSession existing = sessions.get(player.getUniqueId());
        if (existing == null) {
            return null;
        }
        if (!existing.worldName().equals(player.getWorld().getName())) {
            clear(player);
            return null;
        }
        existing.touch();
        return existing;
    }

    public void refresh(Player player, SelectionSession session) {
        highlighter.refresh(player, session);
    }

    public void clear(Player player) {
        SelectionSession removed = sessions.remove(player.getUniqueId());
        if (removed != null) {
            highlighter.destroy(player);
        }
    }

    public void handleQuit(Player player) {
        clear(player);
    }

    public void sweepExpired() {
        Duration timeout = sessionTimeout.get();
        long timeoutNanos = timeout.toNanos();
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, SelectionSession> entry : sessions.entrySet()) {
            if (entry.getValue().idleNanos() >= timeoutNanos) {
                expired.add(entry.getKey());
            }
        }
        for (UUID id : expired) {
            sessions.remove(id);
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                highlighter.destroy(player);
            }
        }
    }

    public void clearAllSessions() {
        for (UUID id : sessions.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                highlighter.destroy(player);
            }
        }
        sessions.clear();
    }

    public void close() {
        if (sweepToken != null) {
            sweepToken.cancel();
            sweepToken = null;
        }
        for (UUID id : sessions.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                highlighter.destroy(player);
            }
        }
        sessions.clear();
    }
}
