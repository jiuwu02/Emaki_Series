package emaki.jiuwu.craft.corelib.gui;

public final class GuiClickThrottle {

    private static volatile int intervalMs;

    private GuiClickThrottle() {
    }

    public static void configureIntervalMs(int millis) {
        intervalMs = Math.max(0, millis);
    }

    public static int intervalMs() {
        return intervalMs;
    }

    public static boolean allow(GuiSession session) {
        if (session == null) {
            return true;
        }
        return session.tryConsumeClick(intervalMs);
    }
}
