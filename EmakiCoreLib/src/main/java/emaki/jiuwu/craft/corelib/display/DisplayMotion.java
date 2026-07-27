package emaki.jiuwu.craft.corelib.display;

/**
 * 展示实体的运动轨迹。
 *
 * <p>轨迹是纯二次函数 {@code p(t) = v·t + ½·a·t²}，{@code t} 以 tick 计。
 * 结果写入 {@code transformation.translation}，即相对实体位置的偏移，
 * 由客户端在插值时长内平滑过渡，实体本身不移动，因此不产生传送开销。
 *
 * <p>缩放为三段线性：前 {@code popTicks} 从 {@code popFromScale} 放大到 1，
 * 末尾 {@code shrinkTicks} 从 1 缩到 {@code shrinkToScale}，中间保持 1。
 *
 * <p>本类不含任何 Bukkit 依赖，可直接单元求值。
 *
 * @param velocity      初速度(格/tick)
 * @param acceleration  加速度(格/tick²)，重力写在 y 上且为负值表示下坠
 * @param durationTicks 运动总时长(tick)。{@code 0} 表示无运动
 * @param stepTicks     关键帧间隔(tick)，同时作为客户端插值时长
 * @param popFromScale  出场起始缩放系数
 * @param popTicks      出场放大时长(tick)
 * @param shrinkToScale 退场结束缩放系数
 * @param shrinkTicks   退场缩小时长(tick)
 */
public record DisplayMotion(DisplayGeometry.Vector3 velocity,
        DisplayGeometry.Vector3 acceleration,
        int durationTicks,
        int stepTicks,
        double popFromScale,
        int popTicks,
        double shrinkToScale,
        int shrinkTicks) {

    /** 无运动。 */
    public static final DisplayMotion NONE = new DisplayMotion(
            DisplayGeometry.Vector3.ZERO, DisplayGeometry.Vector3.ZERO, 0, 1, 1D, 0, 1D, 0);

    public DisplayMotion {
        velocity = velocity == null ? DisplayGeometry.Vector3.ZERO : velocity;
        acceleration = acceleration == null ? DisplayGeometry.Vector3.ZERO : acceleration;
        durationTicks = Math.max(0, durationTicks);
        stepTicks = Math.max(1, stepTicks);
        popFromScale = Math.max(0D, popFromScale);
        popTicks = Math.max(0, popTicks);
        shrinkToScale = Math.max(0D, shrinkToScale);
        shrinkTicks = Math.max(0, shrinkTicks);
    }

    /** {@return 是否需要驱动运动} */
    public boolean isActive() {
        return durationTicks > 0;
    }

    /** {@return 关键帧总数，至少 1} */
    public int frameCount() {
        return Math.max(1, (durationTicks + stepTicks - 1) / stepTicks);
    }

    /**
     * 求某一时刻的位移偏移。
     *
     * @param tick 自运动开始经过的 tick，负值按 0 处理
     */
    public DisplayGeometry.Vector3 translationAt(int tick) {
        double time = Math.max(0, tick);
        double half = 0.5D * time * time;
        return new DisplayGeometry.Vector3(
                velocity.x() * time + acceleration.x() * half,
                velocity.y() * time + acceleration.y() * half,
                velocity.z() * time + acceleration.z() * half
        );
    }

    /**
     * 求某一时刻的缩放系数。
     *
     * <p>出场段与退场段在时长上重叠时退场优先，保证末尾一定落到 {@code shrinkToScale}。
     *
     * @param tick 自运动开始经过的 tick，负值按 0 处理
     */
    public double scaleFactorAt(int tick) {
        int time = Math.max(0, tick);
        int shrinkStart = durationTicks - shrinkTicks;
        if (shrinkTicks > 0 && time >= shrinkStart) {
            double progress = shrinkTicks == 0 ? 1D : (double) (time - shrinkStart) / shrinkTicks;
            return 1D + (shrinkToScale - 1D) * Math.clamp(progress, 0D, 1D);
        }
        if (popTicks > 0 && time < popTicks) {
            double progress = (double) time / popTicks;
            return popFromScale + (1D - popFromScale) * Math.clamp(progress, 0D, 1D);
        }
        return 1D;
    }
}
