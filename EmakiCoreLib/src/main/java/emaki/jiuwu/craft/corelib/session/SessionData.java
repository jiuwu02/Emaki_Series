package emaki.jiuwu.craft.corelib.session;

/**
 * Contract a cached player payload must satisfy to be managed by {@link AbstractPlayerSessionCache}.
 *
 * <p>The four isomorphic player caches (level, skills, nutrition, storage) already declare exactly
 * these seven members on their own data types; this interface only names the shape they share so the
 * cache mechanics can be written once.
 *
 * <p>Revision semantics: {@link #revision()} increases on every mutation, {@link #persistedRevision()}
 * records the revision that reached disk, and {@link #dirty()} is the difference between them. A save
 * lane captures {@code revision()} into a ticket before writing and calls
 * {@link #markPersisted(long)} with that captured value on success, so mutations that happen during
 * the write are not mistaken for persisted state.
 *
 * @param <T> self type, so {@link #copy()} stays exact rather than widening to the interface
 */
public interface SessionData<T extends SessionData<T>> {

    /**
     * Returns a deep copy detached from this instance.
     *
     * <p>Snapshot isolation depends on this: a save ticket copies on the way in and on the way out,
     * so a concurrent mutation can never modify the map or list a write is iterating.
     *
     * @return an independent copy; never {@code null}
     */
    T copy();

    /** @return the current mutation revision, increasing on every change */
    long revision();

    /** @return the highest revision known to have reached persistent storage */
    long persistedRevision();

    /** @return {@code true} when there are mutations that have not been persisted */
    boolean dirty();

    /** Marks this payload as mutated, advancing {@link #revision()}. */
    void markDirty();

    /**
     * Records that the given revision reached persistent storage.
     *
     * @param revision the revision captured in the save ticket before the write started
     */
    void markPersisted(long revision);

    /** Resets dirty tracking, treating the current state as persisted. */
    void clearDirty();
}
