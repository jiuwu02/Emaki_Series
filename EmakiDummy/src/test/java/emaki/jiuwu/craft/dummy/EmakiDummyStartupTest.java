package emaki.jiuwu.craft.dummy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class EmakiDummyStartupTest {

    private static final List<String> BUNDLED_SCENARIOS = List.of(
            "dungeon_full_run",
            "party_wait_cancel",
            "message_isolation",
            "limit_denied",
            "settlement_rewards",
            "exit_rollback",
            "editor_test_start",
            "script_affix");

    @Test
    void pluginEnablesAndLoadsBundledScenarios() {
        MockBukkit.mock();
        EmakiDummyPlugin plugin = MockBukkit.load(EmakiDummyPlugin.class);
        try {
            assertTrue(plugin.runtimeInitialized());
            assertEquals(4, plugin.appConfig().maxDummies());
            assertEquals("direct", plugin.appConfig().connectionMode());
            assertNotNull(plugin.dummyManager());
            assertTrue(plugin.scenarioEngine().ids().containsAll(BUNDLED_SCENARIOS));
            assertTrue(plugin.scenarioEngine().loadFailures().isEmpty());
        } finally {
            MockBukkit.unmock();
        }
    }
}
