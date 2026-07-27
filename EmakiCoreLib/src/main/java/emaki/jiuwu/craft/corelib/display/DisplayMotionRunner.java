package emaki.jiuwu.craft.corelib.display;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;

/**
 * 驱动展示实体运动的关键帧发生器。
 *
 * <p>用**一条**可撤销的全局 timer 统一推进所有运动，而不是为每个关键帧预排延迟任务。
 * 这是正确性要求：伤害飘字会在合并窗口内对同一 key 重新 upsert，
 * 预排的延迟任务无法撤销，旧关键帧会继续写入并让画面跳变。
 *
 * <p>timer 在首个运动注册时懒启动，最后一个运动结束后自动取消，空闲期零开销。
 */
public final class DisplayMotionRunner {

    /** 关键帧回调。 */
    @FunctionalInterface
    public interface FrameSink {

        /**
         * 接收一个关键帧。
         *
         * @param interpolationTicks 客户端插值时长(tick)，等于关键帧间隔
         * @param translation        相对实体位置的位移偏移
         * @param scaleFactor        缩放系数，乘在 profile 原始缩放上
         */
        void accept(int interpolationTicks, DisplayGeometry.Vector3 translation, double scaleFactor);
    }

    private final Plugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final Map<String, MotionState> activeMotions = new ConcurrentHashMap<>();
    private volatile TaskHandle tickTask;

    public DisplayMotionRunner(Plugin plugin, ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
    }

    /**
     * 开始或重启一条运动。
     *
     * <p>同 key 已在运动中时重置为从头开始，对应「重新抛出」语义。
     *
     * @param runtimeKey 展示实体的运行时 key
     * @param motion     运动参数；{@code null} 或非激活时等价于 {@link #cancel(String)}
     * @param frameSink  关键帧回调。首帧在调用 {@code start} 的线程上同步触发，
     *                   其余帧在全局线程触发，因此回调内部必须自行切换到写目标所属线程
     */
    public void start(String runtimeKey, DisplayMotion motion, FrameSink frameSink) {
        if (runtimeKey == null || frameSink == null) {
            return;
        }
        if (motion == null || !motion.isActive()) {
            cancel(runtimeKey);
            return;
        }
        activeMotions.put(runtimeKey, new MotionState(motion, frameSink));
        // 起始状态必须瞬时落位：出场缩放若走插值，会先从 1 缩到 pop_from 再放大，形成反向抖动。
        frameSink.accept(0, motion.translationAt(0), motion.scaleFactorAt(0));
        ensureTicking();
    }

    /** 停止一条运动。 */
    public void cancel(String runtimeKey) {
        if (runtimeKey == null) {
            return;
        }
        activeMotions.remove(runtimeKey);
    }

    /** 停止全部运动并释放 timer。 */
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

    /**
     * 空闲时释放 timer。
     *
     * <p>必须在锁内复查是否真的为空：{@link #start} 可能在别的线程（Folia 的 region 线程）
     * 刚注册了新运动，此时若直接取消 timer，那条运动会停在首帧不动。
     */
    private synchronized void stopTickingIfIdle() {
        if (activeMotions.isEmpty()) {
            stopTicking();
        }
    }

    private synchronized void stopTicking() {
        TaskHandle handle = tickTask;
        tickTask = null;
        if (handle == null) {
            return;
        }
        try {
            handle.cancel();
        } catch (RuntimeException _) {
            // 任务可能已结束，忽略
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
            // 曲线采样点独立于 elapsed 累加：瞬时落位帧占用了第 0 tick，
            // 若直接用 elapsed 采样，首帧会跨越两个步长而在起步处加速。
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

    /**
     * 一条运动的进度。
     *
     * <p>只在全局线程读写计数字段，因此用普通字段而非原子类型。
     */
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
