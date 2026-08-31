package emaki.jiuwu.craft.cooking.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a single-type or combo nutrition threshold changes between met and unmet.
 *
 * <p>Runs synchronously on the player's owner thread and is edge-triggered, not emitted for unchanged state.
 * It is informational and cannot override or cancel configured threshold actions. Single-threshold fields
 * and combo-count fields are mutually exclusive as indicated by {@link Kind}.
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
