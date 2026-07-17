package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptAction;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

abstract class AbstractSkillScriptAction implements SkillScriptAction {

    private final String id;
    private final String category;
    private final String description;
    private final List<SkillActionParameter> parameters;

    AbstractSkillScriptAction(String id, String category, String description, SkillActionParameter... parameters) {
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
    public List<SkillActionParameter> parameters() {
        return parameters;
    }

    protected CompletableFuture<SkillActionResult> completed(SkillActionResult result) {
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

    protected <T> CompletableFuture<T> callOnEntity(SkillScriptContext context,
            Entity entity,
            Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (context == null || context.plugin() == null || entity == null) {
            future.completeExceptionally(new IllegalStateException("Skill entity owner domain is unavailable."));
            return future;
        }
        try {
            var scheduled = FoliaSchedulerAdapter.runEntityTask(
                    context.plugin(), entity, () -> complete(future, task));
            if (scheduled == null) {
                future.completeExceptionally(new IllegalStateException(
                        "Skill entity owner scheduling was rejected."));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        CompletableFuture.delayedExecutor(30L, TimeUnit.SECONDS).execute(() ->
                future.completeExceptionally(new IllegalStateException(
                        "Skill entity owner task did not execute before its scheduling deadline.")));
        return future;
    }

    protected <T> CompletableFuture<T> callAtLocation(SkillScriptContext context,
            Location location,
            Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Location safeLocation = location == null ? null : location.clone();
        if (context == null || context.plugin() == null || safeLocation == null || safeLocation.getWorld() == null) {
            future.completeExceptionally(new IllegalStateException("Skill location owner domain is unavailable."));
            return future;
        }
        try {
            var scheduled = FoliaSchedulerAdapter.runAtLocation(
                    context.plugin(), safeLocation, () -> complete(future, task));
            if (scheduled == null) {
                future.completeExceptionally(new IllegalStateException(
                        "Skill location owner scheduling was rejected."));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        CompletableFuture.delayedExecutor(30L, TimeUnit.SECONDS).execute(() ->
                future.completeExceptionally(new IllegalStateException(
                        "Skill location owner task did not execute before its scheduling deadline.")));
        return future;
    }

    protected CompletableFuture<SkillActionResult> atLocation(SkillScriptContext context,
            Map<String, String> arguments,
            String argumentName,
            String fallback,
            Function<Location, SkillActionResult> action) {
        return callAtResolvedLocation(context, arguments, argumentName, fallback, action);
    }

    protected <T> CompletableFuture<T> callAtResolvedLocation(SkillScriptContext context,
            Map<String, String> arguments,
            String argumentName,
            String fallback,
            Function<Location, T> action) {
        String selector = arg(arguments, argumentName, fallback).toLowerCase(java.util.Locale.ROOT);
        Entity caster = context == null ? null : context.caster();
        if ("caster".equals(selector) || "self".equals(selector) || "player".equals(selector)) {
            return callOnEntity(context, caster, () -> action.apply(caster.getLocation()));
        }
        if ("look".equals(selector)) {
            return callOnEntity(context, caster, () -> action.apply(
                    caster.getEyeLocation().add(caster.getLocation().getDirection().multiply(3))));
        }
        Object stored = context == null ? null : context.sharedValue(selector);
        if (stored instanceof Entity entity) {
            return callOnEntity(context, entity, () -> action.apply(entity.getLocation()));
        }
        if (stored instanceof Location location) {
            Location safeLocation = location.clone();
            return callAtLocation(context, safeLocation, () -> action.apply(safeLocation));
        }
        Entity target = context == null ? null : context.targetEntity();
        if (target != null) {
            return callOnEntity(context, target, () -> action.apply(target.getLocation()));
        }
        Location targetLocation = context == null ? null : context.targetLocation();
        if (targetLocation != null) {
            Location safeLocation = targetLocation.clone();
            return callAtLocation(context, safeLocation, () -> action.apply(safeLocation));
        }
        return callOnEntity(context, caster, () -> action.apply(caster.getLocation()));
    }

    private <T> void complete(CompletableFuture<T> future, Supplier<T> task) {
        try {
            future.complete(task.get());
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
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
}
