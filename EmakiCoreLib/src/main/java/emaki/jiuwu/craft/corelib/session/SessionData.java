package emaki.jiuwu.craft.corelib.session;

public interface SessionData<T extends SessionData<T>> {

    T copy();

    long revision();

    long persistedRevision();

    boolean dirty();

    void markDirty();

    void markPersisted(long revision);

    void clearDirty();
}
