package emaki.jiuwu.craft.skills.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Execution context passed to a {@link SkillScriptAction} when a skill runs.
 *
 * <p>Exposes the casting player, the originating skill and trigger ids, a
 * mutable string-variable map (also used to publish target-related values), a
 * thread-safe shared-state map for passing objects between actions, and the
 * current target entity/location.
 *
 * <p>Variable keys are lower-cased on insertion. Target variables such as
 * {@code target_name}, {@code target_uuid} and {@code target_x/y/z} are
 * refreshed automatically when the target changes.
 */
public class SkillScriptContext {

    private final Plugin plugin;
    private final Player caster;
    private final String skillId;
    private final String triggerId;
    private final Map<String, String> variables;
    private final Map<String, Object> sharedState = new ConcurrentHashMap<>();
    private Entity targetEntity;
    private Location targetLocation;

    /**
     * Creates a new skill-script context.
     *
     * @param plugin    the plugin driving the skill execution
     * @param caster    the casting player
     * @param skillId   the id of the skill being executed; {@code null} becomes
     *                  an empty string
     * @param triggerId the id of the trigger that fired the skill; {@code null}
     *                  becomes an empty string
     * @param variables the initial variable map; {@code null} starts empty
     */
    public SkillScriptContext(Plugin plugin,
            Player caster,
            String skillId,
            String triggerId,
            Map<String, String> variables) {
        this.plugin = plugin;
        this.caster = caster;
        this.skillId = skillId == null ? "" : skillId;
        this.triggerId = triggerId == null ? "" : triggerId;
        this.variables = variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
    }

    /** {@return the plugin driving this skill execution} */
    public Plugin plugin() {
        return plugin;
    }

    /** {@return the casting player} */
    public Player caster() {
        return caster;
    }

    /** {@return the id of the skill being executed} */
    public String skillId() {
        return skillId;
    }

    /** {@return the id of the trigger that fired the skill} */
    public String triggerId() {
        return triggerId;
    }

    /** {@return the underlying invocation object, or {@code null}} */
    public Object invocation() {
        return null;
    }

    /** {@return an immutable copy of the current variables} */
    public Map<String, String> variables() {
        return Map.copyOf(variables);
    }

    /**
     * {@return the value of a variable, or {@code null} if absent}
     *
     * @param key the variable key
     */
    public String variable(String key) {
        return variables.get(key);
    }

    /**
     * Sets a variable. The key is lower-cased; a {@code null} value is stored as
     * an empty string.
     *
     * @param key   the variable key; ignored when blank
     * @param value the value to store (stringified)
     */
    public void putVariable(String key, Object value) {
        if (key != null && !key.isBlank()) {
            variables.put(key.toLowerCase(), value == null ? "" : String.valueOf(value));
        }
    }

    /** {@return the mutable, thread-safe shared-state map} */
    public Map<String, Object> sharedState() {
        return sharedState;
    }

    /**
     * {@return the shared-state value for a key, or {@code null}}
     *
     * @param key the shared-state key
     */
    public Object sharedValue(String key) {
        return sharedState.get(key);
    }

    /**
     * Stores or removes a shared-state value.
     *
     * @param key   the shared-state key; ignored when blank
     * @param value the value to store; {@code null} removes the key
     */
    public void putSharedValue(String key, Object value) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (value == null) {
            sharedState.remove(key);
            return;
        }
        sharedState.put(key, value);
    }

    /** {@return the current target entity, or {@code null}} */
    public Entity targetEntity() {
        return targetEntity;
    }

    /** {@return a clone of the current target location, or {@code null}} */
    public Location targetLocation() {
        return targetLocation == null ? null : targetLocation.clone();
    }

    /** {@return whether a live (non-dead) target entity is set} */
    public boolean hasTarget() {
        return targetEntity != null && !targetEntity.isDead();
    }

    /**
     * Sets the target entity and synchronizes the target location and variables.
     *
     * @param targetEntity the new target entity, or {@code null} to clear
     */
    public void setTarget(Entity targetEntity) {
        this.targetEntity = targetEntity;
        this.targetLocation = targetEntity == null ? null : targetEntity.getLocation();
        refreshTargetVariables();
    }

    /**
     * Sets the target location independently of any entity.
     *
     * @param targetLocation the new target location, or {@code null} to clear
     */
    public void setTargetLocation(Location targetLocation) {
        this.targetLocation = targetLocation == null ? null : targetLocation.clone();
        refreshTargetVariables();
    }

    /**
     * Recomputes the {@code target_*} variables from the current target entity.
     * Called automatically by the target setters.
     */
    public void refreshTargetVariables() {
        putVariable("has_target", hasTarget() ? "1" : "0");
        if (targetEntity == null) {
            return;
        }
        putVariable("target_name", targetEntity.getName());
        putVariable("target_uuid", targetEntity.getUniqueId());
        Location location = targetEntity.getLocation();
        putVariable("target_world", location.getWorld() == null ? "" : location.getWorld().getName());
        putVariable("target_x", location.getX());
        putVariable("target_y", location.getY());
        putVariable("target_z", location.getZ());
    }
}
