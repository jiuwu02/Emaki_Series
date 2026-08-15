package emaki.jiuwu.craft.corelib.display;

public record DisplayMotion(DisplayGeometry.Vector3 velocity,
        DisplayGeometry.Vector3 acceleration,
        int durationTicks,
        int stepTicks,
        double popFromScale,
        int popTicks,
        double shrinkToScale,
        int shrinkTicks) {

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

    public boolean isActive() {
        return durationTicks > 0;
    }

    public int frameCount() {
        return Math.max(1, (durationTicks + stepTicks - 1) / stepTicks);
    }

    public DisplayGeometry.Vector3 translationAt(int tick) {
        double time = Math.max(0, tick);
        double half = 0.5D * time * time;
        return new DisplayGeometry.Vector3(
                velocity.x() * time + acceleration.x() * half,
                velocity.y() * time + acceleration.y() * half,
                velocity.z() * time + acceleration.z() * half
        );
    }

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
