package emaki.jiuwu.craft.corelib.runtime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.DrainResult;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;




public final class CorePluginLifecycle {

    public enum State {
        NEW,
        RUNNING,
        QUIESCING,
        CLOSED
    }

    public record ShutdownReport(boolean fileOperationsDrained,
                                 boolean schedulerTerminated,
                                 int pendingFileOperations,
                                 List<Throwable> fileFailures) {

        public ShutdownReport {
            fileFailures = fileFailures == null ? List.of() : List.copyOf(fileFailures);
        }

        public boolean clean() {
            return fileOperationsDrained && schedulerTerminated && fileFailures.isEmpty();
        }
    }

    private State state = State.NEW;
    private AsyncFileService fileService;
    private AsyncTaskScheduler taskScheduler;
    private ShutdownReport lastReport = new ShutdownReport(true, true, 0, List.of());

    public synchronized void start(AsyncFileService fileService, AsyncTaskScheduler taskScheduler) {
        if (state != State.NEW) {
            throw new IllegalStateException("Core plugin lifecycle already started: " + state);
        }
        this.fileService = Objects.requireNonNull(fileService, "fileService");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        state = State.RUNNING;
    }

    public synchronized State state() {
        return state;
    }

    public synchronized ShutdownReport shutdown(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (state == State.CLOSED) {
            return lastReport;
        }
        state = State.QUIESCING;
        long timeoutNanos = Math.max(1L, unit.toNanos(timeout));
        long deadline = System.nanoTime() + timeoutNanos;
        long fileBudgetNanos = Math.max(1L, timeoutNanos - Math.max(1L, timeoutNanos / 10L));

        DrainResult fileResult = fileService == null
                ? new DrainResult(true, 0, List.of())
                : fileService.closeAndDrain(fileBudgetNanos, TimeUnit.NANOSECONDS);

        if (!fileResult.drained()) {
            lastReport = new ShutdownReport(
                    false,
                    false,
                    fileResult.pendingOperations(),
                    fileResult.failures()
            );
            return lastReport;
        }

        long remainingNanos = Math.max(1L, deadline - System.nanoTime());
        boolean schedulerTerminated = taskScheduler == null
                || taskScheduler.shutdownGracefully(remainingNanos, TimeUnit.NANOSECONDS);

        lastReport = new ShutdownReport(
                true,
                schedulerTerminated,
                0,
                fileResult.failures()
        );
        if (schedulerTerminated) {
            state = State.CLOSED;
        }
        return lastReport;
    }
}
