package emaki.jiuwu.craft.dungeon.api.event;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class DungeonEnterEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final String partyId;

    public DungeonEnterEvent(String instanceId, String dungeonId, UUID player, String partyId) {
        super(instanceId, dungeonId);
        this.player = player;
        this.partyId = partyId;
    }

    public UUID getPlayer() {
        return player;
    }

    public String getPartyId() {
        return partyId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
