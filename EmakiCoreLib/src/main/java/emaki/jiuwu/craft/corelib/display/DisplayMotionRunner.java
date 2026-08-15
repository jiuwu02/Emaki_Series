package emaki.jiuwu.craft.corelib.display;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class DisplayMotionRunner {

    @FunctionalInterface
    public interface FrameSink {

        void accept(int interpolationTicks, DisplayGeometry.Vector3 translation, double scaleFactor);
    }

    private final Plugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final Map<String, MotionState> activeMotions = new ConcurrentHashMap<>();
    private volatile TaskToken tickTask;

    public DisplayMotionRunner(Plugin plugin, ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
    }

    public void start(String runtimeKey, DisplayMotion motion, FrameSink frameSink) {
        if (runtimeKey == null || frameSink == null) {
            return;
        }
        if (motion == null || !motion.isActive()) {
            cancel(runtimeKey);
            return;
        }
        activeMotions.put(runtimeKey, new MotionState(motion, frameSink));

        frameSink.accept(0, motion.translationAt(0), motion.scaleFactorAt(0));
        ensureTicking();
    }

    public void cancel(String runtimeKey) {
        if (runtimeKey == null) {
            return;
        }
        activeMotions.remove(runtimeKey);
    }

    public void shutdown() {
        activeMotions.clear();
        stopTicking();
    }

    private synchronized void ensureTicking() {
        if (tickTask != null || activeMotions.isEmpty()) {
            return;
        }
        tickTask = executionDispatcher.runGlobalTimer(plugin, this::tick, 1L, 1L);
    }

    private synchronized void stopTickingIfIdle() {
        if (activeMotions.isEmpty()) {
            stopTicking();
        }
    }

    private synchronized void stopTicking() {
        TaskToken handle = tickTask;
        tickTask = null;
        if (handle == null) {
            return;
        }
        try {
            handle.cancel();
        } catch (RuntimeException _) {

        }
    }

    private void tick() {
        for (Map.Entry<String, MotionState> entry : Map.copyOf(activeMotions).entrySet()) {
            MotionState state = entry.getValue();
            state.elapsedTicks++;
            DisplayMotion motion = state.motion;
            if (state.elapsedTicks >= motion.durationTicks()) {
                activeMotions.remove(entry.getKey(), state);
                continue;
            }
            if (state.elapsedTicks < state.nextFrameTick) {
                continue;
            }

            state.curveTick += motion.stepTicks();
            state.nextFrameTick = state.elapsedTicks + motion.stepTicks();
            state.frameSink.accept(
                    motion.stepTicks(),
                    motion.translationAt(state.curveTick),
                    motion.scaleFactorAt(state.curveTick)
            );
        }
        stopTickingIfIdle();
    }

    private static final class MotionState {

        private final DisplayMotion motion;
        private final FrameSink frameSink;
        private int elapsedTicks;
        private int nextFrameTick = 1;
        private int curveTick;

        private MotionState(DisplayMotion motion, FrameSink frameSink) {
            this.motion = motion;
            this.frameSink = frameSink;
        }
    }
}
