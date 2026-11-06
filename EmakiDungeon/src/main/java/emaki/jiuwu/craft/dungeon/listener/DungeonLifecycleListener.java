package emaki.jiuwu.craft.dungeon.listener;

import java.util.Map;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.recovery.RecoveryService;
import emaki.jiuwu.craft.dungeon.script.ScriptRuntimeManager;
import emaki.jiuwu.craft.dungeon.ticket.TicketService;
import emaki.jiuwu.craft.dungeon.trigger.TriggerType;

public final class DungeonLifecycleListener implements Listener {

    private final RecoveryService recovery;
    private final PartyService parties;
    private final ScriptRuntimeManager scriptManager;
    private final TicketService tickets;

    public DungeonLifecycleListener(RecoveryService recovery, PartyService parties,
            ScriptRuntimeManager scriptManager, TicketService tickets) {
        this.recovery = recovery;
        this.parties = parties;
        this.scriptManager = scriptManager;
        this.tickets = tickets;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        parties.handleJoin(event.getPlayer());
        recovery.onJoin(event.getPlayer());
        if (tickets != null) {
            tickets.drainPending(event.getPlayer());
        }
        if (scriptManager != null) {
            scriptManager.fireTrigger(TriggerType.onPlayerJoinServer, null, null, Map.of(
                    "player", String.valueOf(event.getPlayer().getUniqueId())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        recovery.onQuit(event.getPlayer());
        parties.handleQuit(event.getPlayer());
    }
}
