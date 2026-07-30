package emaki.jiuwu.craft.skills.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.text.Texts;

/** Owner-scoped runtime registry for skill script actions. */
public final class DefaultSkillScriptActionRegistry implements SkillScriptActionRegistry, Listener, AutoCloseable {

    private final Map<String, Registration> registrations = new ConcurrentHashMap<>();

    @Override
    public SkillActionResult register(Plugin owner, SkillScriptAction action) {
        if (owner == null || !owner.isEnabled()) {
            return SkillActionResult.failure(SkillActionErrorType.INVALID_STATE,
                    "Skill script action owner must be enabled.");
        }
        if (action == null || Texts.isBlank(action.id())) {
            return SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT,
                    "Skill script action id cannot be blank.");
        }
        String id = Texts.normalizeId(action.id());
        Registration previous = registrations.putIfAbsent(id, new Registration(owner, action));
        return previous == null
                ? SkillActionResult.ok()
                : SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT,
                        "Skill script action already registered: " + id);
    }

    @Override
    public void unregister(String actionId) {
        registrations.remove(Texts.normalizeId(actionId));
    }

    @Override
    public void unregisterAll(Plugin owner) {
        if (owner != null) {
            registrations.entrySet().removeIf(entry -> owner == entry.getValue().owner());
        }
    }

    @Override
    public SkillScriptAction get(String actionId) {
        Registration registration = activeRegistration(actionId);
        return registration == null ? null : registration.action();
    }

    @Override
    public Plugin ownerOf(String actionId) {
        Registration registration = activeRegistration(actionId);
        return registration == null ? null : registration.owner();
    }

    @Override
    public Map<String, SkillScriptAction> all() {
        purgeDisabledOwners();
        Map<String, SkillScriptAction> result = new LinkedHashMap<>();
        registrations.forEach((id, registration) -> result.put(id, registration.action()));
        return Map.copyOf(result);
    }

    @Override
    public List<SkillScriptAction> byOwner(Plugin owner) {
        if (owner == null || !owner.isEnabled()) {
            unregisterAll(owner);
            return List.of();
        }
        return registrations.values().stream()
                .filter(registration -> owner == registration.owner())
                .map(Registration::action)
                .toList();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        unregisterAll(event.getPlugin());
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        registrations.clear();
    }

    private Registration activeRegistration(String actionId) {
        String id = Texts.normalizeId(actionId);
        Registration registration = registrations.get(id);
        if (registration == null || registration.owner().isEnabled()) {
            return registration;
        }
        registrations.remove(id, registration);
        return null;
    }

    private void purgeDisabledOwners() {
        registrations.entrySet().removeIf(entry -> !entry.getValue().owner().isEnabled());
    }

    private record Registration(Plugin owner, SkillScriptAction action) {
    }
}
