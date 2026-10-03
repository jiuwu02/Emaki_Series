package emaki.jiuwu.craft.level;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;

class EmakiLevelStartupTest {

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
        Plugin attribute = MockBukkit.load(EmakiAttributePlugin.class);
        assertNotNull(attribute);
        assertTrue(attribute.isEnabled());
        Plugin level = MockBukkit.load(EmakiLevelPlugin.class);
        assertNotNull(level);
        assertTrue(level.isEnabled());
        assertNotNull(Bukkit.getPluginManager().getPlugin("EmakiLevel"));
    }
}
