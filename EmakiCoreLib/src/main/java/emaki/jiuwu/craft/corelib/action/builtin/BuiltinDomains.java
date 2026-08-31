package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;

final class BuiltinDomains {

    private BuiltinDomains() {
    }

    static CoreActionExecutionTarget target(CoreActionExecutionDomain domain) {
        return switch (domain == null ? CoreActionExecutionDomain.UNDECLARED : domain) {
            case SERVER_GLOBAL -> CoreActionExecutionTarget.global();
            case CONTEXT_ENTITY -> CoreActionExecutionTarget.contextEntity();
            case LOCATION_REGION -> CoreActionExecutionTarget.location(null, 0D, 0D, 0D);
            case ASYNC_COMPUTE -> CoreActionExecutionTarget.asyncCompute();
            case UNDECLARED -> CoreActionExecutionTarget.undeclared();
        };
    }
}
