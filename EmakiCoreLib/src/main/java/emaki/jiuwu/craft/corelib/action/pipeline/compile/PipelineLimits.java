package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import org.jetbrains.annotations.NotNull;

/**
 * Compile-time limits applied to a pipeline.
 *
 * <p>{@code maxRepeatTimes} defaults to 100 (decision D4). Exceeding it rejects the configuration
 * rather than silently truncating, because a silently capped {@code times=100000} looks like it
 * worked.</p>
 *
 * @param maxRepeatTimes upper bound for {@code every ... times N}
 * @param maxSequenceDepth upper bound for nested {@code run} calls, replacing the v1 hard-coded 8
 * @param maxBranchDepth upper bound for nested {@code if} branches
 */
public record PipelineLimits(int maxRepeatTimes, int maxSequenceDepth, int maxBranchDepth) {

    /** Default repeat cap, per decision D4. */
    public static final int DEFAULT_MAX_REPEAT_TIMES = 100;

    /** Default sequence nesting cap. */
    public static final int DEFAULT_MAX_SEQUENCE_DEPTH = 8;

    /** Default branch nesting cap. */
    public static final int DEFAULT_MAX_BRANCH_DEPTH = 16;

    public PipelineLimits {
        maxRepeatTimes = maxRepeatTimes <= 0 ? DEFAULT_MAX_REPEAT_TIMES : maxRepeatTimes;
        maxSequenceDepth = maxSequenceDepth <= 0 ? DEFAULT_MAX_SEQUENCE_DEPTH : maxSequenceDepth;
        maxBranchDepth = maxBranchDepth <= 0 ? DEFAULT_MAX_BRANCH_DEPTH : maxBranchDepth;
    }

    /** {@return the default limits} */
    public static @NotNull PipelineLimits defaults() {
        return new PipelineLimits(DEFAULT_MAX_REPEAT_TIMES, DEFAULT_MAX_SEQUENCE_DEPTH, DEFAULT_MAX_BRANCH_DEPTH);
    }

    /**
     * Creates limits with only the repeat cap overridden.
     *
     * @param maxRepeatTimes upper bound for {@code times}
     * @return the limits
     */
    public static @NotNull PipelineLimits withRepeatCap(int maxRepeatTimes) {
        return new PipelineLimits(maxRepeatTimes, DEFAULT_MAX_SEQUENCE_DEPTH, DEFAULT_MAX_BRANCH_DEPTH);
    }
}
