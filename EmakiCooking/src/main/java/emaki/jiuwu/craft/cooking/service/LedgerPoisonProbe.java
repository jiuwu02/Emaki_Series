package emaki.jiuwu.craft.cooking.service;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;

/** TEMPORARY verification probe. Delete after use. */
public final class LedgerPoisonProbe {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        StationStateVersionLedger ledger = new StationStateVersionLedger();
        StationCoordinates at = new StationCoordinates("iris_city", -107, 66, 184);

        // Step 1: first add_ingredient -> nothing persisted, save V1, YAML write completes.
        long v1 = ledger.beginSave(at);
        check("V1 is current save", ledger.isCurrentSave(at, v1));
        long persisted = v1; // YAML fallback wrote V1

        // Step 2: next interaction load(): observe(persisted) then YAML->PDC migrate attempt.
        ledger.observe(at, persisted, false);
        check("observe keeps ledger at persisted", ledger.currentVersion(at) == persisted);
        check("no poison before migrate", !poisonedRead(ledger, at, persisted));

        long v2 = ledger.beginSave(at); // tryMigrateYamlToPdc -> beginSave
        // PDC write fails (block is not a TileState) -> migrate returns false, NO rollback, NO persist.
        check("migrate raised ledger above persisted", v2 > persisted);
        check("ledger now poisoned vs persisted", poisonedRead(ledger, at, persisted));

        // Caller of this load DID save (e.g. stir) -> ledger and persisted resync.
        long v3 = ledger.beginSave(at);
        persisted = v3;
        ledger.observe(at, persisted, false);
        check("save resyncs ledger", !poisonedRead(ledger, at, persisted));

        // Step 3: load() again -> migrate poisons again, but caller REJECTS (no save).
        ledger.observe(at, persisted, false);
        long v4 = ledger.beginSave(at);
        check("second migrate poisons again", v4 > persisted && poisonedRead(ledger, at, persisted));

        // Step 4/5: every later load sees inMemory > persisted -> returns null forever.
        ledger.observe(at, persisted, false);
        check("observe cannot heal poison", ledger.currentVersion(at) > persisted);
        check("state invisible on next read", poisonedRead(ledger, at, persisted));
        ledger.observe(at, persisted, false);
        check("still invisible after repeated reads", poisonedRead(ledger, at, persisted));

        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            throw new AssertionError("probe failed");
        }
    }

    /** Mirrors StationStateStore.load() null-return condition for a non-tombstone persisted state. */
    private static boolean poisonedRead(StationStateVersionLedger ledger, StationCoordinates at, long persistedVersion) {
        StationStateVersionLedger.Mutation inMemory = ledger.currentMutation(at);
        if (inMemory == null) {
            return false;
        }
        if (inMemory.version() > persistedVersion
                || (inMemory.version() == persistedVersion && inMemory.tombstone())) {
            return inMemory.tombstone() || inMemory.version() > persistedVersion;
        }
        return false;
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }
}
