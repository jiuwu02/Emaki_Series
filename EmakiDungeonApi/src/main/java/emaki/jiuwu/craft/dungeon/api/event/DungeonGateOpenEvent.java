package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

/**
 * Completion event fired when a dungeon gate is opened.
 *
 * <p>This is a completion event: the opening has been accepted when
 * listeners run; the gate's block changes are submitted immediately
 * afterwards. The event does not implement
 * {@link org.bukkit.event.Cancellable}; cancelling is not observed.
 *
 * <p>Fired synchronously on the thread dispatching the flow action or
 * script call that opened the gate (the instance event loop for flow
 * events).
 *
 * <p>Coverage is gate-wide: one event per opening, carrying the gate id
 * and the gate's logic condition.
 *
 * <p>Not fired when the gate id does not match a gate definition.
 */
public class DungeonGateOpenEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String gateId;
    private final String condition;

    /**
     * Creates a gate open event.
     *
     * @param instanceId the instance the gate belongs to
     * @param dungeonId the dungeon definition the gate belongs to
     * @param gateId the opened gate id; {@code null} becomes empty
     * @param condition the gate's logic condition; {@code null} becomes
     *         empty
     */
    public DungeonGateOpenEvent(String instanceId, String dungeonId,
            String gateId, String condition) {
        super(instanceId, dungeonId);
        this.gateId = gateId == null ? "" : gateId;
        this.condition = condition == null ? "" : condition;
    }

    /** {@return the id of the opened gate; empty when unknown} */
    public String getGateId() {
        return gateId;
    }

    /** {@return the gate's logic condition; empty when unknown} */
    public String getCondition() {
        return condition;
    }

    /** {@return the handlers of this event} */
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the static handler list for this event type} */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
