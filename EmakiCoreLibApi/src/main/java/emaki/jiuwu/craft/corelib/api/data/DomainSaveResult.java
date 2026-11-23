package emaki.jiuwu.craft.corelib.api.data;

/**
 * Outcome of a revision-guarded {@link PlayerDomainRepository#save} attempt.
 *
 * @param outcome whether the write landed, lost the optimistic lock, or was rejected
 * @param revision stored row revision after the attempt; on {@link Outcome#COMMITTED} this is
 *        the new revision, otherwise the revision observed instead
 */
public record DomainSaveResult(Outcome outcome, long revision) {

    public enum Outcome {
        COMMITTED,
        STALE,
        REJECTED
    }

    public static DomainSaveResult committed(long revision) {
        return new DomainSaveResult(Outcome.COMMITTED, revision);
    }

    public static DomainSaveResult stale(long revision) {
        return new DomainSaveResult(Outcome.STALE, revision);
    }

    public static DomainSaveResult rejected(long revision) {
        return new DomainSaveResult(Outcome.REJECTED, revision);
    }
}
