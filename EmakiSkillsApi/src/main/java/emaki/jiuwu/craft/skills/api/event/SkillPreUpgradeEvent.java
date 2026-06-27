package emaki.jiuwu.craft.skills.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiSkills after a skill upgrade request has passed validation but
 * before any cost is charged and the success roll decides the outcome.
 *
 * <p>Listeners may inspect the player, the skill id and the current/target
 * level, override the success rate via {@link #setSuccessRate(double)}, or
 * cancel the upgrade entirely. A cancelled event stops EmakiSkills from
 * charging and rolling. This event is fired on the server thread.
 */
public final class SkillPreUpgradeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String skillId;
    private final int currentLevel;
    private final int targetLevel;
    private final int maxLevel;
    private double successRate;
    private boolean cancelled;

    /**
     * Creates a pre-upgrade event.
     *
     * @param player       the player performing the upgrade
     * @param skillId      the skill id
     * @param currentLevel the skill level before the upgrade
     * @param targetLevel  the skill level after a successful upgrade
     * @param maxLevel     the maximum skill level
     * @param successRate  the resolved success rate in percent (0-100)
     */
    public SkillPreUpgradeEvent(Player player,
            String skillId,
            int currentLevel,
            int targetLevel,
            int maxLevel,
            double successRate) {
        this.player = player;
        this.skillId = skillId;
        this.currentLevel = currentLevel;
        this.targetLevel = targetLevel;
        this.maxLevel = maxLevel;
        this.successRate = successRate;
    }

    /** {@return the player performing the upgrade} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the skill id} */
    public String getSkillId() {
        return skillId;
    }

    /** {@return the skill level before the upgrade} */
    public int getCurrentLevel() {
        return currentLevel;
    }

    /** {@return the skill level after a successful upgrade} */
    public int getTargetLevel() {
        return targetLevel;
    }

    /** {@return the maximum skill level} */
    public int getMaxLevel() {
        return maxLevel;
    }

    /** {@return the success rate in percent (0-100) used for the roll} */
    public double getSuccessRate() {
        return successRate;
    }

    /**
     * Overrides the success rate used for the roll.
     *
     * @param successRate the new success rate in percent (0-100)
     */
    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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
