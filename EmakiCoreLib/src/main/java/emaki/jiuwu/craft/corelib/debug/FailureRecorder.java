package emaki.jiuwu.craft.corelib.debug;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class FailureRecorder {

    private static final Logger LOGGER = Logger.getLogger(FailureRecorder.class.getName());

    private FailureRecorder() {
    }

    public static void recordIgnoredFailure(String module, String location, Throwable failure) {
        LOGGER.log(Level.WARNING, "[" + module + "] 已忽略的失败 @ " + location, failure);
    }
}
