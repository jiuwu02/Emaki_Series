package emaki.jiuwu.craft.skills.trigger;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * Represents a single trigger invocation carrying the context of the event
 * that fired it. All fields are final except {@code cancelOriginalAction},
 * which downstream handlers may flip to suppress the original Bukkit event.
 */
public final class TriggerInvocation {

    private final Player player;
    private final String triggerId;
    private final Event rawEvent;
    private final boolean sneaking;
    private final long occurredAt;
    private final Entity targetEntity;
    private final Location targetLocation;
    private final Entity sourceEntity;

    private boolean cancelOriginalAction;

    public TriggerInvocation(Player player,
                             String triggerId,
                             Event rawEvent,
                             boolean sneaking,
                             boolean cancelOriginalAction,
                             long occurredAt) {
        this(player, triggerId, rawEvent, sneaking, cancelOriginalAction, occurredAt, null, null, null);
    }

    public TriggerInvocation(Player player,
                             String triggerId,
                             Event rawEvent,
                             boolean sneaking,
                             boolean cancelOriginalAction,
                             long occurredAt,
                             Entity targetEntity,
                             Location targetLocation,
                             Entity sourceEntity) {
        this.player = player;
        this.triggerId = triggerId;
        this.rawEvent = rawEvent;
        this.sneaking = sneaking;
        this.cancelOriginalAction = cancelOriginalAction;
        this.occurredAt = occurredAt;
        this.targetEntity = targetEntity;
        this.targetLocation = targetLocation;
        this.sourceEntity = sourceEntity;
    }

    public Player player() {
        return player;
    }

    public String triggerId() {
        return triggerId;
    }

    public Event rawEvent() {
        return rawEvent;
    }

    public boolean sneaking() {
        return sneaking;
    }

    public boolean cancelOriginalAction() {
        return cancelOriginalAction;
    }

    public void setCancelOriginalAction(boolean cancelOriginalAction) {
        this.cancelOriginalAction = cancelOriginalAction;
    }

    public long occurredAt() {
        return occurredAt;
    }

    public Entity targetEntity() {
        return targetEntity;
    }

    public Location targetLocation() {
        return targetLocation == null ? null : targetLocation.clone();
    }

    public Entity sourceEntity() {
        return sourceEntity;
    }
}
