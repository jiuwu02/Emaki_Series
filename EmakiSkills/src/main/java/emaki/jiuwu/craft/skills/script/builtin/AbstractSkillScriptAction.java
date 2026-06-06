package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.skills.api.SkillScriptAction;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

abstract class AbstractSkillScriptAction implements SkillScriptAction {

    private final String id;
    private final String category;
    private final String description;
    private final List<ActionParameter> parameters;

    AbstractSkillScriptAction(String id, String category, String description, ActionParameter... parameters) {
        this.id = id;
        this.category = category;
        this.description = description;
        this.parameters = parameters == null ? List.of() : List.copyOf(Arrays.asList(parameters));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<ActionParameter> parameters() {
        return parameters;
    }

    protected CompletableFuture<emaki.jiuwu.craft.corelib.action.ActionResult> completed(emaki.jiuwu.craft.corelib.action.ActionResult result) {
        return CompletableFuture.completedFuture(result);
    }

    protected String arg(Map<String, String> arguments, String key, String fallback) {
        String value = arguments == null ? null : arguments.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    protected int intArg(Map<String, String> arguments, String key, int fallback) {
        return ActionParsers.parseInt(arg(arguments, key, null), fallback);
    }

    protected double doubleArg(Map<String, String> arguments, String key, double fallback) {
        return ActionParsers.parseDouble(arg(arguments, key, null), fallback);
    }

    protected Entity entityTarget(SkillScriptContext context, Map<String, String> arguments) {
        String target = arg(arguments, "target", "target").toLowerCase(java.util.Locale.ROOT);
        if ("caster".equals(target) || "self".equals(target) || "player".equals(target)) {
            return context.caster();
        }
        Object stored = context.sharedValue(target);
        if (stored instanceof Entity entity) {
            return entity;
        }
        return context.targetEntity();
    }

    protected Location locationTarget(SkillScriptContext context, Map<String, String> arguments) {
        String at = arg(arguments, "at", arg(arguments, "target", "target")).toLowerCase(java.util.Locale.ROOT);
        if ("caster".equals(at) || "self".equals(at) || "player".equals(at)) {
            return context.caster().getLocation();
        }
        if ("look".equals(at)) {
            return context.caster().getEyeLocation().add(context.caster().getLocation().getDirection().multiply(3));
        }
        Object stored = context.sharedValue(at);
        if (stored instanceof Entity entity) {
            return entity.getLocation();
        }
        if (stored instanceof Location location) {
            return location.clone();
        }
        Entity entity = context.targetEntity();
        if (entity != null) {
            return entity.getLocation();
        }
        Location location = context.targetLocation();
        return location == null ? context.caster().getLocation() : location;
    }
}
