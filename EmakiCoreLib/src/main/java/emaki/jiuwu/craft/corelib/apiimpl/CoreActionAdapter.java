package emaki.jiuwu.craft.corelib.apiimpl;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionExecutionMode;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.api.action.CoreAction;
import emaki.jiuwu.craft.corelib.api.action.CoreActionContext;
import emaki.jiuwu.craft.corelib.api.action.CoreActionErrorType;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionMode;
import emaki.jiuwu.craft.corelib.api.action.CoreActionParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreActionParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

final class CoreActionAdapter implements Action {

    private final CoreAction delegate;

    CoreActionAdapter(CoreAction delegate) {
        this.delegate = delegate;
    }

    CoreAction delegate() {
        return delegate;
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public String description() {
        return delegate.description();
    }

    @Override
    public String category() {
        return delegate.category();
    }

    @Override
    public String version() {
        return delegate.version();
    }

    @Override
    public List<ActionParameter> parameters() {
        return delegate.parameters().stream()
                .map(CoreActionAdapter::toInternalParameter)
                .toList();
    }

    @Override
    public boolean acceptsDynamicParameter(String name) {
        return delegate.acceptsDynamicParameter(name);
    }

    @Override
    public ActionExecutionMode executionMode() {
        return toInternalMode(delegate.executionMode());
    }

    @Override
    public long timeoutMillis() {
        return delegate.timeoutMillis();
    }

    @Override
    public ActionResult validate(Map<String, String> arguments) {
        ActionResult base = Action.super.validate(arguments);
        if (!base.success()) {
            return base;
        }
        try {
            return toInternalResult(delegate.validate(arguments == null ? Map.of() : arguments));
        } catch (RuntimeException exception) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, exception.getMessage());
        }
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        try {
            CoreActionContext apiContext = toApiContext(context);
            return toInternalResult(delegate.execute(apiContext, arguments == null ? Map.of() : arguments));
        } catch (RuntimeException exception) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, exception.getMessage());
        }
    }

    static CoreActionContext toApiContext(ActionContext context) {
        return context == null
                ? CoreActionContext.create(null, null, "default", false)
                : new CoreActionContext(
                        context.sourcePlugin(),
                        context.player(),
                        context.phase(),
                        context.silent(),
                        context.placeholders(),
                        context.attributes(),
                        context.sharedState()
                );
    }

    static ActionResult toInternalResult(CoreActionResult result) {
        if (result == null) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Action returned no result.");
        }
        if (result.skipped()) {
            return ActionResult.skipped(result.errorMessage());
        }
        if (result.success()) {
            return ActionResult.ok(result.data());
        }
        return ActionResult.failure(toInternalErrorType(result.errorType()), result.errorMessage());
    }

    static CoreActionResult toApiResult(ActionResult result) {
        if (result == null) {
            return CoreActionResult.failure(CoreActionErrorType.EXECUTION_EXCEPTION, "Action returned no result.");
        }
        if (result.skipped()) {
            return CoreActionResult.skipped(result.errorMessage());
        }
        if (result.success()) {
            return CoreActionResult.ok(result.data());
        }
        return CoreActionResult.failure(toApiErrorType(result.errorType()), result.errorMessage());
    }

    static CoreActionParameter toApiParameter(ActionParameter parameter) {
        return parameter == null
                ? new CoreActionParameter("", CoreActionParameterType.STRING, false, "", "")
                : new CoreActionParameter(
                        parameter.name(),
                        toApiParameterType(parameter.type()),
                        parameter.required(),
                        parameter.defaultValue(),
                        parameter.description()
                );
    }

    private static ActionParameter toInternalParameter(CoreActionParameter parameter) {
        return parameter == null
                ? new ActionParameter("", ActionParameterType.STRING, false, "", "")
                : new ActionParameter(
                        parameter.name(),
                        toInternalParameterType(parameter.type()),
                        parameter.required(),
                        parameter.defaultValue(),
                        parameter.description()
                );
    }

    static CoreActionExecutionMode toApiMode(ActionExecutionMode mode) {
        if (mode == null) {
            return CoreActionExecutionMode.SYNC;
        }
        try {
            return CoreActionExecutionMode.valueOf(mode.name());
        } catch (IllegalArgumentException exception) {
            return CoreActionExecutionMode.SYNC;
        }
    }

    private static ActionExecutionMode toInternalMode(CoreActionExecutionMode mode) {
        if (mode == null) {
            return ActionExecutionMode.SYNC;
        }
        try {
            return ActionExecutionMode.valueOf(mode.name());
        } catch (IllegalArgumentException exception) {
            return ActionExecutionMode.SYNC;
        }
    }

    private static CoreActionParameterType toApiParameterType(ActionParameterType type) {
        if (type == null) {
            return CoreActionParameterType.STRING;
        }
        try {
            return CoreActionParameterType.valueOf(type.name());
        } catch (IllegalArgumentException exception) {
            return CoreActionParameterType.STRING;
        }
    }

    private static ActionParameterType toInternalParameterType(CoreActionParameterType type) {
        if (type == null) {
            return ActionParameterType.STRING;
        }
        try {
            return ActionParameterType.valueOf(type.name());
        } catch (IllegalArgumentException exception) {
            return ActionParameterType.STRING;
        }
    }

    private static CoreActionErrorType toApiErrorType(ActionErrorType type) {
        if (type == null) {
            return CoreActionErrorType.EXECUTION_EXCEPTION;
        }
        try {
            return CoreActionErrorType.valueOf(type.name());
        } catch (IllegalArgumentException exception) {
            return CoreActionErrorType.EXECUTION_EXCEPTION;
        }
    }

    private static ActionErrorType toInternalErrorType(CoreActionErrorType type) {
        if (type == null) {
            return ActionErrorType.EXECUTION_EXCEPTION;
        }
        try {
            return ActionErrorType.valueOf(type.name());
        } catch (IllegalArgumentException exception) {
            return ActionErrorType.EXECUTION_EXCEPTION;
        }
    }

    static String ownerKey(org.bukkit.plugin.Plugin owner) {
        return owner == null ? "" : owner.getName();
    }

    static String sourceKey(String source) {
        return Texts.toStringSafe(source);
    }
}
