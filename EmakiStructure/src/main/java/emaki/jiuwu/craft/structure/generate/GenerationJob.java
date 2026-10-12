package emaki.jiuwu.craft.structure.generate;

import emaki.jiuwu.craft.structure.rollback.GenerationJournal;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class GenerationJob {

    public enum Outcome {
        COMPLETED, ABORTED, CANCELLED
    }

    private final UUID id = UUID.randomUUID();
    private final String templateId;
    private final long createdAtNanos = System.nanoTime();
    private final AtomicInteger pendingBatches = new AtomicInteger();
    private final AtomicBoolean finished = new AtomicBoolean();
    private volatile boolean abortRequested;
    private volatile String abortReasonKey;
    private volatile String abortDetail;
    private volatile Outcome outcome;
    private volatile GenerationJournal journal;
    private final Consumer<GenerationJob> settledListener;

    public GenerationJob(String templateId, int batchCount) {
        this(templateId, batchCount, null);
    }

    public GenerationJob(String templateId, int batchCount, Consumer<GenerationJob> settledListener) {
        this.templateId = templateId;
        this.pendingBatches.set(batchCount);
        this.settledListener = settledListener;
    }

    public UUID id() {
        return id;
    }

    public String templateId() {
        return templateId;
    }

    public long ageNanos() {
        return System.nanoTime() - createdAtNanos;
    }

    public int pendingBatches() {
        return pendingBatches.get();
    }

    public void requestAbort(String reasonKey, String detail) {
        if (finished.get()) {
            return;
        }
        abortReasonKey = reasonKey;
        abortDetail = detail;
        abortRequested = true;
    }

    public boolean abortRequested() {
        return abortRequested;
    }

    public String abortReasonKey() {
        return abortReasonKey;
    }

    public String abortDetail() {
        return abortDetail;
    }

    public boolean isFinished() {
        return finished.get();
    }

    public Outcome outcome() {
        return outcome;
    }

    public GenerationJournal journal() {
        return journal;
    }

    public void attachJournal(GenerationJournal journal) {
        this.journal = journal;
    }

    public void recordBatchFinished() {
        if (pendingBatches.decrementAndGet() == 0) {
            complete(Outcome.COMPLETED);
        }
    }

    public void recordBatchDropped() {
        if (finished.get()) {
            return;
        }
        int remaining = pendingBatches.decrementAndGet();
        if (remaining <= 0) {
            complete(Outcome.ABORTED);
        }
    }

    public boolean complete(Outcome result) {
        if (!finished.compareAndSet(false, true)) {
            return false;
        }
        outcome = result;
        if (settledListener != null) {
            settledListener.accept(this);
        }
        return true;
    }
}
