package emaki.jiuwu.craft.mobs;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;

class EmakiMobsStartupTest {

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
    @Disabled("MockBukkit 4.93 未实现 ServerMock.getGlobalRegionScheduler（Folia 全局区域调度器），EmakiMobs 启动在 AutonomousSpawnHandler 处无法完成")
    void pluginStartsSuccessfully() {
        Plugin coreLib = MockBukkit.load(EmakiCoreLibPlugin.class);
        assertNotNull(coreLib);
        assertTrue(coreLib.isEnabled());
        Plugin attribute = MockBukkit.load(EmakiAttributePlugin.class);
        assertNotNull(attribute);
        assertTrue(attribute.isEnabled());
        Plugin skills = MockBukkit.load(EmakiSkillsPlugin.class);
        assertNotNull(skills);
        assertTrue(skills.isEnabled());
        Plugin item = MockBukkit.load(EmakiItemPlugin.class);
        assertNotNull(item);
        assertTrue(item.isEnabled());
        Plugin mobs = MockBukkit.load(EmakiMobsPlugin.class);
        assertNotNull(mobs);
        assertTrue(mobs.isEnabled());
        assertNotNull(Bukkit.getPluginManager().getPlugin("EmakiMobs"));
    }
}
