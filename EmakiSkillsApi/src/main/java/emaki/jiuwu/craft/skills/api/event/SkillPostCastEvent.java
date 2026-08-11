package emaki.jiuwu.craft.skills.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired by EmakiSkills after a skill effect has executed successfully and its resource costs, skill cooldown,
 * global cooldown and forced cast delay have all been committed to the player's profile.
 *
 * <p>Informational only: the cast is already committed, so this event is <strong>not cancellable</strong>.
 * Cancel a cast through {@link SkillPreCastEvent} instead, which runs before any effect or charge.
 *
 * <h2>Threading</h2>
 * The cast pipeline resolves asynchronously, but EmakiSkills hops back onto the thread that owns the casting
 * player before committing and firing, so listeners may touch the player, their inventory and the surrounding
 * world directly.
 *
 * <h2>Coverage — this event is not fired for every cast attempt</h2>
 * It is fired only on the fully committed success path. It is skipped when the attempt is rejected before
 * execution (cast mode off, no slot binding, unknown or disabled skill, skill missing from the player's
 * unlocked pool, active cooldown or forced delay, unmet conditions, insufficient resources), when
 * {@link SkillPreCastEvent} is cancelled, when the script or MythicMobs execution fails, when the player's
 * data session is superseded before the commit lands, and when the owner-thread task is rejected or retired
 * during shutdown drain or an unexpected exception. Do not use it as an exhaustive audit trail; treat a
 * missing event as "outcome unknown" rather than "no attempt happened".
 *
 * <p>Coverage spans every cast path that reaches the commit stage: the trigger dispatcher used by in-game
 * triggers, passive triggers, the CoreLib pipeline cast stage, and the public
 * {@link emaki.jiuwu.craft.skills.api.SkillOperations#cast} and
 * {@link emaki.jiuwu.craft.skills.api.SkillOperations#castByTrigger} entry points. A path that bypasses the
 * cooldown or resource gate still fires this event once it commits.
 *
 * @see SkillPreCastEvent
 */
public final class SkillPostCastEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String skillId;
    private final String triggerId;

    /**
     * Creates a post-cast event.
     *
     * @param player    the player whose cast committed
     * @param skillId   the canonical id of the skill that was cast
     * @param triggerId the id of the trigger that initiated the cast
     */
    public SkillPostCastEvent(@NotNull Player player, @NotNull String skillId, @NotNull String triggerId) {
        this.player = player;
        this.skillId = skillId;
        this.triggerId = triggerId;
    }

    /** {@return the player whose cast committed; online at fire time and owned by the current thread} */
    public @NotNull Player getPlayer() {
        return player;
    }

    /** {@return the canonical, already normalized id of the skill that was cast} */
    public @NotNull String getSkillId() {
        return skillId;
    }

    /**
     * {@return the id of the trigger that initiated the cast; blank trigger ids are rejected before an
     * attempt can reach this event, and internal pipeline casts report a synthetic trigger id rather than a
     * player input trigger}
     */
    public @NotNull String getTriggerId() {
        return triggerId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the shared handler list for this event type} */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
