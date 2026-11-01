package emaki.jiuwu.craft.dungeon.api;

import java.util.Map;

/**
 * Callback registered through {@link ScriptApi#registerTrigger} and invoked
 * when the subscribed dungeon trigger event fires.
 */
@FunctionalInterface
public interface ScriptTriggerHandler {

    /**
     * Handles a firing of the registered trigger.
     *
     * <p>Exceptions thrown by the handler are caught by the plugin and logged
     * rather than propagated to the event source.
     *
     * @param triggerId the id this handler was registered under
     * @param context the context supplied by the firing trigger; never
     *         {@code null}, empty when the trigger carries no context
     */
    void handle(String triggerId, Map<String, String> context);
}
