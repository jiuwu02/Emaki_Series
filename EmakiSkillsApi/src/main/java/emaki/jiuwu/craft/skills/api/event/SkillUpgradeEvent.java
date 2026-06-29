package emaki.jiuwu.craft.skills.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiSkills after a skill upgrade attempt has been fully resolved.
 *
 * <p>This event reports the outcome of both successful and failed upgrades. The
 * level change (or failure penalty such as a downgrade) has already been
 * applied, so the result cannot be changed by listeners. It is suitable for
 * statistics, announcements and downstream effects. This event is fired on the
 * server thread.
 */
public final class SkillUpgradeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String skillId;
    private final int fromLevel;
    private final int toLevel;
    private final double successRate;
    private final boolean success;
    private final boolean downgraded;

    /**
     * Creates a post-upgrade event.
     *
     * @param player      the player who performed the upgrade
     * @param skillId     the skill id
     * @param fromLevel   the skill level before the attempt
     * @param toLevel     the skill level after the attempt
     * @param successRate the success rate in percent (0-100) used for the roll
     * @param success     whether the upgrade succeeded
     * @param downgraded  whether a failure penalty downgraded the skill
     */
    public SkillUpgradeEvent(Player player,
            String skillId,
            int fromLevel,
            int toLevel,
            double successRate,
            boolean success,
            boolean downgraded) {
        this.player = player;
        this.skillId = skillId;
        this.fromLevel = fromLevel;
        this.toLevel = toLevel;
        this.successRate = successRate;
        this.success = success;
        this.downgraded = downgraded;
    }

    /** {@return the player who performed the upgrade} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the skill id} */
    public String getSkillId() {
        return skillId;
    }

    /** {@return the skill level before the attempt} */
    public int getFromLevel() {
        return fromLevel;
    }

    /** {@return the skill level after the attempt} */
    public int getToLevel() {
        return toLevel;
    }

    /** {@return the success rate in percent (0-100) used for the roll} */
    public double getSuccessRate() {
        return successRate;
    }

    /** {@return whether the upgrade succeeded} */
    public boolean isSuccess() {
        return success;
    }

    /** {@return whether a failure penalty downgraded the skill} */
    public boolean isDowngraded() {
        return downgraded;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the shared handler list for this event type} */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
