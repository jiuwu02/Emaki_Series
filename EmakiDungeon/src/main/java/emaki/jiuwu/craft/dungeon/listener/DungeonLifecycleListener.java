package emaki.jiuwu.craft.dungeon.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.recovery.RecoveryService;

public final class DungeonLifecycleListener implements Listener {

    private final RecoveryService recovery;
    private final PartyService parties;

    public DungeonLifecycleListener(RecoveryService recovery, PartyService parties) {
        this.recovery = recovery;
        this.parties = parties;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        parties.handleJoin(event.getPlayer());
        recovery.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        recovery.onQuit(event.getPlayer());
        parties.handleQuit(event.getPlayer());
    }
}
