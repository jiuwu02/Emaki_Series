package emaki.jiuwu.craft.corelib.debug;

import java.util.logging.Level;
import java.util.logging.Logger;
import emaki.jiuwu.craft.corelib.log.LogTags;
import java.util.Map;

public final class FailureRecorder {

    private static final Logger LOGGER = Logger.getLogger(FailureRecorder.class.getName());

    private FailureRecorder() {
    }

    public static void recordIgnoredFailure(String module, String location, Throwable failure) {
        LogTags.send(LOGGER, null, Level.WARNING, "debug.ignored_failure", Map.of("module", module, "location", location), failure);
    }
}
