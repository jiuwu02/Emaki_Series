package emaki.jiuwu.craft.corelib.script.js.event;

import org.bukkit.event.EventPriority;

public record JavaScriptEventSubscription(String id,
        String eventType,
        EventPriority priority,
        boolean ignoreCancelled,
        String scriptPath,
        String functionName,
        long timeoutMillis) {

    public boolean dynamicEventClassName() {
        return eventType != null && eventType.contains(".");
    }
}
