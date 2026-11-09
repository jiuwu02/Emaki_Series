package emaki.jiuwu.craft.corelib.gui;

import java.util.concurrent.atomic.AtomicInteger;

public final class GuiClickThrottle {

    private static final AtomicInteger intervalMs = new AtomicInteger();

    private GuiClickThrottle() {
    }

    public static void configureIntervalMs(int millis) {
        intervalMs.set(Math.max(0, millis));
    }

    public static int intervalMs() {
        return intervalMs.get();
    }

    public static boolean allow(GuiSession session) {
        if (session == null) {
            return true;
        }
        return session.tryConsumeClick(intervalMs.get());
    }
}
