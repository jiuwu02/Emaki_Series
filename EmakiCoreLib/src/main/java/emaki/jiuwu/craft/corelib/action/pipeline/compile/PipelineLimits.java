package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import org.jetbrains.annotations.NotNull;

public record PipelineLimits(int maxRepeatTimes, int maxSequenceDepth, int maxBranchDepth, int maxRetryTimes) {

    public static final int DEFAULT_MAX_REPEAT_TIMES = 100;

    public static final int DEFAULT_MAX_SEQUENCE_DEPTH = 8;

    public static final int DEFAULT_MAX_BRANCH_DEPTH = 16;

    public static final int DEFAULT_MAX_RETRY_TIMES = 5;

    public PipelineLimits {
        maxRepeatTimes = maxRepeatTimes <= 0 ? DEFAULT_MAX_REPEAT_TIMES : maxRepeatTimes;
        maxSequenceDepth = maxSequenceDepth <= 0 ? DEFAULT_MAX_SEQUENCE_DEPTH : maxSequenceDepth;
        maxBranchDepth = maxBranchDepth <= 0 ? DEFAULT_MAX_BRANCH_DEPTH : maxBranchDepth;
        maxRetryTimes = maxRetryTimes <= 0 ? DEFAULT_MAX_RETRY_TIMES : maxRetryTimes;
    }

    public PipelineLimits(int maxRepeatTimes, int maxSequenceDepth, int maxBranchDepth) {
        this(maxRepeatTimes, maxSequenceDepth, maxBranchDepth, DEFAULT_MAX_RETRY_TIMES);
    }

    public static @NotNull PipelineLimits defaults() {
        return new PipelineLimits(DEFAULT_MAX_REPEAT_TIMES, DEFAULT_MAX_SEQUENCE_DEPTH,
                DEFAULT_MAX_BRANCH_DEPTH, DEFAULT_MAX_RETRY_TIMES);
    }

    public static @NotNull PipelineLimits withRepeatCap(int maxRepeatTimes) {
        return new PipelineLimits(maxRepeatTimes, DEFAULT_MAX_SEQUENCE_DEPTH,
                DEFAULT_MAX_BRANCH_DEPTH, DEFAULT_MAX_RETRY_TIMES);
    }
}
