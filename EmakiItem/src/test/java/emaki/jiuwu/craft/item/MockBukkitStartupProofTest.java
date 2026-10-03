package emaki.jiuwu.craft.item;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;

class MockBukkitStartupProofTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        try {
            MockBukkit.unmock();
        } catch (Throwable ignored) {
        }
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        try {
            MockBukkit.unmock();
        } catch (Throwable ignored) {
        }
    }

    @Test
    void mockServerIsAvailable() {
        assertNotNull(server);
        assertNotNull(server.getPluginManager());
    }

    @Test
    void coreLibAndEmakiItemStartTogether() {
        Plugin coreLib = MockBukkit.load(EmakiCoreLibPlugin.class);
        assertNotNull(coreLib);
        Plugin item = MockBukkit.load(EmakiItemPlugin.class);
        assertNotNull(item);
    }
}
