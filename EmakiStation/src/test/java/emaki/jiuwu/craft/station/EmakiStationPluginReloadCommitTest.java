package emaki.jiuwu.craft.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.config.precheck.ConfigCommitGate;

class EmakiStationPluginReloadCommitTest {

    @Test
    void rejectedGateRetainsExistingSnapshots() {
        AtomicInteger replacements = new AtomicInteger();

        EmakiStationPlugin.commitIfAccepted(
                new ConfigCommitGate.Result(false, "station", List.of("invalid config")),
                replacements::incrementAndGet);

        assertEquals(0, replacements.get());
    }

    @Test
    void acceptedGateCommitsResolvedSnapshots() {
        AtomicInteger replacements = new AtomicInteger();

        EmakiStationPlugin.commitIfAccepted(
                new ConfigCommitGate.Result(true, "station", List.of()),
                replacements::incrementAndGet);

        assertEquals(1, replacements.get());
    }
}
