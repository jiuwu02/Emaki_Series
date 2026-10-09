package emaki.jiuwu.craft.corelib.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import emaki.jiuwu.craft.corelib.execution.ExecutionBackendLoader;
import emaki.jiuwu.craft.corelib.platform.paper.execution.PaperExecutionBackend;

final class CapabilityProbeTest {

    @Test
    void fixedFoliaProbeReportsBackendReady() {
        CapabilityProbe probe = CapabilityProbe.fixed(null, null, true, true);
        assertTrue(probe.folia());
        assertTrue(probe.paper());
        assertTrue(probe.nativeExecutionSchedulers());
        assertTrue(probe.nativeOwnershipChecks());
        assertTrue(probe.foliaBackendReady());
        assertTrue(probe.hasNativeOwnershipSchedulers());
    }

    @Test
    void fixedPaperProbeIsNotFoliaReady() {
        CapabilityProbe probe = CapabilityProbe.fixed(null, null, false, true);
        assertFalse(probe.folia());
        assertTrue(probe.paper());
        assertFalse(probe.nativeExecutionSchedulers());
        assertFalse(probe.nativeOwnershipChecks());
        assertFalse(probe.foliaBackendReady());
        assertFalse(probe.hasNativeOwnershipSchedulers());
    }

    @Test
    void fixedNonPaperProbeIsNeitherPaperNorFolia() {
        CapabilityProbe probe = CapabilityProbe.fixed(null, null, false, false);
        assertFalse(probe.folia());
        assertFalse(probe.paper());
        assertFalse(probe.foliaBackendReady());
    }

    @Test
    void detectWithoutServerDoesNotThrow() {
        CapabilityProbe probe = CapabilityProbe.detect(null);
        assertFalse(probe.foliaBackendReady());
        assertFalse(probe.isClassAvailable(""));
        assertFalse(probe.isPluginPresent(""));
        assertFalse(probe.isPluginEnabled(""));
    }

    @Test
    void classAvailableReflectsRealClasspath() {
        CapabilityProbe probe = CapabilityProbe.detect(null);
        assertTrue(probe.isClassAvailable("java.lang.String"));
        assertFalse(probe.isClassAvailable("emaki.jiuwu.craft.no.such.Class"));
    }

    @Test
    void backendLoaderRejectsMissingServer() {
        assertThrows(IllegalStateException.class,
                () -> ExecutionBackendLoader.load(null, CapabilityProbe.fixed(null, null, false, true)));
    }

    @Test
    void backendLoaderSelectsPaperBackendOnPaperProbe() {
        ServerMock server = MockBukkit.mock();
        try {
            CapabilityProbe probe = CapabilityProbe.fixed(server, null, false, true);
            ExecutionBackendLoader.LoadedExecution loaded = ExecutionBackendLoader.load(server, probe);
            assertNotNull(loaded.dispatcher());
            assertTrue(loaded.dispatcher() instanceof PaperExecutionBackend);
            assertTrue(loaded.ownership() instanceof PaperExecutionBackend);
        } finally {
            MockBukkit.unmock();
        }
    }
}
