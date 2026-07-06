package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.event.EmakiEvent;

/**
 * A normalized gameplay signal captured once by {@link GameplayEventPublisher} inside
 * EmakiCoreLib and dispatched through the shared
 * {@link emaki.jiuwu.craft.corelib.event.EmakiEventBus} to every interested Emaki plugin
 * (EmakiLevel, EmakiCodex, ...).
 *
 * <p>Every event carries the three things all subscribers need:
 * <ul>
 *   <li>{@link #player()} — the attributed acting player;</li>
 *   <li>{@link #triggerKey()} — the normalized trigger id, e.g. {@code entity_kill};</li>
 *   <li>{@link #variables()} — domain variables exposed to condition / expression evaluation
 *       (the same keys EmakiLevel and EmakiCodex already use).</li>
 * </ul>
 *
 * <p>Concrete subtypes additionally expose the raw Bukkit objects (victim entity, broken
 * block, crafted item, ...) so rule-matching subscribers can inspect them directly without
 * re-deriving state. The publisher performs the shared heavy lifting exactly once:
 * MythicMobs reflection, last-damager attribution, and brew-stand user tracking. Adding a new
 * gameplay trigger is therefore a two-step change confined to CoreLib: add a subtype here and
 * publish it from the publisher; existing subscribers are unaffected until they opt in.
 */
public interface GameplayEvent extends EmakiEvent {

    /** {@return the attributed acting player; never {@code null} once published} */
    Player player();

    /** {@return the normalized trigger key, e.g. {@code entity_kill}} */
    String triggerKey();

    /** {@return immutable domain variables for this event} */
    Map<String, Object> variables();

    @Override
    default String eventType() {
        return triggerKey();
    }
}
