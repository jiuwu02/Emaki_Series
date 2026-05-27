package emaki.jiuwu.craft.skills.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class SkillScriptContext {

    private final Plugin plugin;
    private final Player caster;
    private final String skillId;
    private final String triggerId;
    private final Map<String, String> variables;
    private final Map<String, Object> sharedState = new ConcurrentHashMap<>();
    private Entity targetEntity;
    private Location targetLocation;

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

    public Plugin plugin() {
        return plugin;
    }

    public Player caster() {
        return caster;
    }

    public String skillId() {
        return skillId;
    }

    public String triggerId() {
        return triggerId;
    }

    public Object invocation() {
        return null;
    }

    public Map<String, String> variables() {
        return Map.copyOf(variables);
    }

    public String variable(String key) {
        return variables.get(key);
    }

    public void putVariable(String key, Object value) {
        if (key != null && !key.isBlank()) {
            variables.put(key.toLowerCase(), value == null ? "" : String.valueOf(value));
        }
    }

    public Map<String, Object> sharedState() {
        return sharedState;
    }

    public Object sharedValue(String key) {
        return sharedState.get(key);
    }

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

    public Entity targetEntity() {
        return targetEntity;
    }

    public Location targetLocation() {
        return targetLocation == null ? null : targetLocation.clone();
    }

    public boolean hasTarget() {
        return targetEntity != null && !targetEntity.isDead();
    }

    public void setTarget(Entity targetEntity) {
        this.targetEntity = targetEntity;
        this.targetLocation = targetEntity == null ? null : targetEntity.getLocation();
        refreshTargetVariables();
    }

    public void setTargetLocation(Location targetLocation) {
        this.targetLocation = targetLocation == null ? null : targetLocation.clone();
        refreshTargetVariables();
    }

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
