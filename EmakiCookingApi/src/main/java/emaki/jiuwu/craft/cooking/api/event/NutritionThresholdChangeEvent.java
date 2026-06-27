package emaki.jiuwu.craft.cooking.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiCooking when a player crosses a nutrition threshold, either a
 * single-type threshold or a combo (balanced-diet) threshold.
 *
 * <p>This is an edge-triggered notification: it fires once when a threshold
 * becomes met and again when it is no longer met (recovered). The configured
 * threshold actions are still driven by EmakiCooking; this event is purely
 * informational and cannot be cancelled. It is suitable for achievements,
 * custom buffs/debuffs and UI updates. This event is fired on the server
 * thread.
 */
public final class NutritionThresholdChangeEvent extends Event {

    /** The kind of threshold that changed. */
    public enum Kind {
        /** A single nutrition-type threshold. */
        SINGLE,
        /** A combo threshold counting multiple nutrition types. */
        COMBO
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Kind kind;
    private final String ruleId;
    private final String typeId;
    private final boolean met;
    private final double value;
    private final double threshold;
    private final int matchedCount;
    private final int requiredCount;

    /**
     * Creates a nutrition threshold change event.
     *
     * @param player        the player whose nutrition changed
     * @param kind          the threshold kind
     * @param ruleId        the threshold rule id
     * @param typeId        the nutrition type id for {@link Kind#SINGLE}, or
     *                      {@code null} for {@link Kind#COMBO}
     * @param met           {@code true} when the threshold became met,
     *                      {@code false} when it recovered
     * @param value         the current nutrition value (single) or {@code 0}
     * @param threshold     the configured threshold value
     * @param matchedCount  the number of matching types (combo) or {@code 0}
     * @param requiredCount the required count for a combo, or {@code 0}
     */
    public NutritionThresholdChangeEvent(Player player,
            Kind kind,
            String ruleId,
            String typeId,
            boolean met,
            double value,
            double threshold,
            int matchedCount,
            int requiredCount) {
        this.player = player;
        this.kind = kind;
        this.ruleId = ruleId;
        this.typeId = typeId;
        this.met = met;
        this.value = value;
        this.threshold = threshold;
        this.matchedCount = matchedCount;
        this.requiredCount = requiredCount;
    }

    /** {@return the player whose nutrition changed} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the threshold kind} */
    public Kind getKind() {
        return kind;
    }

    /** {@return the threshold rule id} */
    public String getRuleId() {
        return ruleId;
    }

    /** {@return the nutrition type id for single thresholds, or {@code null}} */
    public String getTypeId() {
        return typeId;
    }

    /** {@return {@code true} when the threshold became met, {@code false} when recovered} */
    public boolean isMet() {
        return met;
    }

    /** {@return the current nutrition value for single thresholds, else {@code 0}} */
    public double getValue() {
        return value;
    }

    /** {@return the configured threshold value} */
    public double getThreshold() {
        return threshold;
    }

    /** {@return the number of matching types for combo thresholds, else {@code 0}} */
    public int getMatchedCount() {
        return matchedCount;
    }

    /** {@return the required count for combo thresholds, else {@code 0}} */
    public int getRequiredCount() {
        return requiredCount;
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
