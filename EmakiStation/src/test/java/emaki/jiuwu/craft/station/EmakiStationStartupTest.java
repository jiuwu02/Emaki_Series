package emaki.jiuwu.craft.station;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.storage.EmakiStoragePlugin;

class EmakiStationStartupTest {

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
    void pluginStartsSuccessfully() {
        Plugin coreLib = MockBukkit.load(EmakiCoreLibPlugin.class);
        assertNotNull(coreLib);
        assertTrue(coreLib.isEnabled());
        Plugin storage = MockBukkit.load(EmakiStoragePlugin.class);
        assertNotNull(storage);
        assertTrue(storage.isEnabled());
        Plugin station = MockBukkit.load(EmakiStationPlugin.class);
        assertNotNull(station);
        assertTrue(station.isEnabled());
        assertNotNull(Bukkit.getPluginManager().getPlugin("EmakiStation"));
    }
}
