package emaki.jiuwu.craft.attribute.script.js;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeDamageEvent;

public final class JavaScriptDamageHookListener implements Listener {

    private final JavaScriptDamageHookRegistry registry;

    public JavaScriptDamageHookListener(JavaScriptDamageHookRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onDamage(EmakiAttributeDamageEvent event) {
        if (registry != null) {
            registry.handle(event);
        }
    }
}
