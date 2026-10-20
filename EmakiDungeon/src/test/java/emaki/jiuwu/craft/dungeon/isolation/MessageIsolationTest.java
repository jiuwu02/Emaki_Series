package emaki.jiuwu.craft.dungeon.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.snapshot.PlayersDataStore;
import emaki.jiuwu.craft.dungeon.testutil.FakeDispatcher;

class MessageIsolationTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void senderOutsideInstanceOnlyReachesNonInstanceTargets() {
        assertFalse(MessageIsolation.dropChatRecipient(false, null, false, null, false));
        assertTrue(MessageIsolation.dropChatRecipient(false, null, false, "emd_a", false));
        assertFalse(MessageIsolation.dropChatRecipient(false, null, false, "emd_a", true));
    }

    @Test
    void senderInsideInstanceWithGlobalListenReachesEveryone() {
        assertFalse(MessageIsolation.dropChatRecipient(true, "emd_a", true, null, false));
        assertFalse(MessageIsolation.dropChatRecipient(true, "emd_a", true, "emd_b", false));
        assertFalse(MessageIsolation.dropChatRecipient(true, "emd_a", true, "emd_b", true));
    }

    @Test
    void senderInsideInstanceWithoutGlobalOnlyReachesSameInstance() {
        assertFalse(MessageIsolation.dropChatRecipient(true, "emd_a", false, "emd_a", false));
        assertFalse(MessageIsolation.dropChatRecipient(true, "emd_a", false, "emd_a", true));
        assertTrue(MessageIsolation.dropChatRecipient(true, "emd_a", false, "emd_b", true));
        assertTrue(MessageIsolation.dropChatRecipient(true, "emd_a", false, null, false));
    }

    @Test
    void broadcastDropsInstanceMembersWithoutGlobalListen() {
        assertTrue(MessageIsolation.dropChatRecipient(false, null, false, "emd_a", false));
        assertFalse(MessageIsolation.dropChatRecipient(false, null, false, "emd_a", true));
        assertFalse(MessageIsolation.dropChatRecipient(false, null, false, null, false));
    }

    @Test
    void globalTogglePersistsAcrossServiceInstances() {
        FakeDispatcher dispatcher = new FakeDispatcher();
        PlayersDataStore store = new PlayersDataStore(
                MockBukkit.createMockPlugin("EmakiDungeonTest"), dispatcher,
                tempDir.toFile(), false);
        InstanceRegistry registry = new InstanceRegistry();
        MessageIsolation isolation = new MessageIsolation(registry, store, null);

        Player player = MockBukkit.getMock().addPlayer();
        UUID playerId = player.getUniqueId();
        assertFalse(isolation.globalListen(playerId));
        assertTrue(isolation.toggleGlobal(player));
        assertTrue(isolation.globalListen(playerId));

        PlayersDataStore reloaded = new PlayersDataStore(
                MockBukkit.createMockPlugin("EmakiDungeonTest"), dispatcher,
                tempDir.toFile(), false);
        MessageIsolation fresh = new MessageIsolation(registry, reloaded, null);
        assertTrue(fresh.globalListen(playerId));
        assertFalse(fresh.toggleGlobal(player));
    }
}
