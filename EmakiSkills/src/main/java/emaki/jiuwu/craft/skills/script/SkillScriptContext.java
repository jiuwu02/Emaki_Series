package emaki.jiuwu.craft.skills.script;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;

public final class SkillScriptContext extends emaki.jiuwu.craft.skills.api.SkillScriptContext {

    private final EmakiSkillsPlugin plugin;
    private final Player caster;
    private final SkillDefinition definition;
    private final String triggerId;
    private final TriggerInvocation invocation;
    private final Map<String, String> variables;
    private final Map<String, Object> sharedState = new ConcurrentHashMap<>();
    private Entity targetEntity;
    private Location targetLocation;
    private boolean targetPresent;
    private String targetName = "";
    private String targetUuid = "";

    public SkillScriptContext(EmakiSkillsPlugin plugin,
            Player caster,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation,
            Map<String, String> variables) {
        super(plugin, caster, definition == null ? "" : definition.id(), triggerId, variables);
        this.plugin = plugin;
        this.caster = caster;
        this.definition = definition;
        this.triggerId = triggerId == null ? "" : triggerId;
        this.invocation = invocation;
        this.variables = variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
        if (invocation != null) {
            this.targetEntity = invocation.targetEntity();
            this.targetLocation = invocation.targetLocation();
            this.targetPresent = this.targetEntity != null || this.targetLocation != null;
        }
        this.targetName = this.variables.getOrDefault("target_name", "");
        this.targetUuid = this.variables.getOrDefault("target_uuid", "");
    }

    public EmakiSkillsPlugin plugin() {
        return plugin;
    }

    public Player caster() {
        return caster;
    }

    public SkillDefinition definition() {
        return definition;
    }

    public String triggerId() {
        return triggerId;
    }

    public TriggerInvocation invocation() {
        return invocation;
    }

    public Map<String, String> variables() {
        return Map.copyOf(variables);
    }

    public String variable(String key) {
        return variables.get(key);
    }

    public void putVariable(String key, Object value) {
        if (key != null && !key.isBlank()) {
            variables.put(key.toLowerCase(java.util.Locale.ROOT), value == null ? "" : String.valueOf(value));
        }
    }

    public Map<String, Object> sharedState() {
        return sharedState;
    }

    public Object sharedValue(String key) {
        return sharedState.get(key);
    }

    public void putSharedValue(String key, Object value) {
        if (key != null && !key.isBlank()) {
            if (value == null) {
                sharedState.remove(key);
            } else {
                sharedState.put(key, value);
            }
        }
    }

    public Entity targetEntity() {
        return targetEntity;
    }

    public Location targetLocation() {
        return targetLocation == null ? null : targetLocation.clone();
    }

    public boolean hasTarget() {
        return targetPresent;
    }

    public void setTarget(Entity targetEntity) {
        this.targetEntity = targetEntity;
        if (targetEntity == null) {
            this.targetLocation = null;
            this.targetPresent = false;
            this.targetName = "";
            this.targetUuid = "";
        } else {
            this.targetLocation = targetEntity.getLocation();
            this.targetPresent = !targetEntity.isDead();
            this.targetName = targetEntity.getName();
            this.targetUuid = targetEntity.getUniqueId().toString();
        }
        refreshTargetVariables();
    }

    public void setTargetLocation(Location targetLocation) {
        this.targetLocation = targetLocation == null ? null : targetLocation.clone();
        this.targetPresent = this.targetEntity != null || this.targetLocation != null;
        refreshTargetVariables();
    }

    public void refreshTargetVariables() {
        putVariable("has_target", targetPresent ? "1" : "0");
        putVariable("target_name", targetName);
        putVariable("target_uuid", targetUuid);
        Location location = targetLocation;
        if (location != null) {
            putVariable("target_world", location.getWorld() == null ? "" : location.getWorld().getName());
            putVariable("target_x", location.getX());
            putVariable("target_y", location.getY());
            putVariable("target_z", location.getZ());
        }
    }
}
