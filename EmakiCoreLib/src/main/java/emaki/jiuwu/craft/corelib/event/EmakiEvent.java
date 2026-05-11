package emaki.jiuwu.craft.corelib.event;

/**
 * Base interface for all Emaki cross-module events.
 * <p>
 * These are lightweight events published through {@link EmakiEventBus},
 * not Bukkit Events. They are used for cross-module communication
 * without creating hard dependencies between modules.
 */
public interface EmakiEvent {

    /**
     * @return a human-readable event type identifier (e.g. "strengthen_success", "gem_inlay")
     */
    String eventType();
}
