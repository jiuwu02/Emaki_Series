package emaki.jiuwu.craft.corelib.action;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/**
 * Concrete scheduler target for one action invocation.
 */
public record ActionExecutionTarget(
        ExecutionDomain domain,
        Entity entity,
        Location location,
        ActionResult failure) {

    public ActionExecutionTarget {
        domain = domain == null ? ExecutionDomain.SERVER_GLOBAL : domain;
        location = location == null ? null : location.clone();
    }

    public static ActionExecutionTarget global() {
        return new ActionExecutionTarget(ExecutionDomain.SERVER_GLOBAL, null, null, null);
    }

    public static ActionExecutionTarget entity(Entity entity) {
        return entity == null
                ? failure(ActionResult.failure(ActionErrorType.INVALID_STATE, "Action requires an entity execution target."))
                : new ActionExecutionTarget(ExecutionDomain.ENTITY, entity, null, null);
    }

    public static ActionExecutionTarget location(Location location) {
        return location == null || location.getWorld() == null
                ? failure(ActionResult.failure(ActionErrorType.WORLD_NOT_FOUND, "Action requires a valid location execution target."))
                : new ActionExecutionTarget(ExecutionDomain.LOCATION_REGION, null, location, null);
    }

    public static ActionExecutionTarget async() {
        return new ActionExecutionTarget(ExecutionDomain.ASYNC_COMPUTE, null, null, null);
    }

    public static ActionExecutionTarget undeclared() {
        return new ActionExecutionTarget(null, null, null,
                ActionResult.failure(ActionErrorType.INVALID_STATE, "Action execution target is undeclared."));
    }

    public static ActionExecutionTarget failure(ActionResult failure) {
        return new ActionExecutionTarget(ExecutionDomain.SERVER_GLOBAL, null, null,
                failure == null
                        ? ActionResult.failure(ActionErrorType.INVALID_STATE, "Action execution target could not be planned.")
                        : failure);
    }

    public boolean valid() {
        return failure == null;
    }
}
