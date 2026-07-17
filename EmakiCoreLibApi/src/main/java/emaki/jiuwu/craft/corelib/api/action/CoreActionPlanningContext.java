package emaki.jiuwu.craft.corelib.api.action;

import java.util.Map;

import org.jetbrains.annotations.NotNull;

/**
 * Context supplied while a third-party action selects its scheduler ownership domain.
 */
public record CoreActionPlanningContext(
        @NotNull CoreActionContext actionContext,
        @NotNull Map<String, String> arguments) {

    public CoreActionPlanningContext {
        actionContext = actionContext == null
                ? CoreActionContext.create(null, null, "default", false)
                : actionContext;
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
