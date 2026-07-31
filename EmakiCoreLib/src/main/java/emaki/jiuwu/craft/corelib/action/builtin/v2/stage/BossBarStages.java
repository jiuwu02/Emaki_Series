package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

/**
 * Package-crossing seam for the boss bar cleanup that {@code BuiltinStages.shutdown()} needs.
 *
 * <p>{@link BossBarStore} stays package-private because nothing outside this package should be able to create or
 * inspect bars. Shutdown, however, lives one package up, so this exposes exactly one operation.</p>
 */
public final class BossBarStages {

    private BossBarStages() {
    }

    /** Detaches every boss bar the v2 stages created. */
    public static void clearAll() {
        BossBarStore.clear();
    }
}
