package emaki.jiuwu.craft.corelib.script;

/** Marks isolated script-worker execution so live server access can fail closed. */
public final class ScriptWorkerBoundary {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private ScriptWorkerBoundary() {
    }

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    public static boolean active() {
        return DEPTH.get() > 0;
    }
}
